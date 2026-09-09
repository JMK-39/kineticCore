package dev.xyat.kineticcore.feature.fps.client;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.selector.HudPositionEditor;
import dev.xyat.kineticcore.config.client.KTConfigScreen;
import dev.xyat.kineticcore.feature.fps.config.FpsClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FpsHudEditorScreen extends Screen {
    private final Screen parent;
    private final List<Component> previewLines;
    private final HudPositionEditor editor = new HudPositionEditor();

    public FpsHudEditorScreen(Screen parent) {
        super(Component.translatable("screen.kineticcore.fps.editor.title"));
        this.parent = parent;
        this.previewLines = FpsRenderer.createPreviewLines();
    }

    @Override
    protected void init() {
        int previewWidth = FpsRenderer.getContentWidth(font, previewLines);
        int previewHeight = FpsRenderer.getContentHeight(font, previewLines.size());
        double initialScale = FpsClientConfig.getHudScale();
        int initialScaledWidth = scaledSize(previewWidth, initialScale);
        int initialScaledHeight = scaledSize(previewHeight, initialScale);
        int initialDefaultX = width - initialScaledWidth - 2;
        int initialDefaultY = height - initialScaledHeight - 2 - FpsClientConfig.DEFAULT_OFFSET_Y;
        int defaultX = width - previewWidth - 2;
        int defaultY = height - previewHeight - 2 - FpsClientConfig.DEFAULT_OFFSET_Y;

        editor.initialize(
                width,
                height,
                previewWidth,
                previewHeight,
                initialDefaultX - FpsClientConfig.getHudOffsetX(),
                initialDefaultY - (FpsClientConfig.getHudOffsetY() - FpsClientConfig.DEFAULT_OFFSET_Y),
                defaultX,
                defaultY,
                initialScale,
                1.0D
        );

        editor.addControlButtons(
                this::addRenderableWidget,
                Component.translatable("gui.kineticcore.hud_editor.save"),
                Component.translatable("gui.kineticcore.hud_editor.reset"),
                Component.translatable("gui.kineticcore.hud_editor.cancel"),
                this::saveAndClose,
                this::closeWithoutSaving
        );
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        editor.render(
                graphics,
                font,
                mouseX,
                mouseY,
                title,
                Component.translatable("screen.kineticcore.hud_editor.instruction_scale"),
                Component.translatable(
                        "screen.kineticcore.hud_editor.position_scale",
                        Component.literal(String.valueOf(currentOffsetX())).withStyle(ChatFormatting.AQUA),
                        Component.literal(String.valueOf(currentOffsetY())).withStyle(ChatFormatting.AQUA),
                        Component.literal(String.valueOf(Math.round(editor.getScale() * 100.0D))).withStyle(ChatFormatting.YELLOW)
                ),
                (g, x, y, mx, my) -> FpsRenderer.renderLines(g, font, previewLines, x, y)
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editor.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (editor.mouseDragged(mouseX, mouseY, button)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (editor.mouseReleased(button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (editor.mouseScrolled(mouseX, mouseY, scrollDelta)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editor.keyPressed(keyCode, hasShiftDown())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        closeWithoutSaving();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void saveAndClose() {
        FpsClientConfig.setHudLayout(currentOffsetX(), currentOffsetY(), editor.getScale());
        if (parent instanceof KTConfigScreen configScreen) {
            configScreen.refreshFromSource();
        }
        closeScreen();
    }

    private void closeWithoutSaving() {
        closeScreen();
    }

    private void closeScreen() {
        Minecraft.getInstance().setScreen(parent);
    }

    private int currentOffsetX() {
        return width - editor.getElementWidth() - 2 - editor.getX();
    }

    private int currentOffsetY() {
        return height - editor.getElementHeight() - 2 - editor.getY();
    }

    private static int scaledSize(int baseSize, double scale) {
        double result = Math.ceil(baseSize * scale);
        if (!Double.isFinite(result) || result >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(1, (int) result);
    }
}
