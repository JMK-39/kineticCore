package dev.xyat.kineticcore.feature.copyitem.client;

import net.minecraft.ChatFormatting;
import com.mojang.blaze3d.platform.InputConstants;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.feature.copyitem.compat.jei.ItemCopyJeiPlugin;
import dev.xyat.kineticcore.feature.copyitem.mixin.client.AbstractContainerScreenAccessor;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import dev.xyat.kineticcore.KineticCore;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

public class ItemCopyManager {
    public static KeyMapping KEY_COPY_ITEM_ID;
    public static KeyMapping KEY_SHOW_DETAIL;

    private static final long TOOLTIP_FALLBACK_KEEP_MS = 750L;
    private static ItemStack tooltipFallbackStack = ItemStack.EMPTY;
    private static long tooltipFallbackTimeMs = 0L;

    @Mod.EventBusSubscriber(modid = KineticCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(KEY_COPY_ITEM_ID = new KeyMapping("key.kineticcore.copyitem.copy_item_id", KeyConflictContext.GUI, KeyModifier.ALT, InputConstants.Type.KEYSYM, InputConstants.KEY_C, "key.kineticcore.category"));
            event.register(KEY_SHOW_DETAIL = new KeyMapping("key.kineticcore.copyitem.copy_item_info", KeyConflictContext.GUI, KeyModifier.ALT, InputConstants.Type.KEYSYM, InputConstants.KEY_F, "key.kineticcore.category"));
        }
    }

    @Mod.EventBusSubscriber(modid = KineticCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeBusEvents {
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty()) return;

            tooltipFallbackStack = stack.copy();
            tooltipFallbackTimeMs = Util.getMillis();
        }

        @SubscribeEvent
        public static void onKeyInput(ScreenEvent.KeyPressed.Pre event) {
            InputConstants.Key inputKey = InputConstants.getKey(event.getKeyCode(), event.getScanCode());

            boolean copyKey = KEY_COPY_ITEM_ID != null && KEY_COPY_ITEM_ID.isActiveAndMatches(inputKey);
            boolean detailKey = KEY_SHOW_DETAIL != null && KEY_SHOW_DETAIL.isActiveAndMatches(inputKey);

            if (!copyKey && !detailKey) return;

            Minecraft mc = Minecraft.getInstance();
            ItemStack stack = resolveHoveredStack(event.getScreen());

            if (stack.isEmpty()) return;

            if (copyKey) {
                copyItem(mc, stack);
                event.setCanceled(true);
                return;
            }

            if (mc.player != null) {
                ItemDetailPrinter.showItemInfo(mc.player, stack);
                GuiOverlay.toast(Component.translatable("msg.kineticcore.copyitem.copy.chat_output.success"));
                event.setCanceled(true);
            }
        }
    }

    private static ItemStack resolveHoveredStack(Screen screen) {
        ItemStack jeiStack = getJeiHoveredStack();
        if (!jeiStack.isEmpty()) return jeiStack;

        ItemStack containerStack = getContainerHoveredStack(screen);
        if (!containerStack.isEmpty()) return containerStack;

        if (!tooltipFallbackStack.isEmpty() && Util.getMillis() - tooltipFallbackTimeMs <= TOOLTIP_FALLBACK_KEEP_MS) {
            return tooltipFallbackStack.copy();
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack getJeiHoveredStack() {
        if (!ModList.get().isLoaded("jei")) {
            return ItemStack.EMPTY;
        }

        return ItemCopyJeiPlugin.getHoveredItemStack();
    }

    private static ItemStack getContainerHoveredStack(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return ItemStack.EMPTY;
        }

        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;

        Slot hoveredSlot = accessor.kineticcore$getHoveredSlot();
        if (isValidSlot(hoveredSlot)) {
            return hoveredSlot.getItem().copy();
        }

        Slot mouseSlot = findSlotByMouse(containerScreen, accessor);
        if (isValidSlot(mouseSlot)) {
            return mouseSlot.getItem().copy();
        }

        ItemStack carried = containerScreen.getMenu().getCarried();
        if (!carried.isEmpty()) {
            return carried.copy();
        }

        return ItemStack.EMPTY;
    }

    private static Slot findSlotByMouse(AbstractContainerScreen<?> screen, AbstractContainerScreenAccessor accessor) {
        Minecraft mc = Minecraft.getInstance();

        double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();

        int left = accessor.kineticcore$getLeftPos();
        int top = accessor.kineticcore$getTopPos();

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;

            int slotX = left + slot.x;
            int slotY = top + slot.y;

            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                return slot;
            }
        }

        return null;
    }

    private static boolean isValidSlot(Slot slot) {
        return slot != null && slot.isActive() && slot.hasItem() && !slot.getItem().isEmpty();
    }

    private static void copyItem(Minecraft mc, ItemStack stack) {
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemKey == null) return;

        String itemId = itemKey.toString();
        String result;

        if (stack.hasTag() && stack.getTag() != null) {
            String tag = stack.getTag().toString().replace("\\", "\\\\").replace("'", "\\'");
            result = "Item.of(\"" + itemId + "\", '" + tag + "')";
        } else {
            result = "\"" + itemId + "\"";
        }

        mc.keyboardHandler.setClipboard(result);
        GuiOverlay.toast(Component.translatable("msg.kineticcore.copyitem.copy.item_id.success", Component.literal(result).withStyle(ChatFormatting.AQUA)));
    }
}
