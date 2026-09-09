package dev.xyat.kineticcore.feature.nbt.network;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.selector.NbtEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;

public final class NbtNetworkHandlerClient {
    private static final String TOAST_ID = "kineticcore_nbt_editor";
    private static Screen pendingEditorParent;

    private NbtNetworkHandlerClient() {
    }

    public static void requestOpen(byte targetType, String targetId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getConnection() == null) {
            handleNotify("gui.kineticcore.config.requires_world");
            return;
        }

        pendingEditorParent = minecraft.screen;
        NbtNetwork.sendToServer(new NbtNetwork.OpenNbtEditorRequestPacket(
                targetType,
                targetId,
                minecraft.level.dimension().location()
        ));
    }


    public static void handleCommandOpen(byte commandMode) {
        if (commandMode == NbtNetwork.COMMAND_OPEN_HAND) {
            requestOpen(NbtNetwork.TARGET_HAND, "");
            return;
        }

        if (commandMode == NbtNetwork.COMMAND_OPEN_CROSSHAIR) {
            requestCrosshairTarget();
            return;
        }

        handleNotify("gui.kineticcore.nbt.error.no_target");
    }

    private static void requestCrosshairTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        HitResult hitResult = minecraft.hitResult;

        if (hitResult instanceof EntityHitResult entityHitResult
                && hitResult.getType() == HitResult.Type.ENTITY) {
            requestOpen(
                    NbtNetwork.TARGET_ENTITY,
                    entityHitResult.getEntity().getUUID().toString()
            );
            return;
        }

        if (hitResult instanceof BlockHitResult blockHitResult
                && hitResult.getType() == HitResult.Type.BLOCK
                && minecraft.level != null) {
            BlockPos pos = blockHitResult.getBlockPos();
            if (minecraft.level.getBlockEntity(pos) != null) {
                requestOpen(
                        NbtNetwork.TARGET_BLOCK_ENTITY,
                        Long.toString(pos.asLong())
                );
                return;
            }
        }

        handleNotify("gui.kineticcore.nbt.error.no_target");
    }

    public static void handleOpenEditor(String nbt) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = pendingEditorParent != null ? pendingEditorParent : minecraft.screen;
        pendingEditorParent = null;
        minecraft.setScreen(new NbtEditorScreen(
                nbt,
                value -> NbtNetwork.sendToServer(new NbtNetwork.SaveNbtPacket(value)),
                parent
        ));
    }

    public static void handleNotify(String translationKey) {
        pendingEditorParent = null;
        GuiOverlay.toast(TOAST_ID, Component.translatable(translationKey));
    }
}
