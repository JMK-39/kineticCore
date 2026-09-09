package dev.xyat.kineticcore.feature.defaultoptions.config;

import dev.xyat.kineticcore.KineticCore;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.bootstrap.annotation.KTClientModule;
import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.kineticcore.config.client.KTConfigPage;
import dev.xyat.kineticcore.config.client.KTConfigScope;
import dev.xyat.kineticcore.feature.defaultoptions.OptionsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@KTClientModule
public final class DefaultOptionsConfigGui {
    public static final String PAGE_ID = "kineticcore:default_options";

    private static final String TOAST_ID = "kineticcore_default_options_save";

    private DefaultOptionsConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.kineticcore.default_options")
                )
                .pageDescription(Component.translatable("cfg.kineticcore.default_options.description"))
                .scope(KTConfigScope.LOCAL_INSTALLATION)
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .action(
                        "save_current_options",
                        Component.translatable("cfg.kineticcore.default_options.save"),
                        DefaultOptionsConfigGui::confirmSave,
                        Component.translatable("cfg.kineticcore.default_options.save.tooltip")
                )
                .build());
    }

    private static void confirmSave() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(parent);
                    if (confirmed) saveCurrentOptions();
                },
                Component.translatable("gui.kineticcore.default_options.confirm.title"),
                Component.translatable("gui.kineticcore.default_options.confirm.message")
        ));
    }

    private static void saveCurrentOptions() {
        try {
            OptionsManager.saveAllSettingsAsDefault();
            GuiOverlay.toast(
                    TOAST_ID,
                    Component.translatable("gui.kineticcore.default_options.save.success")
                            .withStyle(ChatFormatting.GREEN)
            );
        } catch (Exception exception) {
            KineticCore.LOGGER.error("Failed to save the current options as defaults", exception);

            String detail = exception.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = exception.getClass().getSimpleName();
            }
            GuiOverlay.toast(
                    TOAST_ID,
                    Component.translatable(
                            "gui.kineticcore.default_options.save.failure",
                            Component.literal(detail).withStyle(ChatFormatting.RED)
                    ).withStyle(ChatFormatting.RED)
            );
        }
    }
}
