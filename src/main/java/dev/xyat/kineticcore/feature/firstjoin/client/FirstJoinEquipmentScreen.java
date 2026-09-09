package dev.xyat.kineticcore.feature.firstjoin.client;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import dev.xyat.kineticcore.api.client.selector.ItemSelectorScreen;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.selector.NbtEditorScreen;
import dev.xyat.kineticcore.config.client.KTServerConfigClient;
import dev.xyat.kineticcore.feature.firstjoin.config.PlayerConfig;
import dev.xyat.kineticcore.feature.firstjoin.config.PlayerConfigGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FirstJoinEquipmentScreen extends KineticScreen {
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_Y = 116;
    private static final int SLOT_GAP = 70;
    private static final int FIRST_SLOT_X = 161;
    private static final Pattern STACK_PATTERN = Pattern.compile("^(\\d+)[xX]\\s+(.*)$");

    private static final List<SlotDefinition> SLOT_DEFINITIONS = List.of(
            new SlotDefinition("helmet", "gui.kineticcore.firstjoin.equipment.helmet"),
            new SlotDefinition("chestplate", "gui.kineticcore.firstjoin.equipment.chestplate"),
            new SlotDefinition("leggings", "gui.kineticcore.firstjoin.equipment.leggings"),
            new SlotDefinition("boots", "gui.kineticcore.firstjoin.equipment.boots"),
            new SlotDefinition("offhand", "gui.kineticcore.firstjoin.equipment.offhand")
    );

    private final Screen parent;
    private final Map<String, ItemStack> stacks = new LinkedHashMap<>();
    private String hoveredSlot;

    public FirstJoinEquipmentScreen(Screen parent) {
        super(Component.translatable("gui.kineticcore.firstjoin.equipment.title"));
        this.parent = parent;
        useCanvas(640, 360, 6);
        loadStacks();
    }

    private void loadStacks() {
        stacks.put("helmet", parseStack(KTServerConfigClient.getString(PlayerConfigGui.PAGE_ID, "helmet", PlayerConfig.helmetId)));
        stacks.put("chestplate", parseStack(KTServerConfigClient.getString(PlayerConfigGui.PAGE_ID, "chestplate", PlayerConfig.chestplateId)));
        stacks.put("leggings", parseStack(KTServerConfigClient.getString(PlayerConfigGui.PAGE_ID, "leggings", PlayerConfig.leggingsId)));
        stacks.put("boots", parseStack(KTServerConfigClient.getString(PlayerConfigGui.PAGE_ID, "boots", PlayerConfig.bootsId)));
        stacks.put("offhand", parseStack(KTServerConfigClient.getString(PlayerConfigGui.PAGE_ID, "offhand", PlayerConfig.offhandId)));
    }

    private ItemStack parseStack(String value) {
        if (value == null || value.isBlank()) {
            return ItemStack.EMPTY;
        }

        try {
            String normalized = value.replace("\r", "").replace("\n", "").trim();
            int count = 1;
            String itemPart = normalized;
            Matcher matcher = STACK_PATTERN.matcher(normalized);
            if (matcher.matches()) {
                count = Integer.parseInt(matcher.group(1));
                itemPart = matcher.group(2).trim();
            }

            String itemId = itemPart;
            CompoundTag tag = null;
            int tagStart = itemPart.indexOf('{');
            if (tagStart >= 0) {
                tag = TagParser.parseTag(itemPart.substring(tagStart));
                itemId = itemPart.substring(0, tagStart).trim();
            }

            itemId = itemId.toLowerCase(Locale.ROOT);
            if (!itemId.contains(":")) {
                itemId = "minecraft:" + itemId;
            }

            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) {
                return ItemStack.EMPTY;
            }

            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }

            ItemStack stack = new ItemStack(item, Math.max(1, count));
            if (tag != null) {
                stack.setTag(tag);
            }
            return stack;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    protected void buildUi() {
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.config.back"),
                        button -> saveAndClose())
                .bounds(270, 292, 100, 20)
                .build());
    }

    private void openItemSelector(String slotKey) {
        ItemSearchIndex.prepareCache(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ItemSelectorScreen(this, selection -> {
                if (selection == null || !selection.isItem()) {
                    return;
                }
                stacks.put(slotKey, selection.stack().copy());
            }));
        });
    }

    private void openNbtEditor(String slotKey) {
        ItemStack stack = stacks.get(slotKey);
        if (stack == null || stack.isEmpty()) {
            return;
        }

        String initialNbt = stack.hasTag() && stack.getTag() != null
                ? stack.getTag().toString()
                : "";

        Minecraft.getInstance().setScreen(new NbtEditorScreen(initialNbt, value -> {
            if (value == null || value.isBlank()) {
                stack.setTag(null);
                return;
            }
            try {
                stack.setTag(TagParser.parseTag(value));
            } catch (Exception ignored) {
            }
        }, this));
    }

    private void clearSlot(String slotKey) {
        stacks.put(slotKey, ItemStack.EMPTY);
    }

    private void saveAndClose() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("helmet", PlayerConfig.serializeItemStack(stacks.get("helmet")));
        values.put("chestplate", PlayerConfig.serializeItemStack(stacks.get("chestplate")));
        values.put("leggings", PlayerConfig.serializeItemStack(stacks.get("leggings")));
        values.put("boots", PlayerConfig.serializeItemStack(stacks.get("boots")));
        values.put("offhand", PlayerConfig.serializeItemStack(stacks.get("offhand")));
        if (!KTServerConfigClient.savePartial(PlayerConfigGui.PAGE_ID, values)) {
            GuiOverlay.toast(Component.translatable("gui.kineticcore.config.server.save_failed"));
            return;
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private int slotX(int index) {
        return FIRST_SLOT_X + index * SLOT_GAP;
    }

    private String slotAt(double mouseX, double mouseY) {
        for (int index = 0; index < SLOT_DEFINITIONS.size(); index++) {
            int x = slotX(index);
            if (GuiTheme.hovering(mouseX, mouseY, x, SLOT_Y, SLOT_SIZE, SLOT_SIZE)) {
                return SLOT_DEFINITIONS.get(index).key();
            }
        }
        return null;
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 70, 42, 500, 226);
        graphics.drawCenteredString(font, title, canvasWidth / 2, 58, 0xFFFFFF);
        hoveredSlot = slotAt(mouseX, mouseY);

        for (int index = 0; index < SLOT_DEFINITIONS.size(); index++) {
            SlotDefinition definition = SLOT_DEFINITIONS.get(index);
            int x = slotX(index);
            boolean hovered = definition.key().equals(hoveredSlot);
            ItemStack stack = stacks.getOrDefault(definition.key(), ItemStack.EMPTY);

            graphics.drawCenteredString(
                    font,
                    Component.translatable(definition.labelKey()),
                    x + SLOT_SIZE / 2,
                    SLOT_Y - 17,
                    0xFFFFFF
            );
            GuiTheme.itemSlot(graphics, x, SLOT_Y, SLOT_SIZE, 4, hovered);
            GuiTheme.item(
                    graphics,
                    font,
                    stack,
                    x,
                    SLOT_Y,
                    SLOT_SIZE,
                    1.0F,
                    true
            );
        }

        graphics.drawCenteredString(
                font,
                Component.translatable("gui.kineticcore.firstjoin.equipment.hint"),
                canvasWidth / 2,
                210,
                0xFFFFFF
        );
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
    ) {
        String slotKey = slotAt(scaledMouseX, scaledMouseY);
        if (slotKey == null) {
            return;
        }

        ItemStack stack = stacks.getOrDefault(slotKey, ItemStack.EMPTY);
        if (stack.isEmpty()) {
            return;
        }
        showItemTooltip(stack);
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        String slotKey = slotAt(mouseX, mouseY);
        if (slotKey != null) {
            if (button == 0) {
                openItemSelector(slotKey);
                return true;
            }
            if (button == 1) {
                openNbtEditor(slotKey);
                return true;
            }
            if (button == 2) {
                clearSlot(slotKey);
                return true;
            }
        }
        return super.canvasMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record SlotDefinition(String key, String labelKey) {
    }
}
