package dev.xyat.kineticcore.feature.spawnegg.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.bootstrap.annotation.KTClientModule;
import dev.xyat.kineticcore.feature.spawnegg.SpawnEggInit;
import dev.xyat.kineticcore.feature.spawnegg.network.SpawnEggNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@KTClientModule
public final class SpawnEggClient {
    private static final String MODE_KEY = "DisableEggThrow";
    private static final String TOAST_ID = "spawn_egg_toggle";

    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.kineticcore.toggle_egg",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.kineticcore.category"
    );

    private static boolean registered;

    private SpawnEggClient() {
    }

    public static void register(IEventBus modEventBus) {
        if (registered) return;
        registered = true;
        modEventBus.addListener(SpawnEggClient::onRegisterKeyMappings);
        modEventBus.addListener(SpawnEggClient::onRegisterRenderers);
        MinecraftForge.EVENT_BUS.addListener(SpawnEggClient::onKeyInput);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SpawnEggInit.THROWABLE_SPAWN_EGG.get(), ThrowSpawnEggRenderer::new);
    }

    private static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) return;

        while (TOGGLE_KEY.consumeClick()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;

            boolean disabled = !player.getPersistentData().getBoolean(MODE_KEY);
            player.getPersistentData().putBoolean(MODE_KEY, disabled);
            GuiOverlay.toast(
                    TOAST_ID,
                    Component.translatable(disabled ? "tip.kineticcore.egg.vanilla" : "tip.kineticcore.egg.throw")
            );
            SpawnEggNetwork.sendModeToServer(disabled);
        }
    }
}
