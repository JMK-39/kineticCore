package dev.xyat.kineticcore.feature.mining.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.xyat.kineticcore.KineticCore;
import dev.xyat.kineticcore.feature.mining.network.MiningModeNetwork;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class MiningModeClient {

    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.kineticcore.toggle_mining_mode",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.kineticcore.category"
    );

    // 客户端本地状态，用于零延迟驱动 UI 提示
    public static boolean isSingleModeClientSide = false;

    @Mod.EventBusSubscriber(modid = KineticCore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = KineticCore.MODID, value = Dist.CLIENT)
    public static class ClientForgeBusEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                while (TOGGLE_KEY.consumeClick()) {
                    if (Minecraft.getInstance().player != null) {
                        // 切换本地显示状态
                        isSingleModeClientSide = !isSingleModeClientSide;

                        // 弹出 Toast 提醒（无物品要求，全局生效）
                        if (isSingleModeClientSide) {
                            GuiOverlay.toast("mining_mode_toggle", Component.translatable("tip.kineticcore.mining.mode.single"));
                        } else {
                            GuiOverlay.toast("mining_mode_toggle", Component.translatable("tip.kineticcore.mining.mode.normal"));
                        }

                        // 发送无参数空包给服务端，通知服务端翻转 NBT 状态
                        MiningModeNetwork.INSTANCE.sendToServer(new MiningModeNetwork.ToggleMiningModePacket());
                    }
                }
            }
        }
    }
}
