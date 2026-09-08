package dev.xyat.kineticcore.api.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class ColorPreviewButton extends Button {
    private int rgb;

    public ColorPreviewButton(int x, int y, int width, int height, int rgb, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.rgb = rgb & 0xFFFFFF;
    }

    public void setRgb(int rgb) {
        this.rgb = rgb & 0xFFFFFF;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        int size = Math.min(12, Math.max(8, getHeight() - 8));
        int x = getX() + 6;
        int y = getY() + (getHeight() - size) / 2;
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + size, y + size, 0xFF000000 | rgb);
    }
}
