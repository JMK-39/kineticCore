package dev.xyat.kineticcore.api.client.color;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class ColorPickerApi {
    public static final int DEFAULT_MAX_PALETTE_COLORS = 24;

    private ColorPickerApi() {
    }

    public static Screen createColorPicker(
            Screen parent,
            Component title,
            int initialRgb,
            Consumer<Integer> onApply
    ) {
        return ColorPickerScreen.single(
                parent,
                title,
                initialRgb,
                Objects.requireNonNull(onApply, "onApply")
        );
    }

    public static void openColorPicker(
            Screen parent,
            Component title,
            int initialRgb,
            Consumer<Integer> onApply
    ) {
        Minecraft.getInstance().setScreen(createColorPicker(parent, title, initialRgb, onApply));
    }

    public static Screen createPalettePicker(
            Screen parent,
            Component title,
            List<Integer> initialColors,
            int maxColors,
            Consumer<List<Integer>> onApply
    ) {
        List<Integer> safeColors = initialColors == null ? List.of() : new ArrayList<>(initialColors);
        return ColorPickerScreen.palette(
                parent,
                title,
                safeColors,
                Math.max(1, Math.min(DEFAULT_MAX_PALETTE_COLORS, maxColors)),
                Objects.requireNonNull(onApply, "onApply")
        );
    }

    public static void openPalettePicker(
            Screen parent,
            Component title,
            List<Integer> initialColors,
            int maxColors,
            Consumer<List<Integer>> onApply
    ) {
        Minecraft.getInstance().setScreen(createPalettePicker(parent, title, initialColors, maxColors, onApply));
    }

    public static void openPalettePicker(
            Screen parent,
            Component title,
            List<Integer> initialColors,
            Consumer<List<Integer>> onApply
    ) {
        openPalettePicker(parent, title, initialColors, DEFAULT_MAX_PALETTE_COLORS, onApply);
    }
}
