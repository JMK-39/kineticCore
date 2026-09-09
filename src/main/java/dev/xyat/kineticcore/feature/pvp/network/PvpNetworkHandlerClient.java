package dev.xyat.kineticcore.feature.pvp.network;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import net.minecraft.network.chat.Component;

public class PvpNetworkHandlerClient {

    public static void handleState(boolean enabled) {
        String key = enabled ? "cmd.kineticcore.pvp.enabled" : "cmd.kineticcore.pvp.disabled";
        GuiOverlay.toast("pvp_toggle", Component.translatable(key));
    }
}
