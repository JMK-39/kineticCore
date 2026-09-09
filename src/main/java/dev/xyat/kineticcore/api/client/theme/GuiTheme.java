package dev.xyat.kineticcore.api.client.theme;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;

import java.util.Objects;

public final class GuiTheme {
    public record Palette(
            int background,
            int panel,
            int panelAlt,
            int border,
            int accent,
            int accentHover,
            int field,
            int text,
            int mutedText,
            int danger,
            int scrollTrack,
            int scrollThumb,
            int scrollThumbHover,
            int shadow
    ) {
    }

    public static final Palette DEFAULT = new Palette(
            0xCC0A0A0A,
            0xE01B1B1B,
            0xE0262626,
            0xFF666666,
            0xFF777777,
            0xFFAAAAAA,
            0xE0101010,
            0xFFFFFFFF,
            0xFFB0B0B0,
            0xFFE05A5A,
            0xFF171717,
            0xFFFF9800,
            0xFFFFD700,
            0xC0000000
    );

    private static volatile Palette current = DEFAULT;

    private static final ResourceLocation ITEM_GRID_TEXTURE = new ResourceLocation("kineticcore", "textures/gui/item_selector_checkerboard.png");
    private static final int ITEM_GRID_TEXTURE_WIDTH = 475;
    private static final int ITEM_GRID_TEXTURE_HEIGHT = 304;

    private GuiTheme() {
    }

    public static Palette current() {
        return current;
    }

    public static void use(Palette palette) {
        current = Objects.requireNonNullElse(palette, DEFAULT);
    }

    public static void reset() {
        current = DEFAULT;
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        panel(graphics, x, y, width, height, current.panel(), current.border());
    }

    public static void panelAlt(GuiGraphics graphics, int x, int y, int width, int height) {
        panel(graphics, x, y, width, height, current.panelAlt(), current.border());
    }

    public static void panel(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int background,
            int border
    ) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, background);
        graphics.renderOutline(x, y, width, height, border);
    }

    public static void shadow(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, current.shadow());
    }

    public static void checkerboard(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int cellSize,
            int colorA,
            int colorB
    ) {
        int size = Math.max(1, cellSize);
        for (int yy = 0; yy < height; yy += size) {
            int drawHeight = Math.min(size, height - yy);
            for (int xx = 0; xx < width; xx += size) {
                int drawWidth = Math.min(size, width - xx);
                boolean first = ((xx / size) + (yy / size)) % 2 == 0;
                graphics.fill(
                        x + xx,
                        y + yy,
                        x + xx + drawWidth,
                        y + yy + drawHeight,
                        first ? colorA : colorB
                );
            }
        }
    }

    public static String trim(Font font, String text, int width) {
        if (text == null || text.isEmpty()) return "";
        if (font.width(text) <= width) return text;
        int ellipsis = font.width("...");
        return font.plainSubstrByWidth(text, Math.max(0, width - ellipsis)) + "...";
    }

    public static boolean hovering(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static int thumbSize(int trackSize, int visibleItems, int totalItems, int minimum) {
        if (trackSize <= 0) return 0;
        if (totalItems <= 0 || visibleItems >= totalItems) return trackSize;
        int size = Math.round(trackSize * (visibleItems / (float) totalItems));
        return Mth.clamp(size, Math.min(trackSize, Math.max(1, minimum)), trackSize);
    }

    public static int scrollOffset(
            double mouse,
            int trackStart,
            int trackSize,
            int thumbSize,
            int maxOffset,
            int grabOffset
    ) {
        int travel = Math.max(0, trackSize - thumbSize);
        if (travel <= 0 || maxOffset <= 0) return 0;
        double relative = mouse - trackStart - grabOffset;
        return Mth.clamp((int) Math.round(relative / travel * maxOffset), 0, maxOffset);
    }

    public static void scrollbar(
            GridScrollController controller,
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            int height,
            int minThumbHeight
    ) {
        if (controller == null) return;
        controller.renderFramed(
                graphics,
                mouseX,
                mouseY,
                x,
                y,
                width,
                height,
                minThumbHeight,
                current.border(),
                current.scrollTrack(),
                current.scrollThumb(),
                current.scrollThumbHover()
        );
    }

    public static void scrollbar(
            GuiGraphics graphics,
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height,
            int thumbHeight,
            int maxOffset,
            int offset,
            boolean dragging
    ) {
        scrollbar(graphics, mouseX, mouseY, x, y, width, height, thumbHeight, maxOffset, (double) offset, dragging);
    }

    public static void scrollbar(
            GuiGraphics graphics,
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height,
            int thumbHeight,
            int maxOffset,
            double offset,
            boolean dragging
    ) {
        if (maxOffset <= 0 || width <= 0 || height <= 0) return;
        int safeThumb = Mth.clamp(thumbHeight, 1, height);
        double safeOffset = Math.max(0D, Math.min(offset, maxOffset));
        int thumbY = y + (int) Math.round((height - safeThumb) * (safeOffset / maxOffset));
        boolean hovered = hovering(mouseX, mouseY, x, thumbY, width, safeThumb);
        graphics.fill(x, y, x + width, y + height, current.border());
        if (width > 2 && height > 2) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, current.scrollTrack());
        }
        graphics.fill(
                x,
                thumbY,
                x + width,
                thumbY + safeThumb,
                dragging || hovered ? current.scrollThumbHover() : current.scrollThumb()
        );
    }

    public static void itemSlot(GuiGraphics graphics, int x, int y) {
        itemSlot(graphics, x, y, 18, false);
    }

    public static void itemSlot(GuiGraphics graphics, int x, int y, boolean hovered) {
        itemSlot(graphics, x, y, 18, hovered);
    }

    public static void itemSlot(GuiGraphics graphics, int x, int y, int size, int cellSize, boolean hovered) {
        itemSlot(graphics, x, y, size, hovered);
    }

    public static void itemSlot(GuiGraphics graphics, ItemStack stack, int x, int y, int size, int cellSize, boolean hovered) {
        itemSlot(graphics, x, y, size, hovered);
    }

    public static void itemSlot(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int cellSize,
            boolean hovered
    ) {
        if (graphics == null || width <= 0 || height <= 0) return;
        drawItemGridTexture(graphics, x, y, width, height, 0, 0, 18, 18);
        if (hovered) graphics.renderOutline(x, y, width, height, current.accentHover());
    }

    public static void itemSlot(GuiGraphics graphics, ItemStack stack, int x, int y) {
        itemSlot(graphics, x, y, 18, false);
    }

    public static void itemSlot(GuiGraphics graphics, ItemStack stack, int x, int y, boolean hovered) {
        itemSlot(graphics, x, y, 18, hovered);
    }

    public static void itemSlot(
            GuiGraphics graphics,
            ItemStack stack,
            int x,
            int y,
            int width,
            int height,
            int cellSize,
            boolean hovered
    ) {
        itemSlot(graphics, x, y, width, height, cellSize, hovered);
    }

    public static void itemSlot(GuiGraphics graphics, int x, int y, int size, boolean hovered) {
        if (graphics == null || size <= 0) return;
        drawItemGridTexture(graphics, x, y, size, size, 0, 0, 18, 18);
        if (hovered) graphics.renderOutline(x, y, size, size, current.accentHover());
    }

    public static void itemGrid(
            GuiGraphics graphics,
            ItemStack stack,
            int x,
            int y,
            int width,
            int height,
            int cellSize,
            boolean hovered
    ) {
        itemGrid(graphics, x, y, width, height, cellSize);
    }

    public static void itemGrid(GuiGraphics graphics, int x, int y, int width, int height, int cellSize) {
        itemGrid(graphics, x, y, width, height);
    }

    public static void itemGrid(GuiGraphics graphics, int x, int y, int width, int height) {
        if (graphics == null || width <= 0 || height <= 0) return;
        drawItemGridTexture(graphics, x, y, width, height, 1, 1, 16, 16);
    }

    public static void itemSelectorGrid(GuiGraphics graphics, int x, int y) {
        itemSelectorGrid(graphics, x, y, 25, 16);
    }

    public static void itemSelectorGrid(GuiGraphics graphics, int x, int y, int columns, int rows) {
        if (graphics == null || columns <= 0 || rows <= 0) return;
        int width = Math.min(ITEM_GRID_TEXTURE_WIDTH, columns * 19);
        int height = Math.min(ITEM_GRID_TEXTURE_HEIGHT, rows * 19);
        graphics.blit(
                ITEM_GRID_TEXTURE,
                x,
                y,
                0,
                0,
                width,
                height,
                ITEM_GRID_TEXTURE_WIDTH,
                ITEM_GRID_TEXTURE_HEIGHT
        );
    }

    public static void item(
            GuiGraphics graphics,
            Font font,
            ItemStack stack,
            int x,
            int y,
            int slotSize,
            float scale,
            boolean decorations
    ) {
        if (graphics == null || stack == null || stack.isEmpty() || scale <= 0f) return;
        float renderSize = 16f * scale;
        float offset = (slotSize - renderSize) / 2f;
        RenderSystem.enableDepthTest();
        graphics.pose().pushPose();
        graphics.pose().translate(x + offset, y + offset, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(stack, 0, 0);
        if (decorations && font != null) graphics.renderItemDecorations(font, stack, 0, 0);
        graphics.pose().popPose();
        RenderSystem.disableDepthTest();
    }

    private static void drawItemGridTexture(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight
    ) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(width / (float) sourceWidth, height / (float) sourceHeight, 1f);
        graphics.blit(
                ITEM_GRID_TEXTURE,
                0,
                0,
                sourceX,
                sourceY,
                sourceWidth,
                sourceHeight,
                ITEM_GRID_TEXTURE_WIDTH,
                ITEM_GRID_TEXTURE_HEIGHT
        );
        graphics.pose().popPose();
    }
}
