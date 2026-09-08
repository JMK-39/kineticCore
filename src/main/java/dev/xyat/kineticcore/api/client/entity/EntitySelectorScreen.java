package dev.xyat.kineticcore.api.client.entity;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.AdvancedSearchUtil;
import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.PinyinUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.gui.ConfigScrollbarTheme;
import dev.xyat.kineticcore.api.client.gui.GridScrollController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reusable visual multi-selector for exact entity IDs. It edits a local draft
 * only: applying invokes the callback, while persistence remains the caller's
 * responsibility.
 */
public final class EntitySelectorScreen extends ScaledScreen {
    private static final int COLS = 8;
    private static final int CELL_W = 68;
    private static final int CELL_H = 68;
    private static final int GRID_X = 42;
    private static final int GRID_Y = 67;
    private static final int VISIBLE_ROWS = 3;
    private static final int GRID_W = COLS * CELL_W;
    private static final int GRID_H = VISIBLE_ROWS * CELL_H;
    private static final int SCROLL_X = GRID_X + GRID_W + 6;
    private static final int SCROLL_W = 6;

    private final Screen parent;
    private final Consumer<List<String>> onApply;
    private final List<String> allEntityIds = new ArrayList<>();
    private final List<String> filteredEntityIds = new ArrayList<>();
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final Set<String> originalIds = new LinkedHashSet<>();
    private final Map<String, String> searchData = new HashMap<>();
    private final GridScrollController scroll = new GridScrollController();
    private final EntityPreviewRenderer previewRenderer = new EntityPreviewRenderer();

    private EditBox searchBox;
    private String searchQuery = "";
    private List<Component> deferredTooltip;

    public EntitySelectorScreen(
            Screen parent,
            Component title,
            Collection<String> initialEntityIds,
            Consumer<List<String>> onApply
    ) {
        super(title);
        this.parent = parent;
        this.onApply = onApply;
        if (initialEntityIds != null) {
            for (String value : initialEntityIds) {
                if (value != null && !value.isBlank()) selectedIds.add(value.trim());
            }
        }
        originalIds.addAll(selectedIds);

        ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .map(ResourceLocation::toString)
                .sorted(String::compareToIgnoreCase)
                .forEach(allEntityIds::add);
        for (String selected : selectedIds) {
            if (!allEntityIds.contains(selected)) allEntityIds.add(selected);
        }
        allEntityIds.sort(String::compareToIgnoreCase);
        buildSearchData();
        configureResponsiveCanvas(640, 360, 6);
    }

    @Override
    protected void initScaled() {
        searchBox = addRenderableWidget(new EditBox(
                font, GRID_X, 38, Math.min(GRID_W, 430), 20,
                Component.translatable("gui.kineticcore.entity_selector.search_hint")
        ));
        searchBox.setMaxLength(256);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(query -> {
            searchQuery = query == null ? "" : query;
            updateSearch(searchQuery);
        });

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.entity_selector.clear"),
                        ignored -> selectedIds.clear())
                .bounds(166, 325, 92, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.config.back"),
                        ignored -> onClose())
                .bounds(274, 325, 92, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.entity_selector.apply"),
                        ignored -> applyAndReturn())
                .bounds(382, 325, 92, 20).build());

        updateSearch(searchQuery);
    }

    private void buildSearchData() {
        searchData.clear();
        for (String id : allEntityIds) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            EntityType<?> type = location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
            String name = type == null ? id : type.getDescription().getString();
            String raw = id + " " + name;
            searchData.put(id, (raw + " " + PinyinUtil.getSearchData(raw)).toLowerCase(Locale.ROOT));
        }
    }

    private void updateSearch(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredEntityIds.clear();
        for (String id : allEntityIds) {
            if (normalized.isEmpty() || AdvancedSearchUtil.match(
                    searchData.getOrDefault(id, id.toLowerCase(Locale.ROOT)), normalized)) {
                filteredEntityIds.add(id);
            }
        }
        filteredEntityIds.sort((left, right) -> {
            int selectedCompare = Boolean.compare(selectedIds.contains(right), selectedIds.contains(left));
            return selectedCompare != 0 ? selectedCompare : left.compareToIgnoreCase(right);
        });
        scroll.reset();
        updateScrollRange();
    }

    private void updateScrollRange() {
        int totalRows = (filteredEntityIds.size() + COLS - 1) / COLS;
        scroll.updateRange(Math.max(0, totalRows - VISIBLE_ROWS), totalRows, VISIBLE_ROWS);
    }

    @Override
    protected void renderScaledBackground(
            @NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        deferredTooltip = null;
        GuiRenderUtil.drawStandardPanel(graphics, 20, 12, 600, 342);
        graphics.drawCenteredString(font, title, vWidth / 2, 22, 0xFFFFAA00);
        GuiRenderUtil.drawDarkPanel(graphics, GRID_X - 3, GRID_Y - 3, GRID_W + 6, GRID_H + 6);
        renderGrid(graphics, mouseX, mouseY);
        ConfigScrollbarTheme.render(
                scroll, graphics, mouseX, mouseY,
                SCROLL_X, GRID_Y, SCROLL_W, GRID_H, 18
        );
        graphics.drawCenteredString(
                font,
                Component.translatable("gui.kineticcore.entity_selector.selected", Component.literal(String.valueOf(selectedIds.size())).withStyle(ChatFormatting.GREEN)),
                vWidth / 2,
                306,
                0xFFAAAAAA
        );
        if (filteredEntityIds.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.kineticcore.entity_selector.empty"),
                    GRID_X + GRID_W / 2,
                    GRID_Y + GRID_H / 2,
                    0xFFAAAAAA
            );
        }
    }

    @Override
    protected void renderScaledForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderSearchPlaceholder(
                graphics,
                searchBox,
                "gui.kineticcore.entity_selector.search_hint"
        );
    }

    private void renderSearchPlaceholder(
            GuiGraphics graphics,
            EditBox box,
            String translationKey
    ) {
        if (box == null
                || !box.visible
                || !box.getValue().isEmpty()
                || box.isFocused()) {
            return;
        }

        String text = font.plainSubstrByWidth(
                Component.translatable(translationKey).getString(),
                Math.max(0, box.getWidth() - 10)
        );
        graphics.drawString(
                font,
                text,
                box.getX() + 5,
                box.getY() + (box.getHeight() - font.lineHeight) / 2,
                0xFFAAAAAA,
                false
        );
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int first = scroll.offset() * COLS;
        int last = Math.min(first + VISIBLE_ROWS * COLS, filteredEntityIds.size());
        for (int index = first; index < last; index++) {
            int local = index - first;
            int x = GRID_X + local % COLS * CELL_W;
            int y = GRID_Y + local / COLS * CELL_H;
            String id = filteredEntityIds.get(index);
            boolean selected = selectedIds.contains(id);
            boolean hovered = mouseX >= x && mouseX < x + CELL_W
                    && mouseY >= y && mouseY < y + CELL_H;

            EntityPreviewRenderer.drawCheckerboard(graphics, x + 2, y + 2, CELL_W - 4, CELL_H - 16);
            graphics.renderOutline(x, y, CELL_W, CELL_H,
                    selected ? 0xFF55FF55 : hovered ? 0xFFFFAA00 : 0xFF555555);
            if (selected) graphics.renderOutline(x + 1, y + 1, CELL_W - 2, CELL_H - 2, 0xFF55FF55);

            boolean rendered = previewRenderer.render(
                    graphics, id, "selector:" + id,
                    x + 3, y + 3, CELL_W - 6, CELL_H - 19,
                    guiScale, offsetX, offsetY, hovered
            );
            if (!rendered) {
                graphics.drawCenteredString(font, "?", x + CELL_W / 2, y + 23, 0xFF777777);
            }
            graphics.drawCenteredString(
                    font,
                    GuiRenderUtil.trimText(font, entityName(id), CELL_W - 6),
                    x + CELL_W / 2,
                    y + CELL_H - 12,
                    selected ? 0xFF55FF55 : 0xFFE0E0E0
            );
            if (hovered) {
                deferredTooltip = List.of(
                        Component.literal(entityName(id)),
                        Component.literal(id),
                        Component.translatable(selected
                                ? "gui.kineticcore.entity_selector.remove_hint"
                                : "gui.kineticcore.entity_selector.add_hint")
                );
            }
        }
    }

    private String entityName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        EntityType<?> type = location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
        return type == null ? id : type.getDescription().getString();
    }

    private boolean inGrid(double mouseX, double mouseY) {
        return mouseX >= GRID_X && mouseX < GRID_X + GRID_W
                && mouseY >= GRID_Y && mouseY < GRID_Y + GRID_H;
    }

    private int entityIndex(double mouseX, double mouseY) {
        int column = (int) ((mouseX - GRID_X) / CELL_W);
        int row = (int) ((mouseY - GRID_Y) / CELL_H);
        return scroll.offset() * COLS + row * COLS + column;
    }

    @Override
    protected boolean universalMouseClicked(double mouseX, double mouseY, int button) {
        boolean widgetHandled = super.universalMouseClicked(mouseX, mouseY, button);
        if (button == 0 && scroll.beginDrag(
                mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_W, GRID_H, 18, 2)) return true;
        if (button == 0 && inGrid(mouseX, mouseY)) {
            int index = entityIndex(mouseX, mouseY);
            if (index >= 0 && index < filteredEntityIds.size()) {
                String id = filteredEntityIds.get(index);
                if (!selectedIds.add(id)) selectedIds.remove(id);
                return true;
            }
        }
        return widgetHandled;
    }

    @Override
    protected boolean universalMouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scroll.drag(mouseY, GRID_Y, GRID_H, 18)
                || super.universalMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean universalMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button)
                || super.universalMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean universalMouseScrolled(double mouseX, double mouseY, double delta) {
        if (Screen.hasControlDown() && inGrid(mouseX, mouseY)) {
            int index = entityIndex(mouseX, mouseY);
            if (index >= 0 && index < filteredEntityIds.size()) {
                String id = filteredEntityIds.get(index);
                previewRenderer.adjustZoom("selector:" + id, delta);
                return true;
            }
        }
        if (inGrid(mouseX, mouseY) && scroll.scroll(delta)) return true;
        return super.universalMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        if (deferredTooltip != null) {
            graphics.renderComponentTooltip(font, deferredTooltip, mouseX, mouseY);
        }
    }

    private void applyAndReturn() {
        onApply.accept(new ArrayList<>(selectedIds));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        Minecraft client = Minecraft.getInstance();
        if (selectedIds.equals(originalIds)) {
            client.setScreen(parent);
            return;
        }
        client.setScreen(new ConfirmScreen(
                shouldApply -> {
                    if (shouldApply) {
                        applyAndReturn();
                    } else {
                        client.setScreen(parent);
                    }
                },
                Component.translatable("gui.kineticcore.config.unsaved_action.title"),
                Component.translatable("gui.kineticcore.entity_selector.unsaved")
        ));
    }

    @Override
    public void removed() {
        previewRenderer.clear();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
