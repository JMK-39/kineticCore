package dev.xyat.kineticcore.feature.firstjoin.client;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import dev.xyat.kineticcore.api.client.selector.ItemSelectorScreen;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.selector.NbtEditorScreen;
import dev.xyat.kineticcore.config.client.KTServerConfigClient;
import dev.xyat.kineticcore.feature.firstjoin.config.PlayerConfig;
import dev.xyat.kineticcore.feature.firstjoin.config.PlayerConfigGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FirstJoinRewardItemsScreen extends KineticScreen {
    private static final int PANEL_X = 24;
    private static final int PANEL_Y = 18;
    private static final int PANEL_W = 592;
    private static final int PANEL_H = 324;
    private static final int LIST_X = 44;
    private static final int LIST_Y = 58;
    private static final int LIST_W = 538;
    private static final int ROW_H = 32;
    private static final int VISIBLE_ROWS = 7;
    private static final int LIST_H = ROW_H * VISIBLE_ROWS;
    private static final int COUNT_LABEL_X = LIST_X + 12;
    private static final int COUNT_FIELD_X = LIST_X + 52;
    private static final int COUNT_FIELD_W = 30;
    private static final int ITEM_X = LIST_X + 94;
    private static final int ITEM_Y_OFFSET = 7;
    private static final int SLOT_SIZE = 18;
    private static final int SCROLL_X = LIST_X + LIST_W + 6;
    private static final int SCROLL_W = 4;
    private static final int MOVE_BUTTON_W = 30;
    private static final int DELETE_BUTTON_W = 48;
    private static final int BUTTON_GAP = 3;

    private final Screen parent;
    private final List<RewardEntry> entries = new ArrayList<>();
    private final List<RewardEntry> savedEntries = new ArrayList<>();
    private final GridScrollController scroll = new GridScrollController();
    private final List<EditBox> countFields = new ArrayList<>();
    private final List<Button> upButtons = new ArrayList<>();
    private final List<Button> downButtons = new ArrayList<>();
    private final List<Button> deleteButtons = new ArrayList<>();
    private boolean updatingCountFields;

    public FirstJoinRewardItemsScreen(Screen parent) {
        super(Component.translatable("gui.kineticcore.firstjoin.reward_items.title"));
        this.parent = parent;
        List<String> rawItems = KTServerConfigClient.getStringList(
                PlayerConfigGui.PAGE_ID,
                "items",
                PlayerConfig.firstJoinItemsRaw
        );
        int defaultSlot = 0;
        for (String raw : rawItems) {
            int slot = defaultSlot;
            String itemText = raw == null ? "" : raw.trim();
            if (itemText.startsWith("[")) {
                int end = itemText.indexOf(']');
                if (end > 1) {
                    try {
                        slot = Integer.parseInt(itemText.substring(1, end));
                        itemText = itemText.substring(end + 1).trim();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            ItemStack stack = PlayerConfig.parseItemStack(itemText);
            if (!stack.isEmpty()) {
                RewardEntry loaded = new RewardEntry(slot, stack.copy());
                entries.add(loaded);
                savedEntries.add(copyEntry(loaded));
            }
            defaultSlot++;
        }
        useCanvas(640F, 360F, 6);
    }

    @Override
    protected void buildUi() {
        resetScrollableWidgets();
        countFields.clear();
        upButtons.clear();
        downButtons.clear();
        deleteButtons.clear();
        updateScrollRange();

        int deleteX = LIST_X + LIST_W - DELETE_BUTTON_W - 4;
        int downX = deleteX - BUTTON_GAP - MOVE_BUTTON_W;
        int upX = downX - BUTTON_GAP - MOVE_BUTTON_W;

        for (int index = 0; index < entries.size(); index++) {
            final int entryIndex = index;
            int y = LIST_Y + index * ROW_H + 6;

            EditBox countField = new EditBox(
                    font,
                    COUNT_FIELD_X,
                    y,
                    COUNT_FIELD_W,
                    20,
                    Component.translatable("gui.kineticcore.firstjoin.reward_items.count")
            );
            countField.setMaxLength(3);
            countField.setFilter(value -> value.isEmpty() || value.matches("[1-9]\\d{0,2}"));
            countField.setResponder(value -> applyCount(entryIndex, value));
            countFields.add(addRowWidget(countField));

            upButtons.add(addRowWidget(Button.builder(
                            Component.literal("↑"),
                            button -> moveIndex(entryIndex, -1))
                    .bounds(upX, y, MOVE_BUTTON_W, 20)
                    .build()));
            downButtons.add(addRowWidget(Button.builder(
                            Component.literal("↓"),
                            button -> moveIndex(entryIndex, 1))
                    .bounds(downX, y, MOVE_BUTTON_W, 20)
                    .build()));
            deleteButtons.add(addRowWidget(Button.builder(
                            Component.translatable("gui.kineticcore.firstjoin.reward_items.delete"),
                            button -> deleteIndex(entryIndex))
                    .bounds(deleteX, y, DELETE_BUTTON_W, 20)
                    .build()));
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.firstjoin.reward_items.add"),
                        button -> addEntry())
                .bounds(44, 314, 110, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.firstjoin.reward_items.back"),
                        button -> requestClose())
                .bounds(265, 314, 110, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.firstjoin.reward_items.save"),
                        button -> saveAndClose())
                .bounds(472, 314, 110, 20)
                .build());
        updateRowButtons();
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addRowWidget(T widget) {
        return addScrollableWidget(
                widget,
                LIST_X,
                LIST_Y,
                LIST_X + LIST_W,
                LIST_Y + LIST_H,
                () -> scroll.smoothOffset() * ROW_H
        );
    }

    private void updateScrollRange() {
        scroll.update(entries.size(), VISIBLE_ROWS);
    }

    private void updateRowButtons() {
        updatingCountFields = true;
        try {
            int widgetCount = Math.min(entries.size(), countFields.size());
            for (int index = 0; index < countFields.size(); index++) {
                boolean visible = index < widgetCount;
                boolean stackable = visible && entries.get(index).stack().getMaxStackSize() > 1;

                EditBox countField = countFields.get(index);
                countField.setVisible(stackable);
                countField.setEditable(stackable);
                if (stackable) {
                    String value = String.valueOf(entries.get(index).stack().getCount());
                    if (!countField.isFocused() && !value.equals(countField.getValue())) {
                        countField.setValue(value);
                    }
                } else {
                    countField.setFocused(false);
                    if (!countField.getValue().isEmpty()) {
                        countField.setValue("");
                    }
                }

                upButtons.get(index).visible = visible;
                downButtons.get(index).visible = visible;
                deleteButtons.get(index).visible = visible;
                if (visible) {
                    upButtons.get(index).active = index > 0;
                    downButtons.get(index).active = index < entries.size() - 1;
                    deleteButtons.get(index).active = true;
                }
            }
        } finally {
            updatingCountFields = false;
        }
    }

    private void applyCount(int index, String value) {
        if (updatingCountFields || value == null || value.isBlank()) return;
        if (index < 0 || index >= entries.size()) return;
        ItemStack stack = entries.get(index).stack();
        if (stack.getMaxStackSize() <= 1) return;
        try {
            stack.setCount(Math.max(1, Math.min(999, Integer.parseInt(value))));
        } catch (NumberFormatException ignored) {
        }
    }

    private void moveIndex(int index, int direction) {
        clearCountFieldFocus();
        int target = index + direction;
        if (index < 0 || index >= entries.size() || target < 0 || target >= entries.size()) return;
        RewardEntry entry = entries.remove(index);
        entries.add(target, entry);
        if (target < scroll.smoothOffset()) scroll.setOffset(target);
        if (target >= scroll.smoothOffset() + VISIBLE_ROWS) scroll.setOffset(target - VISIBLE_ROWS + 1);
        updateRowButtons();
    }

    private void deleteIndex(int index) {
        clearCountFieldFocus();
        if (index < 0 || index >= entries.size()) return;
        entries.remove(index);
        updateScrollRange();
        updateRowButtons();
    }

    private void clearCountFieldFocus() {
        for (EditBox countField : countFields) {
            countField.setFocused(false);
        }
    }

    private void addEntry() {
        clearCountFieldFocus();
        ItemSearchIndex.prepareCache(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ItemSelectorScreen(this, selection -> {
                if (selection == null || !selection.isItem()) return;
                entries.add(new RewardEntry(firstFreeInventorySlot(), selection.stack().copy()));
                updateScrollRange();
                int last = entries.size() - 1;
                if (last >= scroll.smoothOffset() + VISIBLE_ROWS) {
                    scroll.setOffset(last - VISIBLE_ROWS + 1);
                }
                updateRowButtons();
            }));
        });
    }

    private int firstFreeInventorySlot() {
        for (int slot = 0; slot < 36; slot++) {
            boolean used = false;
            for (RewardEntry entry : entries) {
                if (entry.slot() == slot) {
                    used = true;
                    break;
                }
            }
            if (!used) return slot;
        }
        return entries.size();
    }

    private void openItemSelector(int index) {
        clearCountFieldFocus();
        if (index < 0 || index >= entries.size()) return;
        ItemSearchIndex.prepareCache(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ItemSelectorScreen(this, selection -> {
                if (selection == null || !selection.isItem() || index >= entries.size()) return;
                ItemStack selected = selection.stack().copy();
                int oldCount = entries.get(index).stack().getCount();
                if (selected.getMaxStackSize() > 1) {
                    selected.setCount(Math.max(1, Math.min(999, oldCount)));
                } else {
                    selected.setCount(1);
                }
                entries.set(index, new RewardEntry(entries.get(index).slot(), selected));
                updateRowButtons();
            }));
        });
    }

    private void openNbtEditor(int index) {
        clearCountFieldFocus();
        if (index < 0 || index >= entries.size()) return;
        ItemStack stack = entries.get(index).stack();
        if (stack.isEmpty()) return;
        String initialNbt = stack.hasTag() && stack.getTag() != null ? stack.getTag().toString() : "";
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

    private void saveAndClose() {
        clearCountFieldFocus();
        List<String> saved = new ArrayList<>();
        for (RewardEntry entry : entries) {
            if (!entry.stack().isEmpty()) {
                saved.add("[" + entry.slot() + "] " + PlayerConfig.serializeItemStack(entry.stack()));
            }
        }
        if (!KTServerConfigClient.savePartial(PlayerConfigGui.PAGE_ID, Map.of("items", saved))) {
            GuiOverlay.toast(Component.translatable("gui.kineticcore.config.server.save_failed"));
            return;
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private void requestClose() {
        clearCountFieldFocus();
        Minecraft client = Minecraft.getInstance();
        if (!hasUnsavedChanges()) {
            client.setScreen(parent);
            return;
        }

        client.setScreen(new ConfirmScreen(
                shouldSave -> {
                    if (!shouldSave) {
                        client.setScreen(parent);
                        return;
                    }
                    client.setScreen(this);
                    saveAndClose();
                },
                Component.translatable("gui.kineticcore.config.unsaved_action.title"),
                Component.translatable("gui.kineticcore.firstjoin.reward_items.unsaved")
        ));
    }

    private boolean hasUnsavedChanges() {
        if (entries.size() != savedEntries.size()) return true;
        for (int i = 0; i < entries.size(); i++) {
            RewardEntry current = entries.get(i);
            RewardEntry saved = savedEntries.get(i);
            if (current.slot() != saved.slot()) return true;
            if (!sameStack(current.stack(), saved.stack())) return true;
        }
        return false;
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        CompoundTag leftTag = new CompoundTag();
        CompoundTag rightTag = new CompoundTag();
        left.save(leftTag);
        right.save(rightTag);
        return leftTag.equals(rightTag);
    }

    private static RewardEntry copyEntry(RewardEntry entry) {
        return new RewardEntry(entry.slot(), entry.stack().copy());
    }

    private boolean inList(double mouseX, double mouseY) {
        return mouseX >= LIST_X && mouseX < LIST_X + LIST_W
                && mouseY >= LIST_Y && mouseY < LIST_Y + LIST_H;
    }

    private int rowIndex(double mouseY) {
        if (mouseY < LIST_Y || mouseY >= LIST_Y + LIST_H) return -1;
        double contentY = mouseY - LIST_Y + scroll.smoothOffset() * ROW_H;
        int index = (int) Math.floor(contentY / ROW_H);
        return index >= 0 && index < entries.size() ? index : -1;
    }

    private boolean overItem(double mouseX, double mouseY, int index) {
        if (index < 0 || index >= entries.size() || !inList(mouseX, mouseY)) return false;
        int y = LIST_Y + (int) Math.round((index - scroll.smoothOffset()) * ROW_H) + ITEM_Y_OFFSET;
        return GuiTheme.hovering(mouseX, mouseY, ITEM_X, y, SLOT_SIZE, SLOT_SIZE);
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int hoveredIndex = rowIndex(mouseY);
        updateRowButtons();
        graphics.fillGradient(0, 0, canvasWidth, canvasHeight, 0xFF171717, 0xFF0E0E0E);
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        GuiTheme.panelAlt(graphics, LIST_X - 4, LIST_Y - 4, LIST_W + 8, LIST_H + 8);
        graphics.drawCenteredString(font, title, canvasWidth / 2, 30, 0xFFFFFF);

        scroll.update(entries.size(), VISIBLE_ROWS);
        double smoothOffset = scroll.smoothOffset();
        int first = Math.max(0, (int) Math.floor(smoothOffset));
        int end = Math.min(entries.size(), first + VISIBLE_ROWS + 2);
        enableCanvasScissor(graphics, LIST_X, LIST_Y, LIST_X + LIST_W, LIST_Y + LIST_H);
        try {
            for (int index = first; index < end; index++) {
                int y = LIST_Y + (int) Math.round((index - smoothOffset) * ROW_H);
                boolean rowHovered = index == hoveredIndex;
                graphics.fill(LIST_X, y, LIST_X + LIST_W, y + ROW_H - 2, index % 2 == 0 ? 0xCC181818 : 0xCC111111);
                graphics.renderOutline(LIST_X, y, LIST_W, ROW_H - 2, rowHovered ? GuiTheme.current().accentHover() : 0xFF555555);

                RewardEntry entry = entries.get(index);
                ItemStack stack = entry.stack();
                int itemY = y + ITEM_Y_OFFSET;
                boolean itemHovered = overItem(mouseX, mouseY, index);
                if (!stack.isEmpty() && stack.getMaxStackSize() > 1) {
                    graphics.drawString(
                            font,
                            Component.translatable("gui.kineticcore.firstjoin.reward_items.count"),
                            COUNT_LABEL_X,
                            y + 12,
                            0xFFFFFFFF,
                            false
                    );
                }

                GuiTheme.itemSlot(graphics, ITEM_X, itemY, SLOT_SIZE, 4, itemHovered);
                GuiTheme.item(graphics, font, stack, ITEM_X, itemY, SLOT_SIZE, 1.0F, false);

                graphics.drawString(
                        font,
                        GuiTheme.trim(font, stack.getHoverName().getString(), 220),
                        ITEM_X + SLOT_SIZE + 8,
                        y + 11,
                        0xFFFFFFFF,
                        false
                );
            }
        } finally {
            graphics.disableScissor();
        }

        GuiTheme.scrollbar(scroll, graphics, mouseX, mouseY, SCROLL_X, LIST_Y, SCROLL_W, LIST_H, 18);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.kineticcore.firstjoin.reward_items.empty"),
                    LIST_X + LIST_W / 2,
                    LIST_Y + LIST_H / 2,
                    0xFFFFFFFF
            );
        }
        graphics.drawCenteredString(
                font,
                Component.translatable("gui.kineticcore.firstjoin.reward_items.hint"),
                canvasWidth / 2,
                292,
                0xFFFFFFFF
        );
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        int index = rowIndex(scaledMouseY);
        if (!overItem(scaledMouseX, scaledMouseY, index)) return;
        ItemStack stack = entries.get(index).stack();
        if (stack.isEmpty()) return;
        showItemTooltip(stack);
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (super.canvasMouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && scroll.beginDrag(mouseX, mouseY, SCROLL_X, LIST_Y, SCROLL_W, LIST_H, 18, 2)) return true;
        if (inList(mouseX, mouseY)) {
            int index = rowIndex(mouseY);
            if (overItem(mouseX, mouseY, index)) {
                if (button == 0) {
                    openItemSelector(index);
                    return true;
                }
                if (button == 1) {
                    openNbtEditor(index);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scroll.drag(mouseY, LIST_Y, LIST_H, 18)
                || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button) || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (inList(mouseX, mouseY) && scroll.scroll(delta)) {
            clearCountFieldFocus();
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        requestClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RewardEntry(int slot, ItemStack stack) {
    }
}
