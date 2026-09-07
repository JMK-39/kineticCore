package dev.xyat.kineticcore.api.client.color;

import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.gui.NumericEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class ColorPickerScreen extends ScaledScreen {
    private static final int CANVAS_W = 640;
    private static final int CANVAS_H = 360;
    private static final int PANEL_X = 120;
    private static final int PANEL_Y = 45;
    private static final int PANEL_W = 400;
    private static final int PANEL_H = 270;
    private static final int PICKER_X = 145;
    private static final int PICKER_Y = 90;
    private static final int PICKER_SIZE = 128;
    private static final int HUE_X = 280;
    private static final int HUE_W = 10;
    private static final int SIDE_X = 304;
    private static final int SIDE_W = 190;
    private static final int SWATCH_X = 145;
    private static final int SWATCH_Y = 247;
    private static final int SWATCH_CELL = 16;
    private static final int SWATCH_GAP = 3;
    private static final int SWATCH_COLS = 12;
    private static final int CONTEXT_W = 116;
    private static final int CONTEXT_ROW_H = 18;

    private record ContextEntry(Component label, Runnable action, boolean active) {
    }

    private final Screen parent;
    private final boolean paletteMode;
    private final int maxColors;
    private final Consumer<Integer> colorConsumer;
    private final Consumer<List<Integer>> paletteConsumer;
    private final List<Integer> colors = new ArrayList<>();

    private EditBox hexBox;
    private NumericEditBox redBox;
    private NumericEditBox greenBox;
    private NumericEditBox blueBox;
    private int rgb;
    private float hue;
    private float saturation;
    private float value;
    private boolean squareDragging;
    private boolean hueDragging;
    private boolean syncingFields;
    private List<ContextEntry> contextEntries = List.of();
    private int contextX;
    private int contextY;

    static ColorPickerScreen single(
            Screen parent,
            Component title,
            int initialRgb,
            Consumer<Integer> onApply
    ) {
        return new ColorPickerScreen(parent, title, false, initialRgb, List.of(), 1, onApply, null);
    }

    static ColorPickerScreen palette(
            Screen parent,
            Component title,
            List<Integer> initialColors,
            int maxColors,
            Consumer<List<Integer>> onApply
    ) {
        int initialRgb = initialColors.isEmpty() ? 0xFF00FF : initialColors.get(0);
        return new ColorPickerScreen(parent, title, true, initialRgb, initialColors, maxColors, null, onApply);
    }

    private ColorPickerScreen(
            Screen parent,
            Component title,
            boolean paletteMode,
            int initialRgb,
            List<Integer> initialColors,
            int maxColors,
            Consumer<Integer> colorConsumer,
            Consumer<List<Integer>> paletteConsumer
    ) {
        super(title);
        this.parent = parent;
        this.paletteMode = paletteMode;
        this.maxColors = Math.max(1, maxColors);
        this.colorConsumer = colorConsumer;
        this.paletteConsumer = paletteConsumer;
        configureResponsiveCanvas(CANVAS_W, CANVAS_H, 6);
        maxScale = 1.0f;
        for (Integer color : initialColors) {
            if (color != null && colors.size() < this.maxColors) {
                colors.add(color & 0xFFFFFF);
            }
        }
        setRgb(initialRgb, false);
    }

    @Override
    protected void initScaled() {
        Button cancel = Button.builder(
                        Component.translatable("gui.kineticcore.palette.cancel"),
                        button -> onClose()
                )
                .bounds(PANEL_X + PANEL_W - 128, PANEL_Y + 10, 56, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.kineticcore.palette.cancel.tooltip")))
                .build();
        addRenderableWidget(cancel);

        Button apply = Button.builder(
                        Component.translatable("gui.kineticcore.palette.apply"),
                        button -> applyAndClose()
                )
                .bounds(PANEL_X + PANEL_W - 66, PANEL_Y + 10, 56, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.kineticcore.palette.apply.tooltip")))
                .build();
        addRenderableWidget(apply);

        hexBox = new EditBox(
                font,
                SIDE_X + 45,
                PICKER_Y + 33,
                SIDE_W - 45,
                18,
                Component.translatable("gui.kineticcore.palette.hex")
        );
        hexBox.setMaxLength(6);
        hexBox.setFilter(raw -> raw.matches("[0-9a-fA-F]{0,6}"));
        hexBox.setResponder(this::onHexChanged);
        hexBox.setTooltip(Tooltip.create(Component.translatable("gui.kineticcore.palette.hex.tooltip")));
        addRenderableWidget(hexBox);

        redBox = createRgbBox(PICKER_Y + 59, "gui.kineticcore.palette.red");
        greenBox = createRgbBox(PICKER_Y + 83, "gui.kineticcore.palette.green");
        blueBox = createRgbBox(PICKER_Y + 107, "gui.kineticcore.palette.blue");

        Button copy = Button.builder(
                        Component.translatable("gui.kineticcore.palette.copy_hex"),
                        button -> Minecraft.getInstance().keyboardHandler.setClipboard(String.format(Locale.ROOT, "#%06X", rgb))
                )
                .bounds(SIDE_X, PICKER_Y + 137, 89, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.kineticcore.palette.copy_hex.tooltip")))
                .build();
        addRenderableWidget(copy);

        if (paletteMode) {
            Button add = Button.builder(
                            Component.translatable("gui.kineticcore.palette.add"),
                            button -> addCurrentColor()
                    )
                    .bounds(SIDE_X + 96, PICKER_Y + 137, 91, 18)
                    .tooltip(Tooltip.create(Component.translatable("gui.kineticcore.palette.add.tooltip")))
                    .build();
            add.active = colors.size() < maxColors;
            addRenderableWidget(add);
        }

        syncFields();
    }

    private NumericEditBox createRgbBox(int y, String key) {
        NumericEditBox box = NumericEditBox.integer(
                font,
                SIDE_X + 45,
                y,
                SIDE_W - 45,
                18,
                Component.translatable(key),
                false,
                0,
                255
        );
        box.setResponder(raw -> onRgbChanged());
        box.setTooltip(Tooltip.create(Component.translatable("gui.kineticcore.palette.rgb.tooltip")));
        addRenderableWidget(box);
        return box;
    }

    @Override
    protected void renderScaledBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiRenderUtil.drawShadowOverlay(graphics, vWidth, vHeight);
        GuiRenderUtil.drawStandardPanel(graphics, PANEL_X, PANEL_Y, PANEL_W, paletteMode ? PANEL_H : 215);
        graphics.drawCenteredString(font, title, PANEL_X + PANEL_W / 2, PANEL_Y + 15, 0xFFFFFF);

        graphics.drawString(font, Component.translatable("gui.kineticcore.palette.picker"), PICKER_X, PICKER_Y - 15, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.kineticcore.palette.current"), SIDE_X, PICKER_Y - 15, 0xFFFFFF, false);
        renderPicker(graphics);

        graphics.fill(SIDE_X, PICKER_Y, SIDE_X + SIDE_W, PICKER_Y + 24, 0xFF000000 | rgb);
        graphics.renderOutline(SIDE_X, PICKER_Y, SIDE_W, 24, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("gui.kineticcore.palette.hex"), SIDE_X, PICKER_Y + 38, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.kineticcore.palette.red"), SIDE_X, PICKER_Y + 64, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.kineticcore.palette.green"), SIDE_X, PICKER_Y + 88, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.kineticcore.palette.blue"), SIDE_X, PICKER_Y + 112, 0xFFFFFF, false);

        if (paletteMode) {
            graphics.drawString(
                    font,
                    Component.translatable("gui.kineticcore.palette.selected", colors.size(), maxColors),
                    SWATCH_X,
                    SWATCH_Y - 16,
                    0xFFFFFF,
                    false
            );
            renderSwatches(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderScaledForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderContextMenu(graphics, mouseX, mouseY);
    }

    private void renderPicker(GuiGraphics graphics) {
        for (int x = 0; x < PICKER_SIZE; x++) {
            float sat = x / (float) (PICKER_SIZE - 1);
            int top = 0xFF000000 | (Mth.hsvToRgb(hue, sat, 1.0f) & 0xFFFFFF);
            graphics.fillGradient(PICKER_X + x, PICKER_Y, PICKER_X + x + 1, PICKER_Y + PICKER_SIZE, top, 0xFF000000);
        }
        graphics.renderOutline(PICKER_X - 1, PICKER_Y - 1, PICKER_SIZE + 2, PICKER_SIZE + 2, 0xFFFFFFFF);

        int[] hues = {
                0xFFFF0000,
                0xFFFFFF00,
                0xFF00FF00,
                0xFF00FFFF,
                0xFF0000FF,
                0xFFFF00FF,
                0xFFFF0000
        };
        for (int i = 0; i < 6; i++) {
            int y1 = PICKER_Y + i * PICKER_SIZE / 6;
            int y2 = PICKER_Y + (i + 1) * PICKER_SIZE / 6;
            graphics.fillGradient(HUE_X, y1, HUE_X + HUE_W, y2, hues[i], hues[i + 1]);
        }
        graphics.renderOutline(HUE_X - 1, PICKER_Y - 1, HUE_W + 2, PICKER_SIZE + 2, 0xFFFFFFFF);

        int selectorX = PICKER_X + Math.round(saturation * (PICKER_SIZE - 1));
        int selectorY = PICKER_Y + Math.round((1.0f - value) * (PICKER_SIZE - 1));
        graphics.renderOutline(selectorX - 2, selectorY - 2, 5, 5, 0xFF000000);
        graphics.renderOutline(selectorX - 1, selectorY - 1, 3, 3, 0xFFFFFFFF);

        int hueY = PICKER_Y + Math.round(hue * (PICKER_SIZE - 1));
        graphics.fill(HUE_X - 2, hueY - 1, HUE_X + HUE_W + 2, hueY + 2, 0xFFFFFFFF);
        graphics.fill(HUE_X - 1, hueY, HUE_X + HUE_W + 1, hueY + 1, 0xFF000000);
    }

    private void renderSwatches(GuiGraphics graphics, int mouseX, int mouseY) {
        if (colors.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.kineticcore.palette.empty"), SWATCH_X, SWATCH_Y + 5, 0xFFFFFF, false);
            return;
        }
        for (int i = 0; i < colors.size(); i++) {
            int col = i % SWATCH_COLS;
            int row = i / SWATCH_COLS;
            int x = SWATCH_X + col * (SWATCH_CELL + SWATCH_GAP);
            int y = SWATCH_Y + row * (SWATCH_CELL + SWATCH_GAP);
            int color = colors.get(i) & 0xFFFFFF;
            graphics.fill(x, y, x + SWATCH_CELL, y + SWATCH_CELL, 0xFF000000 | color);
            graphics.renderOutline(x, y, SWATCH_CELL, SWATCH_CELL, 0xFFFFFFFF);
            if (GuiRenderUtil.isHovering(mouseX, mouseY, x, y, SWATCH_CELL, SWATCH_CELL)) {
                graphics.renderOutline(x - 1, y - 1, SWATCH_CELL + 2, SWATCH_CELL + 2, 0xFFFFB300);
            }
        }
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        if (!contextEntries.isEmpty()) {
            return;
        }
        if (GuiRenderUtil.isHovering(scaledMouseX, scaledMouseY, PICKER_X, PICKER_Y, PICKER_SIZE, PICKER_SIZE)) {
            graphics.renderTooltip(font, Component.translatable("gui.kineticcore.palette.picker.tooltip"), mouseX, mouseY);
            return;
        }
        if (GuiRenderUtil.isHovering(scaledMouseX, scaledMouseY, HUE_X - 2, PICKER_Y, HUE_W + 4, PICKER_SIZE)) {
            graphics.renderTooltip(font, Component.translatable("gui.kineticcore.palette.hue.tooltip"), mouseX, mouseY);
            return;
        }
        if (GuiRenderUtil.isHovering(scaledMouseX, scaledMouseY, SIDE_X, PICKER_Y, SIDE_W, 24)) {
            graphics.renderTooltip(
                    font,
                    Component.translatable("gui.kineticcore.palette.current.tooltip", String.format(Locale.ROOT, "%06X", rgb)),
                    mouseX,
                    mouseY
            );
            return;
        }
        if (paletteMode) {
            int index = swatchIndexAt(scaledMouseX, scaledMouseY);
            if (index >= 0 && index < colors.size()) {
                graphics.renderTooltip(
                        font,
                        Component.translatable("gui.kineticcore.palette.swatch.tooltip", String.format(Locale.ROOT, "%06X", colors.get(index))),
                        mouseX,
                        mouseY
                );
            }
        }
    }

    @Override
    protected boolean universalMouseClicked(double mouseX, double mouseY, int button) {
        if (!contextEntries.isEmpty()) {
            if (handleContextMenuClick(mouseX, mouseY, button)) {
                return true;
            }
            closeContextMenu();
            if (button == 0) {
                return true;
            }
        }

        if (button == 0) {
            if (GuiRenderUtil.isHovering(mouseX, mouseY, PICKER_X, PICKER_Y, PICKER_SIZE, PICKER_SIZE)) {
                squareDragging = true;
                updateFromSquare(mouseX, mouseY);
                return true;
            }
            if (GuiRenderUtil.isHovering(mouseX, mouseY, HUE_X - 2, PICKER_Y, HUE_W + 4, PICKER_SIZE)) {
                hueDragging = true;
                updateFromHue(mouseY);
                return true;
            }
            if (paletteMode) {
                int index = swatchIndexAt(mouseX, mouseY);
                if (index >= 0 && index < colors.size()) {
                    setRgb(colors.get(index), true);
                    return true;
                }
            }
        }

        if (button == 1 && paletteMode) {
            int index = swatchIndexAt(mouseX, mouseY);
            if (index >= 0) {
                openSwatchContextMenu(index, mouseX, mouseY);
                return true;
            }
        }
        return super.universalMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean universalMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (squareDragging || hueDragging)) {
            squareDragging = false;
            hueDragging = false;
            return true;
        }
        return super.universalMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean universalMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && squareDragging) {
            updateFromSquare(mouseX, mouseY);
            return true;
        }
        if (button == 0 && hueDragging) {
            updateFromHue(mouseY);
            return true;
        }
        return super.universalMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateFromSquare(double mouseX, double mouseY) {
        saturation = (float) Mth.clamp((mouseX - PICKER_X) / (PICKER_SIZE - 1.0), 0.0, 1.0);
        value = 1.0f - (float) Mth.clamp((mouseY - PICKER_Y) / (PICKER_SIZE - 1.0), 0.0, 1.0);
        rgb = Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
        syncFields();
    }

    private void updateFromHue(double mouseY) {
        hue = (float) Mth.clamp((mouseY - PICKER_Y) / (PICKER_SIZE - 1.0), 0.0, 1.0);
        if (hue >= 1.0f) {
            hue = 0.0f;
        }
        rgb = Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
        syncFields();
    }

    private void onHexChanged(String raw) {
        if (syncingFields || raw == null || !raw.matches("[0-9a-fA-F]{6}")) {
            return;
        }
        try {
            setRgb(Integer.parseInt(raw, 16), true);
        } catch (NumberFormatException ignored) {
        }
    }

    private void onRgbChanged() {
        if (syncingFields || redBox == null || greenBox == null || blueBox == null) {
            return;
        }
        Integer red = redBox.getIntValue();
        Integer green = greenBox.getIntValue();
        Integer blue = blueBox.getIntValue();
        if (red == null || green == null || blue == null) {
            return;
        }
        setRgb((red << 16) | (green << 8) | blue, true);
    }

    private void setRgb(int color, boolean sync) {
        rgb = color & 0xFFFFFF;
        float[] hsv = rgbToHsv(rgb);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        if (sync) {
            syncFields();
        }
    }

    private void syncFields() {
        if (hexBox == null || redBox == null || greenBox == null || blueBox == null) {
            return;
        }
        syncingFields = true;
        try {
            hexBox.setValue(String.format(Locale.ROOT, "%06X", rgb));
            redBox.setIntValue((rgb >> 16) & 0xFF);
            greenBox.setIntValue((rgb >> 8) & 0xFF);
            blueBox.setIntValue(rgb & 0xFF);
        } finally {
            syncingFields = false;
        }
    }

    private void addCurrentColor() {
        if (!paletteMode || colors.size() >= maxColors) {
            return;
        }
        int color = rgb & 0xFFFFFF;
        if (!colors.contains(color)) {
            colors.add(color);
        }
        rebuildColorPickerWidgets();
    }

    private void rebuildColorPickerWidgets() {
        clearWidgets();
        initScaled();
    }

    private int swatchIndexAt(double mouseX, double mouseY) {
        if (!paletteMode) {
            return -1;
        }
        for (int i = 0; i < colors.size(); i++) {
            int col = i % SWATCH_COLS;
            int row = i / SWATCH_COLS;
            int x = SWATCH_X + col * (SWATCH_CELL + SWATCH_GAP);
            int y = SWATCH_Y + row * (SWATCH_CELL + SWATCH_GAP);
            if (GuiRenderUtil.isHovering(mouseX, mouseY, x, y, SWATCH_CELL, SWATCH_CELL)) {
                return i;
            }
        }
        return -1;
    }

    private void openSwatchContextMenu(int index, double mouseX, double mouseY) {
        List<ContextEntry> entries = new ArrayList<>();
        entries.add(new ContextEntry(
                Component.translatable("gui.kineticcore.palette.context.load"),
                () -> {
                    if (index >= 0 && index < colors.size()) {
                        setRgb(colors.get(index), true);
                    }
                    closeContextMenu();
                },
                true
        ));
        entries.add(new ContextEntry(
                Component.translatable("gui.kineticcore.palette.context.delete"),
                () -> {
                    if (index >= 0 && index < colors.size()) {
                        colors.remove(index);
                    }
                    closeContextMenu();
                    rebuildColorPickerWidgets();
                },
                true
        ));
        entries.add(new ContextEntry(
                Component.translatable("gui.kineticcore.palette.context.clear"),
                () -> {
                    colors.clear();
                    closeContextMenu();
                    rebuildColorPickerWidgets();
                },
                !colors.isEmpty()
        ));
        openContextMenu(entries, mouseX, mouseY);
    }

    private void openContextMenu(List<ContextEntry> entries, double mouseX, double mouseY) {
        contextEntries = List.copyOf(entries);
        int height = contextEntries.size() * CONTEXT_ROW_H + 4;
        int x = (int) mouseX + 6;
        int y = (int) mouseY + 6;
        if (x + CONTEXT_W > CANVAS_W - 4) {
            x = (int) mouseX - CONTEXT_W - 6;
        }
        if (y + height > CANVAS_H - 4) {
            y = (int) mouseY - height - 6;
        }
        contextX = Mth.clamp(x, 4, CANVAS_W - CONTEXT_W - 4);
        contextY = Mth.clamp(y, 4, CANVAS_H - height - 4);
    }

    private void renderContextMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if (contextEntries.isEmpty()) {
            return;
        }
        int height = contextEntries.size() * CONTEXT_ROW_H + 4;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 500.0f);
        graphics.fill(contextX, contextY, contextX + CONTEXT_W, contextY + height, 0xF0181818);
        graphics.renderOutline(contextX, contextY, CONTEXT_W, height, 0xFFFFB300);
        for (int i = 0; i < contextEntries.size(); i++) {
            ContextEntry entry = contextEntries.get(i);
            int y = contextY + 2 + i * CONTEXT_ROW_H;
            boolean hovered = GuiRenderUtil.isHovering(mouseX, mouseY, contextX + 2, y, CONTEXT_W - 4, CONTEXT_ROW_H);
            if (hovered && entry.active()) {
                graphics.fill(contextX + 2, y, contextX + CONTEXT_W - 2, y + CONTEXT_ROW_H, 0x553C7FA8);
            }
            graphics.drawString(font, entry.label(), contextX + 7, y + 5, entry.active() ? 0xFFFFFF : 0xFF999999, false);
        }
        graphics.pose().popPose();
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY, int button) {
        if (button != 0 || contextEntries.isEmpty()) {
            return false;
        }
        int height = contextEntries.size() * CONTEXT_ROW_H + 4;
        if (!GuiRenderUtil.isHovering(mouseX, mouseY, contextX, contextY, CONTEXT_W, height)) {
            return false;
        }
        int row = (int) ((mouseY - contextY - 2) / CONTEXT_ROW_H);
        if (row >= 0 && row < contextEntries.size()) {
            ContextEntry entry = contextEntries.get(row);
            if (entry.active()) {
                entry.action().run();
            }
        }
        return true;
    }

    private void closeContextMenu() {
        contextEntries = List.of();
    }

    private void applyAndClose() {
        if (paletteMode) {
            if (paletteConsumer != null) {
                paletteConsumer.accept(new ArrayList<>(colors));
            }
        } else if (colorConsumer != null) {
            colorConsumer.accept(rgb & 0xFFFFFF);
        }
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static float[] rgbToHsv(int rgb) {
        float red = ((rgb >> 16) & 0xFF) / 255.0f;
        float green = ((rgb >> 8) & 0xFF) / 255.0f;
        float blue = (rgb & 0xFF) / 255.0f;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        float hueValue;
        if (delta == 0.0f) {
            hueValue = 0.0f;
        } else if (max == red) {
            hueValue = ((green - blue) / delta) % 6.0f;
        } else if (max == green) {
            hueValue = ((blue - red) / delta) + 2.0f;
        } else {
            hueValue = ((red - green) / delta) + 4.0f;
        }
        hueValue /= 6.0f;
        if (hueValue < 0.0f) {
            hueValue += 1.0f;
        }
        float saturationValue = max == 0.0f ? 0.0f : delta / max;
        return new float[]{hueValue, saturationValue, max};
    }
}
