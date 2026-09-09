package dev.xyat.kineticcore.api.client.selector;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class ItemListEditorScreen extends KineticScreen {
    public enum SelectionMode {
        ITEMS_ONLY,
        ITEMS_TAGS_MODS
    }

    private static final int PANEL_X = 42;
    private static final int PANEL_Y = 18;
    private static final int PANEL_WIDTH = 556;
    private static final int PANEL_HEIGHT = 330;
    private static final int GRID_X = 69;
    private static final int GRID_Y = 60;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 1;
    private static final int CELL_SIZE = SLOT_SIZE + SLOT_GAP;
    private static final int COLUMNS = 27;
    private static final int ROWS_VISIBLE = 13;
    private static final int GRID_WIDTH = COLUMNS * CELL_SIZE - SLOT_GAP;
    private static final int GRID_HEIGHT = ROWS_VISIBLE * CELL_SIZE - SLOT_GAP;
    private static final int SCROLL_X = GRID_X + GRID_WIDTH + 6;

    private final Screen parent;
    private final SelectionMode selectionMode;
    private final Consumer<List<String>> onSave;
    private final List<String> rules = new ArrayList<>();
    private final Map<String, ItemStack> previewCache = new HashMap<>();
    private final GridScrollController scroll = new GridScrollController();

    private int hoveredIndex = -1;

    public ItemListEditorScreen(
            Screen parent,
            Component title,
            List<String> initialRules,
            SelectionMode selectionMode,
            Consumer<List<String>> onSave
    ) {
        super(Objects.requireNonNull(title, "title"));
        this.parent = parent;
        this.selectionMode = Objects.requireNonNull(selectionMode, "selectionMode");
        this.onSave = Objects.requireNonNull(onSave, "onSave");
        if (initialRules != null) {
            Set<String> unique = new LinkedHashSet<>();
            for (String rule : initialRules) {
                String normalized = normalizeRule(rule);
                if (!normalized.isEmpty()) unique.add(normalized);
            }
            rules.addAll(unique);
        }
        useCanvas(640, 360, 6);
        maxScale = 1.0F;
    }

    @Override
    protected void buildUi() {
        updateScrollRange();

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.items.list_editor.add"),
                        ignored -> openSelector())
                .bounds(158, 316, 96, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.config.back"),
                        ignored -> onClose())
                .bounds(272, 316, 96, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.hud_editor.save"),
                        ignored -> saveAndClose())
                .bounds(386, 316, 96, 20)
                .build());
    }

    private void openSelector() {
        ItemSearchIndex.prepareCache(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ItemSelectorScreen(this, this::acceptSelection));
        });
    }

    private void acceptSelection(ItemSelectorScreen.Selection selection) {
        if (selection == null) return;

        String rule;
        if (selection.isItem()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(selection.stack().getItem());
            if (id == null) return;
            rule = id.toString();
        } else if (selection.isTag()) {
            if (selectionMode == SelectionMode.ITEMS_ONLY) {
                showItemOnlyToast();
                return;
            }
            rule = "#" + selection.value();
        } else if (selection.isMod()) {
            if (selectionMode == SelectionMode.ITEMS_ONLY) {
                showItemOnlyToast();
                return;
            }
            rule = "@" + selection.value();
        } else {
            return;
        }

        String normalized = normalizeRule(rule);
        if (!normalized.isEmpty() && !rules.contains(normalized)) {
            rules.add(normalized);
            updateScrollRange();
        }
    }

    private void showItemOnlyToast() {
        GuiOverlay.toast(
                "kineticcore_item_list_item_only",
                Component.translatable("gui.kineticcore.items.list_editor.item_only")
        );
    }

    private void saveAndClose() {
        onSave.accept(List.copyOf(rules));
        Minecraft.getInstance().setScreen(parent);
    }

    private void updateScrollRange() {
        scroll.update(totalRows(), ROWS_VISIBLE);
    }

    private int totalRows() {
        return (rules.size() + COLUMNS - 1) / COLUMNS;
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.drawCenteredString(font, title, canvasWidth / 2, 30, 0xFFFFAA00);
        GuiTheme.itemGrid(graphics, GRID_X, GRID_Y, GRID_WIDTH, GRID_HEIGHT);
        renderRules(graphics, mouseX, mouseY);
        GuiTheme.scrollbar(
                scroll,
                graphics,
                mouseX,
                mouseY,
                SCROLL_X,
                GRID_Y,
                4,
                GRID_HEIGHT,
                18
        );
    }

    @Override
    protected void renderCanvasForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (rules.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.kineticcore.items.list_editor.empty"),
                    GRID_X + GRID_WIDTH / 2,
                    GRID_Y + GRID_HEIGHT / 2 - font.lineHeight / 2,
                    0xFFAAAAAA
            );
        }
    }

    private void renderRules(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredIndex = indexAt(mouseX, mouseY);
        int smoothRow = scroll.smoothIndexOffset();
        int scrollShift = scroll.visualShift(CELL_SIZE);
        int first = smoothRow * COLUMNS;
        int last = Math.min(
                rules.size(),
                first + (ROWS_VISIBLE + 1) * COLUMNS
        );

        enableCanvasScissor(
                graphics,
                GRID_X,
                GRID_Y,
                GRID_X + GRID_WIDTH,
                GRID_Y + GRID_HEIGHT
        );
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int column = visible % COLUMNS;
            int row = visible / COLUMNS;
            int x = GRID_X + column * CELL_SIZE;
            int y = GRID_Y + row * CELL_SIZE - scrollShift;
            boolean hovered = index == hoveredIndex;

            GuiTheme.itemSlot(graphics, x, y, SLOT_SIZE, hovered);
            ItemStack stack = previewStack(rules.get(index));
            if (!stack.isEmpty()) {
                GuiTheme.item(
                        graphics,
                        font,
                        stack,
                        x,
                        y,
                        SLOT_SIZE,
                        1.0F,
                        true
                );
            }

            String rule = rules.get(index);
            if (rule.startsWith("#") || rule.startsWith("@")) {
                graphics.drawString(font, rule.substring(0, 1), x + 2, y + 2, 0xFFFFFFFF, true);
            }
        }
        graphics.disableScissor();
    }

    private ItemStack previewStack(String rule) {
        if (rule == null || rule.isBlank()) return ItemStack.EMPTY;
        return previewCache.computeIfAbsent(rule, this::buildPreviewStack);
    }

    private ItemStack buildPreviewStack(String rule) {
        if (rule.startsWith("@")) {
            String namespace = rule.substring(1);
            return ForgeRegistries.ITEMS.getEntries().stream()
                    .filter(entry -> entry.getKey().location().getNamespace().equals(namespace))
                    .map(entry -> new ItemStack(entry.getValue()))
                    .filter(stack -> !stack.isEmpty())
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
        }
        if (rule.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(rule.substring(1));
            if (id == null) return ItemStack.EMPTY;
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            return ForgeRegistries.ITEMS.getValues().stream()
                    .map(ItemStack::new)
                    .filter(stack -> stack.is(tag))
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
        }

        ResourceLocation id = ResourceLocation.tryParse(rule);
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!GuiTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_WIDTH, GRID_HEIGHT)) {
            return -1;
        }
        int localX = (int) (mouseX - GRID_X);
        int localY = (int) Math.floor(
                mouseY - GRID_Y + scroll.visualShift(CELL_SIZE)
        );
        int column = localX / CELL_SIZE;
        int row = localY / CELL_SIZE;
        if (column >= COLUMNS || row >= ROWS_VISIBLE) return -1;
        if (localX % CELL_SIZE >= SLOT_SIZE || localY % CELL_SIZE >= SLOT_SIZE) return -1;
        int index = (scroll.smoothIndexOffset() + row) * COLUMNS + column;
        return index >= 0 && index < rules.size() ? index : -1;
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        boolean widget = super.canvasMouseClicked(mouseX, mouseY, button);

        if (button == 0 && scroll.beginDrag(
                mouseX,
                mouseY,
                SCROLL_X,
                GRID_Y,
                6,
                GRID_HEIGHT,
                18,
                2
        )) {
            return true;
        }

        if (button == 1) {
            int index = indexAt(mouseX, mouseY);
            if (index >= 0) {
                rules.remove(index);
                updateScrollRange();
                return true;
            }
        }
        return widget;
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        return scroll.drag(mouseY, GRID_Y, GRID_HEIGHT, 18)
                || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button)
                || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (GuiTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_WIDTH + 12, GRID_HEIGHT)
                && scroll.scroll(delta)) {
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
    ) {
        if (hoveredIndex < 0 || hoveredIndex >= rules.size()) return;
        String rule = rules.get(hoveredIndex);
        List<Component> lines = new ArrayList<>();
        ItemStack stack = previewStack(rule);
        if (!stack.isEmpty()) {
            lines.add(stack.getHoverName());
        }
        lines.add(Component.literal(rule));
        lines.add(Component.translatable("gui.kineticcore.items.list_editor.remove_hint"));
        showTooltip(lines, 300);
    }

    private static String normalizeRule(String rule) {
        return rule == null ? "" : rule.trim();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
