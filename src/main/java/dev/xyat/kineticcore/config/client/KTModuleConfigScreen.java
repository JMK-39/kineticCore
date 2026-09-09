package dev.xyat.kineticcore.config.client;

import dev.xyat.kineticcore.KineticCore;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.selector.ItemListEditorScreen;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.selector.ColorPickerScreen;
import dev.xyat.kineticcore.api.client.selector.EntitySelectorScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.ColorPreviewButton;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.NumericEditBox;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class KTModuleConfigScreen extends KineticScreen {
    private enum SaveOutcome {
        FAILED,
        UNCHANGED,
        SAVED
    }

    private enum RowKind {
        SCOPE,
        PAGE,
        ENTRY
    }

    private record Row(RowKind kind, KTConfigScope scope, KTConfigPage page, KTConfigEntry<?> entry, Component text) {
        static Row scope(KTConfigScope scope) {
            return new Row(
                    RowKind.SCOPE,
                    scope,
                    null,
                    null,
                    Component.translatable(scopeHeaderKey(scope))
            );
        }

        static Row page(KTConfigPage page) {
            return new Row(RowKind.PAGE, page.scope(), page, null, page.title());
        }

        static Row entry(KTConfigPage page, KTConfigEntry<?> entry) {
            return new Row(RowKind.ENTRY, page.scope(), page, entry, entry.label());
        }
    }

    private static final int VISIBLE_ROWS = 9;
    private static final int ROW_TOP = 64;
    private static final int ROW_HEIGHT = 27;
    private static final int LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;
    private static final int SCROLL_X = 615;
    private static final int SCROLL_WIDTH = 4;

    private final Screen parent;
    private final List<KTConfigPage> pages;
    private final Map<String, Object> pendingValues = new HashMap<>();
    private final Map<String, Object> originalValues = new HashMap<>();
    private final Map<String, String> rawTextValues = new HashMap<>();
    private final Set<String> invalidEntries = new HashSet<>();
    private final Map<Row, Integer> visibleRows = new LinkedHashMap<>();
    private final GridScrollController rowScroll = new GridScrollController();

    private List<Row> rows = List.of();
    private Component status;
    private EditBox searchBox;
    private Row hoveredRow;
    private String searchQuery = "";
    private boolean searchDirty;

    public KTModuleConfigScreen(
            Screen parent,
            Component moduleTitle,
            List<KTConfigPage> pages
    ) {
        super(Objects.requireNonNull(moduleTitle, "moduleTitle"));
        this.parent = parent;
        this.pages = pages.stream()
                .sorted(Comparator.comparingInt((KTConfigPage page) -> scopeOrder(page.scope()))
                        .thenComparing(KTConfigPage::id))
                .toList();
        useCanvas(640, 360, 6);
        refreshFromSource();
        rebuildRows();
        for (KTConfigPage page : this.pages) {
            KTServerConfigClient.request(page);
        }
    }

    public void refreshFromSource() {
        pendingValues.clear();
        originalValues.clear();
        rawTextValues.clear();
        invalidEntries.clear();
        status = null;

        for (KTConfigPage page : pages) {
            for (KTConfigEntry<?> entry : page.entries()) {
                if (!entry.isValue()) continue;
                String key = entryKey(page, entry);
                Object value;
                try {
                    value = entry.readSnapshot();
                } catch (Throwable throwable) {
                    KineticCore.LOGGER.error(
                            "Failed to read config value {} from page {}",
                            entry.id(),
                            page.id(),
                            throwable
                    );
                    value = entry.defaultSnapshot();
                }
                if (!entry.accepts(value)) {
                    KineticCore.LOGGER.warn(
                            "Invalid config value {} on page {}; using its default",
                            entry.id(),
                            page.id()
                    );
                    value = entry.defaultSnapshot();
                }
                Object snapshot = entry.snapshot(value);
                pendingValues.put(key, snapshot);
                originalValues.put(key, entry.snapshot(snapshot));
            }
        }
    }

    @Override
    protected void buildUi() {
        resetScrollableWidgets();
        visibleRows.clear();
        rebuildRows();
        rowScroll.update(rows.size(), VISIBLE_ROWS);

        searchBox = new EditBox(
                font,
                38,
                37,
                430,
                18,
                Component.translatable("gui.kineticcore.config.search_fields")
        );
        searchBox.setMaxLength(256);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(query -> {
            searchQuery = query == null ? "" : query;
            rowScroll.reset();
            searchDirty = true;
        });
        addRenderableWidget(searchBox);

        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            int y = ROW_TOP + index * ROW_HEIGHT;
            visibleRows.put(row, y);
            if (row.kind() != RowKind.ENTRY || row.entry() == null) continue;
            if (row.entry().isValue()) {
                addValueWidgets(row.page(), row.entry(), y);
            } else if (row.entry().type() == KTConfigEntry.Type.ACTION) {
                addActionWidget(row.page(), row.entry(), y);
            }
        }

        int footerY = 325;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.config.reset_all"),
                        ignored -> resetAll())
                .bounds(166, footerY, 92, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.config.back"),
                        ignored -> onClose())
                .bounds(274, footerY, 92, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.config.save"),
                        ignored -> saveAndClose())
                .bounds(382, footerY, 92, 20)
                .build());
    }

    private double rowPixelOffset() {
        return rowScroll.smoothOffset() * ROW_HEIGHT;
    }

    private <T extends AbstractWidget> T addRowScrollableWidget(T widget) {
        return addScrollableWidget(
                widget,
                28,
                ROW_TOP,
                SCROLL_X - 2,
                ROW_TOP + LIST_HEIGHT,
                this::rowPixelOffset
        );
    }

    private static int compactEditorWidth(KTConfigEntry.Type type) {
        return switch (type) {
            case BOOLEAN -> 88;
            case INTEGER, LONG, DOUBLE -> 104;
            case STRING -> 176;
            case CHOICE -> 132;
            case STRING_LIST, ITEM_LIST, ITEM_RULE_LIST, ENTITY_LIST, INTEGER_LIST -> 120;
            case COLOR -> 104;
            default -> 132;
        };
    }

    private void addValueWidgets(KTConfigPage page, KTConfigEntry<?> entry, int y) {
        final int resetX = 554;
        final int resetWidth = 58;
        final int editorWidth = compactEditorWidth(entry.type());
        final int editorX = resetX - 8 - editorWidth;
        String key = entryKey(page, entry);
        AbstractWidget editor;

        switch (entry.type()) {
            case BOOLEAN -> {
                boolean value = Boolean.TRUE.equals(pendingValues.get(key));
                editor = Button.builder(booleanText(value), button -> {
                    boolean next = !Boolean.TRUE.equals(pendingValues.get(key));
                    pendingValues.put(key, next);
                    button.setMessage(booleanText(next));
                }).bounds(editorX, y, editorWidth, 20).build();
            }
            case INTEGER -> {
                int min = entry.minimum().intValue();
                int max = entry.maximum().intValue();
                NumericEditBox box = NumericEditBox.integer(
                        font,
                        editorX,
                        y,
                        editorWidth,
                        20,
                        entry.label(),
                        min < 0,
                        min,
                        max
                );
                box.setValue(rawTextValues.getOrDefault(
                        key,
                        Integer.toString(((Number) pendingValues.get(key)).intValue())
                ));
                box.setResponder(raw -> setParsedValue(key, box.getIntValue(), box));
                setParsedValue(key, box.getIntValue(), box);
                editor = box;
            }
            case LONG -> {
                long min = entry.minimum().longValue();
                long max = entry.maximum().longValue();
                NumericEditBox box = NumericEditBox.longInteger(
                        font,
                        editorX,
                        y,
                        editorWidth,
                        20,
                        entry.label(),
                        min < 0,
                        null,
                        null
                );
                box.setValue(rawTextValues.getOrDefault(
                        key,
                        Long.toString(((Number) pendingValues.get(key)).longValue())
                ));
                box.setResponder(raw -> {
                    Long parsed = box.getLongValue();
                    setParsedValue(key, parsed != null && entry.accepts(parsed) ? parsed : null, box);
                });
                Long parsed = box.getLongValue();
                setParsedValue(key, parsed != null && entry.accepts(parsed) ? parsed : null, box);
                editor = box;
            }
            case DOUBLE -> {
                double min = entry.minimum().doubleValue();
                double max = entry.maximum().doubleValue();
                NumericEditBox box = NumericEditBox.decimal(
                        font,
                        editorX,
                        y,
                        editorWidth,
                        20,
                        entry.label(),
                        min < 0,
                        min,
                        max
                );
                box.setMaxLength(350);
                box.setValue(rawTextValues.getOrDefault(
                        key,
                        NumericEditBox.format(((Number) pendingValues.get(key)).doubleValue())
                ));
                box.setResponder(raw -> setParsedValue(key, box.getDoubleValue(), box));
                setParsedValue(key, box.getDoubleValue(), box);
                editor = box;
            }
            case STRING -> {
                EditBox box = new EditBox(font, editorX, y, editorWidth, 20, entry.label());
                box.setMaxLength(32767);
                box.setValue(String.valueOf(pendingValues.get(key)));
                box.setResponder(value -> pendingValues.put(key, value));
                editor = box;
            }
            case CHOICE -> {
                String value = String.valueOf(pendingValues.get(key));
                editor = Button.builder(Component.literal(value), button -> {
                    List<String> choices = entry.choices();
                    String current = String.valueOf(pendingValues.get(key));
                    int next = (choices.indexOf(current) + 1) % choices.size();
                    String selected = choices.get(next);
                    pendingValues.put(key, selected);
                    button.setMessage(Component.literal(selected));
                }).bounds(editorX, y, editorWidth, 20).build();
            }
            case STRING_LIST, ITEM_LIST, ITEM_RULE_LIST, ENTITY_LIST, INTEGER_LIST -> {
                List<?> values = listValue(key);
                editor = Button.builder(
                                Component.translatable(
                                        "gui.kineticcore.config.edit_list",
                                        Component.literal(String.valueOf(values.size())).withStyle(ChatFormatting.AQUA)
                                ),
                                ignored -> openListEditor(page, entry)
                        )
                        .bounds(editorX, y, editorWidth, 20)
                        .build();
            }
            case COLOR -> {
                int currentColor = ((Number) pendingValues.get(key)).intValue() & 0xFFFFFF;
                editor = new ColorPreviewButton(
                        editorX,
                        y,
                        editorWidth,
                        20,
                        currentColor,
                        Component.literal(formatColor(currentColor)),
                        ignored -> ColorPickerScreen.open(
                                this,
                                entry.label(),
                                currentColor,
                                selected -> {
                                    pendingValues.put(key, selected & 0xFFFFFF);
                                    rawTextValues.remove(key);
                                    invalidEntries.remove(key);
                                    status = null;
                                    rebuildWidgets();
                                }
                        )
                );
            }
            default -> throw new IllegalStateException("Unsupported value type: " + entry.type());
        }

        boolean editable = KTConfigApi.canEdit(page);
        if (entry.type() == KTConfigEntry.Type.COLOR && editable) {
            editor.setTooltip(Tooltip.create(
                    entry.tooltip() != null
                            ? Component.translatable("gui.kineticcore.config.color_picker.tooltip").append(Component.literal(" ")).append(entry.tooltip())
                            : Component.translatable("gui.kineticcore.config.color_picker.tooltip")
            ));
        } else if (entry.tooltip() != null) {
            editor.setTooltip(Tooltip.create(entry.tooltip()));
        }
        editor.active = editable;
        addRowScrollableWidget(editor);

        Button reset = Button.builder(
                        Component.translatable("gui.kineticcore.config.reset"),
                        ignored -> {
                            pendingValues.put(key, entry.defaultSnapshot());
                            invalidEntries.remove(key);
                            rawTextValues.remove(key);
                            status = null;
                            rebuildWidgets();
                        })
                .bounds(resetX, y, resetWidth, 20)
                .build();
        reset.active = editable;
        reset.setTooltip(Tooltip.create(
                editable
                        ? Component.translatable("gui.kineticcore.config.reset.tooltip")
                        : KTConfigApi.unavailableReason(page)
        ));
        addRowScrollableWidget(reset);
    }

    private void addActionWidget(KTConfigPage page, KTConfigEntry<?> entry, int y) {
        Button button = Button.builder(
                        Component.translatable("gui.kineticcore.config.open"),
                        ignored -> requestAction(page, entry))
                .bounds(480, y, 132, 20)
                .build();
        boolean editable = KTConfigApi.canEdit(page);
        button.active = editable;
        if (entry.tooltip() != null && editable) {
            button.setTooltip(Tooltip.create(entry.tooltip()));
        } else if (!editable) {
            button.setTooltip(Tooltip.create(KTConfigApi.unavailableReason(page)));
        }
        addRowScrollableWidget(button);
    }

    private void requestAction(KTConfigPage page, KTConfigEntry<?> entry) {
        if (minecraft == null || !ensurePageEditable(page)) return;
        if (!isDirty()) {
            runAction(page, entry);
            return;
        }

        minecraft.setScreen(new ConfirmScreen(
                shouldSave -> {
                    Minecraft.getInstance().setScreen(this);
                    if (shouldSave) {
                        SaveOutcome outcome = persistPendingValues();
                        if (outcome == SaveOutcome.FAILED) return;
                        if (outcome == SaveOutcome.SAVED) showSavedToast();
                        runAction(page, entry);
                    }
                },
                Component.translatable("gui.kineticcore.config.unsaved_action.title"),
                Component.translatable("gui.kineticcore.config.unsaved_action.message")
        ));
    }

    private void runAction(KTConfigPage page, KTConfigEntry<?> entry) {
        if (!ensurePageEditable(page)) return;
        try {
            entry.runAction();
        } catch (Throwable throwable) {
            KineticCore.LOGGER.error(
                    "Config action {} on page {} failed",
                    entry.id(),
                    page.id(),
                    throwable
            );
            status = Component.translatable(
                    "gui.kineticcore.config.action_failed",
                    Component.literal(throwable.getClass().getSimpleName()).withStyle(ChatFormatting.RED)
            );
        }
    }

    private boolean isDirty() {
        if (!invalidEntries.isEmpty()) return true;
        for (KTConfigPage page : pages) {
            for (KTConfigEntry<?> entry : page.entries()) {
                if (!entry.isValue()) continue;
                String key = entryKey(page, entry);
                if (!Objects.equals(pendingValues.get(key), originalValues.get(key))) return true;
            }
        }
        return false;
    }

    private void openListEditor(KTConfigPage page, KTConfigEntry<?> entry) {
        if (minecraft == null || !ensurePageEditable(page)) return;
        String key = entryKey(page, entry);
        List<?> values = listValue(key);

        if (entry.type() == KTConfigEntry.Type.ENTITY_LIST) {
            List<String> entityIds = values.stream().map(String::valueOf).toList();
            minecraft.setScreen(new EntitySelectorScreen(
                    this,
                    entry.label(),
                    entityIds,
                    result -> {
                        pendingValues.put(key, new ArrayList<>(result));
                        invalidEntries.remove(key);
                    }
            ));
            return;
        }

        if (entry.type() == KTConfigEntry.Type.ITEM_LIST
                || entry.type() == KTConfigEntry.Type.ITEM_RULE_LIST) {
            List<String> itemRules = values.stream().map(String::valueOf).toList();
            ItemListEditorScreen.SelectionMode mode = entry.type() == KTConfigEntry.Type.ITEM_LIST
                    ? ItemListEditorScreen.SelectionMode.ITEMS_ONLY
                    : ItemListEditorScreen.SelectionMode.ITEMS_TAGS_MODS;
            minecraft.setScreen(new ItemListEditorScreen(
                    this,
                    entry.label(),
                    itemRules,
                    mode,
                    result -> {
                        pendingValues.put(key, new ArrayList<>(result));
                        invalidEntries.remove(key);
                    }
            ));
            return;
        }

        boolean integerList = entry.type() == KTConfigEntry.Type.INTEGER_LIST;
        minecraft.setScreen(new KTConfigListScreen(
                this,
                entry.label(),
                entry.tooltip(),
                integerList,
                values,
                result -> {
                    pendingValues.put(key, new ArrayList<>(result));
                    invalidEntries.remove(key);
                }
        ));
    }

    private List<?> listValue(String key) {
        Object value = pendingValues.get(key);
        return value instanceof List<?> list ? list : List.of();
    }

    private void setParsedValue(String key, Object value, EditBox box) {
        rawTextValues.put(key, box.getValue());
        if (value == null) {
            invalidEntries.add(key);
            box.setTextColor(0xFFFF5555);
        } else {
            invalidEntries.remove(key);
            pendingValues.put(key, value);
            box.setTextColor(0xFFE0E0E0);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!searchDirty) return;

        searchDirty = false;
        rebuildRows();
        rowScroll.update(rows.size(), VISIBLE_ROWS);
        rebuildWidgets();
        if (searchBox != null) {
            searchBox.setFocused(true);
            searchBox.setCursorPosition(searchQuery.length());
            setFocused(searchBox);
        }
    }

    private void resetAll() {
        for (KTConfigPage page : pages) {
            if (!KTConfigApi.canEdit(page)) continue;
            for (KTConfigEntry<?> entry : page.entries()) {
                if (!entry.isValue()) continue;
                String key = entryKey(page, entry);
                pendingValues.put(key, entry.defaultSnapshot());
                invalidEntries.remove(key);
                rawTextValues.remove(key);
            }
        }
        status = Component.translatable("gui.kineticcore.config.reset_done");
        rebuildWidgets();
    }

    private void saveAndClose() {
        SaveOutcome outcome = persistPendingValues();
        if (outcome == SaveOutcome.FAILED) return;
        if (outcome == SaveOutcome.SAVED) showSavedToast();
        Minecraft.getInstance().setScreen(parent);
    }

    private SaveOutcome persistPendingValues() {
        for (KTConfigPage page : pages) {
            for (KTConfigEntry<?> entry : page.entries()) {
                if (!entry.isValue()) continue;
                String key = entryKey(page, entry);
                if (!entry.accepts(pendingValues.get(key))) invalidEntries.add(key);
            }
        }

        if (!invalidEntries.isEmpty()) {
            status = Component.translatable(
                    "gui.kineticcore.config.invalid",
                    Component.literal(String.valueOf(invalidEntries.size())).withStyle(ChatFormatting.RED)
            );
            return SaveOutcome.FAILED;
        }

        Map<KTConfigPage, List<KTConfigEntry<?>>> changedByPage = new LinkedHashMap<>();
        for (KTConfigPage page : pages) {
            List<KTConfigEntry<?>> changed = page.entries().stream()
                    .filter(KTConfigEntry::isValue)
                    .filter(entry -> {
                        String key = entryKey(page, entry);
                        return !Objects.equals(pendingValues.get(key), originalValues.get(key));
                    })
                    .toList();
            if (!changed.isEmpty()) changedByPage.put(page, changed);
        }

        if (changedByPage.isEmpty()) {
            status = null;
            return SaveOutcome.UNCHANGED;
        }

        for (KTConfigPage page : changedByPage.keySet()) {
            if (!ensurePageEditable(page)) return SaveOutcome.FAILED;
        }

        for (Map.Entry<KTConfigPage, List<KTConfigEntry<?>>> pageChange : changedByPage.entrySet()) {
            KTConfigPage page = pageChange.getKey();
            if (page.scope() == KTConfigScope.SERVER_AUTHORITATIVE) {
                Map<String, Object> changedValues = new LinkedHashMap<>();
                for (KTConfigEntry<?> entry : pageChange.getValue()) {
                    String key = entryKey(page, entry);
                    changedValues.put(entry.id(), entry.snapshot(pendingValues.get(key)));
                }
                if (!KTServerConfigClient.save(page, changedValues)) {
                    status = KTConfigApi.unavailableReason(page).copy().withStyle(ChatFormatting.RED);
                    return SaveOutcome.FAILED;
                }
            } else {
                List<KTConfigEntry<?>> applied = new ArrayList<>();
                try {
                    for (KTConfigEntry<?> entry : pageChange.getValue()) {
                        String key = entryKey(page, entry);
                        entry.writeSnapshot(pendingValues.get(key));
                        applied.add(entry);
                    }
                    page.save();
                } catch (Throwable throwable) {
                    KineticCore.LOGGER.error("Failed to save config page {}", page.id(), throwable);
                    rollbackOriginalValues(page, applied);
                    status = Component.translatable(
                            "gui.kineticcore.config.save_failed",
                            Component.literal(throwable.getClass().getSimpleName()).withStyle(ChatFormatting.RED)
                    );
                    return SaveOutcome.FAILED;
                }
            }

            for (KTConfigEntry<?> entry : pageChange.getValue()) {
                String key = entryKey(page, entry);
                originalValues.put(key, entry.snapshot(pendingValues.get(key)));
            }
        }

        status = null;
        return SaveOutcome.SAVED;
    }

    private boolean ensurePageEditable(KTConfigPage page) {
        if (KTConfigApi.canEdit(page)) return true;
        status = KTConfigApi.unavailableReason(page).copy().withStyle(ChatFormatting.RED);
        GuiOverlay.toast("kineticcore_config_unavailable", status);
        return false;
    }

    private void showSavedToast() {
        try {
            GuiOverlay.toast(Component.translatable(
                    "gui.kineticcore.config.module_saved",
                    title.copy().withStyle(ChatFormatting.GOLD)
            ));
        } catch (Throwable throwable) {
            KineticCore.LOGGER.debug("Could not show module config saved toast", throwable);
        }
    }

    private void rollbackOriginalValues(KTConfigPage page, List<KTConfigEntry<?>> appliedEntries) {
        for (KTConfigEntry<?> entry : appliedEntries) {
            try {
                entry.writeSnapshot(originalValues.get(entryKey(page, entry)));
            } catch (Throwable rollbackFailure) {
                KineticCore.LOGGER.error(
                        "Failed to roll back config value {} on page {}",
                        entry.id(),
                        page.id(),
                        rollbackFailure
                );
            }
        }
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        GuiTheme.panel(graphics, 18, 12, 604, 342);
        graphics.drawCenteredString(font, title, canvasWidth / 2, 24, 0xFFFFAA00);

        hoveredRow = null;
        double pixelOffset = rowPixelOffset();
        enableCanvasScissor(graphics, 28, ROW_TOP, SCROLL_X - 2, ROW_TOP + LIST_HEIGHT);
        try {
            for (Map.Entry<Row, Integer> visible : visibleRows.entrySet()) {
                Row row = visible.getKey();
                int y = visible.getValue() - (int) Math.round(pixelOffset);
                if (y + ROW_HEIGHT <= ROW_TOP || y >= ROW_TOP + LIST_HEIGHT) continue;
                int tooltipWidth = row.kind() == RowKind.PAGE ? 578 : 292;
                if (mouseY >= ROW_TOP && mouseY < ROW_TOP + LIST_HEIGHT
                        && GuiTheme.hovering(mouseX, mouseY, 30, y - 3, tooltipWidth, 23)) {
                    hoveredRow = row;
                }

                switch (row.kind()) {
                    case SCOPE -> {
                        graphics.fill(30, y - 3, 612, y + 20, 0x66303030);
                        graphics.drawString(font, row.text(), 38, y + 4, row.scope().displayColor(), false);
                    }
                    case PAGE -> {
                        graphics.fill(34, y - 2, 608, y + 19, 0x44222222);
                        String text = GuiTheme.trim(font, row.text().getString(), 540);
                        graphics.drawString(font, text, 46, y + 4, 0xFFFFAA00, false);
                        if (!KTConfigApi.canEdit(row.page())) {
                            Component locked = Component.translatable("gui.kineticcore.config.server_locked");
                            graphics.drawString(
                                    font,
                                    locked,
                                    600 - font.width(locked),
                                    y + 4,
                                    0xFFFF5555,
                                    false
                            );
                        }
                    }
                    case ENTRY -> {
                        KTConfigEntry<?> entry = row.entry();
                        String key = entryKey(row.page(), entry);
                        if (entry.type() == KTConfigEntry.Type.SECTION) {
                            graphics.fill(38, y - 3, 612, y + 20, 0x33222222);
                            graphics.drawString(font, entry.label(), 46, y + 4, 0xFFFFCC55, false);
                        } else if (entry.type() == KTConfigEntry.Type.DESCRIPTION) {
                            String text = GuiTheme.trim(font, entry.label().getString(), 554);
                            graphics.drawString(font, text, 46, y + 5, 0xFFAAAAAA, false);
                        } else {
                            int color;
                            if (!KTConfigApi.canEdit(row.page())) {
                                color = 0xFF999999;
                            } else {
                                color = invalidEntries.contains(key) ? 0xFFFF5555 : 0xFFE0E0E0;
                            }
                            String text = GuiTheme.trim(font, entry.label().getString(), 282);
                            graphics.drawString(font, text, 46, y + 6, color, false);
                        }
                    }
                }
            }
        } finally {
            graphics.disableScissor();
        }

        if (rows.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.kineticcore.config.no_fields"),
                    canvasWidth / 2,
                    176,
                    0xFFAAAAAA
            );
        }

        GuiTheme.scrollbar(
                rowScroll,
                graphics,
                mouseX,
                mouseY,
                SCROLL_X,
                ROW_TOP,
                SCROLL_WIDTH,
                LIST_HEIGHT,
                18
        );

        if (status != null) {
            graphics.drawCenteredString(
                    font,
                    status,
                    canvasWidth / 2,
                    309,
                    invalidEntries.isEmpty() ? 0xFFFFFF55 : 0xFFFF5555
            );
        } else {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.kineticcore.config.module_scope_hint"),
                    canvasWidth / 2,
                    309,
                    0xFFAAAAAA
            );
        }
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
    ) {
        if (hoveredRow == null) return;

        Component tooltip = switch (hoveredRow.kind()) {
            case SCOPE -> null;
            case PAGE -> {
                if (!KTConfigApi.canEdit(hoveredRow.page())) {
                    yield KTConfigApi.unavailableReason(hoveredRow.page());
                }
                yield hoveredRow.page().description();
            }
            case ENTRY -> hoveredRow.entry().type() == KTConfigEntry.Type.DESCRIPTION
                    ? hoveredRow.entry().label()
                    : hoveredRow.entry().tooltip();
        };

        if (tooltip != null) {
            showTooltip(tooltip, 400);
        }
    }

    @Override
    protected void renderCanvasForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderSearchPlaceholder(graphics, searchBox);
    }

    private void renderSearchPlaceholder(GuiGraphics graphics, EditBox box) {
        if (box == null || !box.visible || !box.getValue().isEmpty() || box.isFocused()) return;
        String text = font.plainSubstrByWidth(
                Component.translatable("gui.kineticcore.config.search_fields").getString(),
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

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && rowScroll.beginDrag(
                mouseX,
                mouseY,
                SCROLL_X,
                ROW_TOP,
                SCROLL_WIDTH,
                LIST_HEIGHT,
                18,
                2
        )) {
            return true;
        }
        return super.canvasMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (rowScroll.drag(mouseY, ROW_TOP, LIST_HEIGHT, 18)) {
            return true;
        }
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return rowScroll.release(button) || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= 28 && mouseX <= 620
                && mouseY >= ROW_TOP && mouseY < ROW_TOP + LIST_HEIGHT
                && rowScroll.scroll(delta)) {
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Minecraft client = Minecraft.getInstance();
        if (!isDirty()) {
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
                    SaveOutcome outcome = persistPendingValues();
                    if (outcome == SaveOutcome.FAILED) return;
                    if (outcome == SaveOutcome.SAVED) showSavedToast();
                    client.setScreen(parent);
                },
                Component.translatable("gui.kineticcore.config.unsaved_action.title"),
                Component.translatable("gui.kineticcore.config.module_unsaved_close")
        ));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    void serverSnapshotUpdated(String pageId) {
        KTConfigPage page = pages.stream()
                .filter(candidate -> candidate.id().equals(pageId))
                .findFirst()
                .orElse(null);
        if (page == null) return;

        for (KTConfigEntry<?> entry : page.entries()) {
            if (!entry.isValue()) continue;
            String key = entryKey(page, entry);
            Object value;
            try {
                value = entry.readSnapshot();
            } catch (Throwable throwable) {
                KineticCore.LOGGER.error("Failed to refresh server config value {} from page {}", entry.id(), page.id(), throwable);
                value = entry.defaultSnapshot();
            }
            if (!entry.accepts(value)) value = entry.defaultSnapshot();
            Object snapshot = entry.snapshot(value);
            pendingValues.put(key, snapshot);
            originalValues.put(key, entry.snapshot(snapshot));
            invalidEntries.remove(key);
            rawTextValues.remove(key);
        }
        if (minecraft != null) rebuildWidgets();
    }

    private void rebuildRows() {
        String normalized = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Row> result = new ArrayList<>();

        appendScopeGroup(result, false, normalized);
        appendScopeGroup(result, true, normalized);
        rows = List.copyOf(result);
    }

    private void appendScopeGroup(List<Row> result, boolean serverGroup, String normalized) {
        List<Row> groupRows = new ArrayList<>();
        for (KTConfigPage page : pages) {
            boolean serverPage = page.scope() == KTConfigScope.SERVER_AUTHORITATIVE;
            if (serverPage != serverGroup) continue;

            List<KTConfigEntry<?>> matched = matchingEntries(page, normalized);
            boolean pageMatched = normalized.isEmpty() || matchesPage(page, normalized);
            if (!pageMatched && matched.isEmpty()) continue;

            groupRows.add(Row.page(page));
            if (normalized.isEmpty() || pageMatched) {
                for (KTConfigEntry<?> entry : page.entries()) groupRows.add(Row.entry(page, entry));
            } else {
                for (KTConfigEntry<?> entry : matched) groupRows.add(Row.entry(page, entry));
            }
        }

        if (!groupRows.isEmpty()) {
            result.add(Row.scope(serverGroup
                    ? KTConfigScope.SERVER_AUTHORITATIVE
                    : KTConfigScope.CLIENT_LOCAL));
            result.addAll(groupRows);
        }
    }

    private List<KTConfigEntry<?>> matchingEntries(KTConfigPage page, String query) {
        if (query.isEmpty()) return page.entries();
        return page.entries().stream()
                .filter(entry -> matchesEntry(entry, query))
                .toList();
    }

    private boolean matchesPage(KTConfigPage page, String query) {
        StringBuilder raw = new StringBuilder(page.id()).append(' ').append(page.title().getString());
        if (page.description() != null) raw.append(' ').append(page.description().getString());
        raw.append(' ').append(Component.translatable(page.scope().detailTranslationKey()).getString());
        return matches(raw.toString(), query);
    }

    private boolean matchesEntry(KTConfigEntry<?> entry, String query) {
        StringBuilder raw = new StringBuilder(entry.id()).append(' ').append(entry.label().getString());
        if (entry.tooltip() != null) raw.append(' ').append(entry.tooltip().getString());
        return matches(raw.toString(), query);
    }

    private boolean matches(String raw, String query) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains(query)) return true;
        return KineticSearch.match(raw, query);
    }

    private static int scopeOrder(KTConfigScope scope) {
        return switch (scope) {
            case CLIENT_LOCAL -> 0;
            case LOCAL_INSTALLATION -> 1;
            case SERVER_AUTHORITATIVE -> 2;
        };
    }

    private static String scopeHeaderKey(KTConfigScope scope) {
        return switch (scope) {
            case CLIENT_LOCAL, LOCAL_INSTALLATION -> "gui.kineticcore.config.scope.client.header";
            case SERVER_AUTHORITATIVE -> "gui.kineticcore.config.scope.server.header";
        };
    }

    private static String entryKey(KTConfigPage page, KTConfigEntry<?> entry) {
        return page.id() + "/" + entry.id();
    }

    private static Component booleanText(boolean value) {
        return Component.translatable(value
                ? "gui.kineticcore.config.enabled"
                : "gui.kineticcore.config.disabled");
    }

    private static String formatColor(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

}
