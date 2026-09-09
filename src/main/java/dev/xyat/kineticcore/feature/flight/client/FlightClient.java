package dev.xyat.kineticcore.feature.flight.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.xyat.kineticcore.KineticCore;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.feature.flight.network.FlightNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class FlightClient {
    public static boolean noclipEnabled = false;
    public static float storedFlightMultiplier = 1.0F;
    public static boolean inertiaEnabled = false;

    public static final KeyMapping NOCLIP_KEY = new KeyMapping(
            "key.kineticcore.flying.noclip",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.movement"
    );

    public static final KeyMapping SPEED_MOD_KEY = new KeyMapping(
            "key.kineticcore.flying.speed.modifier",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.movement"
    );

    public static final KeyMapping INERTIA_KEY = new KeyMapping(
            "key.kineticcore.flying.inertia",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            "key.kineticcore.category"
    );

    public static void applyServerNoclip(boolean state) {
        Minecraft mc = Minecraft.getInstance();
        noclipEnabled = state;
        if (mc.player != null) {
            mc.player.noPhysics = state;
            mc.player.refreshDimensions();
        }
    }

    public static void setNoclip(boolean state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (noclipEnabled == state) return;

        noclipEnabled = state;
        FlightNetwork.CHANNEL.sendToServer(new FlightNetwork.PacketNoclip(noclipEnabled));
        mc.player.noPhysics = noclipEnabled;
        mc.player.refreshDimensions();

        Component status = Component.translatable(noclipEnabled ? "options.on" : "options.off")
                .withStyle(noclipEnabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        mc.player.displayClientMessage(Component.translatable("msg.kineticcore.flying.noclip_status", status), true);
    }

    public static void toggleNoclip() {
        setNoclip(!noclipEnabled);
    }

    public static void toggleInertia() {
        inertiaEnabled = !inertiaEnabled;
        Component status = Component.translatable(inertiaEnabled ? "msg.kineticcore.flying.on" : "msg.kineticcore.flying.off")
                .withStyle(inertiaEnabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        GuiOverlay.toast("flight_inertia_toggle", Component.translatable("msg.kineticcore.flying.inertia_status", status));
    }

    @Mod.EventBusSubscriber(modid = KineticCore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(NOCLIP_KEY);
            event.register(SPEED_MOD_KEY);
            event.register(INERTIA_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = KineticCore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    InputConstants.Key inputKey = InputConstants.getKey(event.getKey(), event.getScanCode());

                    if (NOCLIP_KEY.isActiveAndMatches(inputKey) && mc.player.isCreative()) {
                        toggleNoclip();
                    } else if (INERTIA_KEY.isActiveAndMatches(inputKey)) {
                        toggleInertia();
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            noclipEnabled = false;
        }

        @SubscribeEvent
        public static void onBlockOverlay(RenderBlockScreenEffectEvent event) {
            if (noclipEnabled && event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK) {
                event.setCanceled(true);
            }
        }
    }
}
