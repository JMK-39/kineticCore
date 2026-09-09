package dev.xyat.kineticcore.api.client.selector;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public final class HudPositionEditor {
    public record State(int x, int y, double scale) {
    }

    @FunctionalInterface
    public interface ElementRenderer {
        void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY);
    }

    public static final int INVENTORY_WIDTH = 176;
    public static final int INVENTORY_HEIGHT = 166;

    private static final ResourceLocation INVENTORY_TEXTURE = new ResourceLocation(
            "minecraft",
            "textures/gui/container/inventory.png"
    );
    private static final int ELEMENT_PADDING = 4;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final double MIN_SCALE = 0.5D;
    private static final int SAFE_MAX_SIZE = 1_000_000_000;

    private int screenWidth;
    private int screenHeight;
    private int baseElementWidth;
    private int baseElementHeight;
    private int elementWidth;
    private int elementHeight;
    private int defaultX;
    private int defaultY;
    private double defaultScale = 1.0D;
    private double minimumScale = MIN_SCALE;
    private double scale = 1.0D;
    private int x;
    private int y;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean initialized;
    private boolean dragging;

    public static int getInventoryLeft(int screenWidth) {
        return (screenWidth - INVENTORY_WIDTH) / 2;
    }

    public static int getInventoryTop(int screenHeight) {
        return (screenHeight - INVENTORY_HEIGHT) / 2;
    }

    public static void renderInventoryReference(
            GuiGraphics graphics,
            Font font,
            Player player,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY
    ) {
        int left = getInventoryLeft(screenWidth);
        int top = getInventoryTop(screenHeight);

        graphics.blit(INVENTORY_TEXTURE, left, top, 0, 0, INVENTORY_WIDTH, INVENTORY_HEIGHT);
        graphics.drawString(
                font,
                Component.translatable("container.crafting"),
                left + 97,
                top + 8,
                4210752,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("container.inventory"),
                left + 8,
                top + 72,
                4210752,
                false
        );
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                left + 51,
                top + 75,
                30,
                left + 51 - mouseX,
                top + 25 - mouseY,
                player
        );
    }

    public void initialize(
            int screenWidth,
            int screenHeight,
            int elementWidth,
            int elementHeight,
            int initialX,
            int initialY,
            int defaultX,
            int defaultY
    ) {
        initialize(
                screenWidth,
                screenHeight,
                elementWidth,
                elementHeight,
                initialX,
                initialY,
                defaultX,
                defaultY,
                1.0D,
                1.0D
        );
    }

    public void initialize(
            int screenWidth,
            int screenHeight,
            int elementWidth,
            int elementHeight,
            int initialX,
            int initialY,
            int defaultX,
            int defaultY,
            double initialScale,
            double defaultScale
    ) {
        initialize(
                screenWidth,
                screenHeight,
                elementWidth,
                elementHeight,
                initialX,
                initialY,
                defaultX,
                defaultY,
                initialScale,
                defaultScale,
                MIN_SCALE
        );
    }

    /**
     * Initializes an editor with a caller-defined minimum scale. Existing
     * overloads retain the historical {@value #MIN_SCALE} lower bound.
     */
    public void initialize(
            int screenWidth,
            int screenHeight,
            int elementWidth,
            int elementHeight,
            int initialX,
            int initialY,
            int defaultX,
            int defaultY,
            double initialScale,
            double defaultScale,
            double minimumScale
    ) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.baseElementWidth = Math.max(1, elementWidth);
        this.baseElementHeight = Math.max(1, elementHeight);
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.minimumScale = sanitizeMinimumScale(minimumScale);
        this.defaultScale = sanitizeScale(defaultScale);

        if (!initialized) {
            this.scale = sanitizeScale(initialScale);
            updateElementSize();
            this.x = clampX(initialX);
            this.y = clampY(initialY);
            this.initialized = true;
        } else {
            this.scale = sanitizeScale(this.scale);
            updateElementSize();
            this.x = clampX(this.x);
            this.y = clampY(this.y);
        }
    }

    public void addControlButtons(
            Consumer<Button> buttonAdder,
            Component saveText,
            Component resetText,
            Component cancelText,
            Runnable saveAction,
            Runnable cancelAction
    ) {
        int totalWidth = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
        int startX = (screenWidth - totalWidth) / 2;
        int buttonY = screenHeight - 30;

        buttonAdder.accept(Button.builder(saveText, button -> saveAction.run())
                .bounds(startX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        buttonAdder.accept(Button.builder(resetText, button -> reset())
                .bounds(startX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        buttonAdder.accept(Button.builder(cancelText, button -> cancelAction.run())
                .bounds(startX + (BUTTON_WIDTH + BUTTON_GAP) * 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    public void render(
            GuiGraphics graphics,
            Font font,
            int mouseX,
            int mouseY,
            Component title,
            Component instruction,
            Component position,
            ElementRenderer renderer
    ) {
        renderGuides(graphics);
        graphics.drawCenteredString(font, title, screenWidth / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, instruction, screenWidth / 2, 28, 0xFFFFFF);
        graphics.drawCenteredString(font, position, screenWidth / 2, 42, 0xFFFFFF);
        renderElement(graphics, mouseX, mouseY, renderer);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isElementHovered(mouseX, mouseY)) {
            return false;
        }

        dragging = true;
        dragOffsetX = (int) Math.round(mouseX) - x;
        dragOffsetY = (int) Math.round(mouseY) - y;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!dragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        x = clampX((int) Math.round(mouseX) - dragOffsetX);
        y = clampY((int) Math.round(mouseY) - dragOffsetY);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !dragging) {
            return false;
        }

        dragging = false;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (scrollDelta == 0.0D || !isElementHovered(mouseX, mouseY)) {
            return false;
        }

        double oldWidth = Math.max(1, elementWidth);
        double oldHeight = Math.max(1, elementHeight);
        double relativeX = (mouseX - x) / oldWidth;
        double relativeY = (mouseY - y) / oldHeight;
        double step = scrollDelta > 0.0D ? 0.1D : -0.1D;
        double nextScale = Math.max(
                minimumScale,
                Math.round((scale + step) * 100.0D) / 100.0D
        );

        if (Double.compare(nextScale, scale) == 0) {
            return true;
        }

        scale = nextScale;
        updateElementSize();
        x = clampX((int) Math.round(mouseX - relativeX * elementWidth));
        y = clampY((int) Math.round(mouseY - relativeY * elementHeight));
        return true;
    }

    public boolean keyPressed(int keyCode, boolean shiftDown) {
        int step = shiftDown ? 5 : 1;

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            x = clampX(x - step);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            x = clampX(x + step);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            y = clampY(y - step);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            y = clampY(y + step);
            return true;
        }
        return false;
    }

    public void reset() {
        scale = defaultScale;
        updateElementSize();
        x = clampX(defaultX);
        y = clampY(defaultY);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getElementWidth() {
        return elementWidth;
    }

    public int getElementHeight() {
        return elementHeight;
    }

    public double getScale() {
        return scale;
    }

    public State snapshot() {
        return new State(x, y, scale);
    }

    public void restore(State state) {
        if (state == null) return;
        scale = sanitizeScale(state.scale());
        updateElementSize();
        x = clampX(state.x());
        y = clampY(state.y());
        dragging = false;
    }

    private void renderGuides(GuiGraphics graphics) {
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        graphics.fill(centerX, 52, centerX + 1, screenHeight - 38, 0x44FFFFFF);
        graphics.fill(0, centerY, screenWidth, centerY + 1, 0x44FFFFFF);
    }

    private void renderElement(GuiGraphics graphics, int mouseX, int mouseY, ElementRenderer renderer) {
        boolean hovered = isElementHovered(mouseX, mouseY);
        int borderColor = dragging ? 0xFFFFAA00 : hovered ? 0xFFFFFFFF : 0xAAFFFFFF;
        int left = x - ELEMENT_PADDING;
        int top = y - ELEMENT_PADDING;
        int right = safeAdd(x, elementWidth + ELEMENT_PADDING);
        int bottom = safeAdd(y, elementHeight + ELEMENT_PADDING);

        graphics.fill(left, top, right, top + 1, borderColor);
        graphics.fill(left, bottom - 1, right, bottom, borderColor);
        graphics.fill(left, top, left + 1, bottom, borderColor);
        graphics.fill(right - 1, top, right, bottom, borderColor);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0x66000000);

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale((float) scale, (float) scale, 1.0F);
        graphics.pose().translate(-x, -y, 0.0F);
        renderer.render(graphics, x, y, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private boolean isElementHovered(double mouseX, double mouseY) {
        return mouseX >= x - ELEMENT_PADDING
                && mouseX < (double) x + elementWidth + ELEMENT_PADDING
                && mouseY >= y - ELEMENT_PADDING
                && mouseY < (double) y + elementHeight + ELEMENT_PADDING;
    }

    private void updateElementSize() {
        elementWidth = scaledSize(baseElementWidth, scale);
        elementHeight = scaledSize(baseElementHeight, scale);
    }

    private int clampX(int value) {
        return Mth.clamp(value, 0, Math.max(0, screenWidth - elementWidth));
    }

    private int clampY(int value) {
        return Mth.clamp(value, 0, Math.max(0, screenHeight - elementHeight));
    }

    private static int scaledSize(int baseSize, double scale) {
        double result = Math.ceil(baseSize * scale);
        if (!Double.isFinite(result) || result >= SAFE_MAX_SIZE) {
            return SAFE_MAX_SIZE;
        }
        return Math.max(1, (int) result);
    }

    private double sanitizeScale(double value) {
        if (!Double.isFinite(value)) {
            return Math.max(minimumScale, 1.0D);
        }
        return Math.max(minimumScale, value);
    }

    private static double sanitizeMinimumScale(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : MIN_SCALE;
    }

    private static int safeAdd(int base, int delta) {
        long result = (long) base + delta;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }
}
