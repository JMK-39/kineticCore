package dev.xyat.kineticcore.config.client;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.KineticCore;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.selector.ItemListEditorScreen;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.selector.ColorPickerScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.ColorPreviewButton;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.NumericEditBox;
import dev.xyat.kineticcore.api.client.selector.EntitySelectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Generic editor for one module-owned {@link KTConfigPage}. */
public final class KTConfigScreen extends KineticScreen {
    private enum SaveOutcome {
        FAILED,
        UNCHANGED,
        SAVED
    }

    private record DraftState(
            Map<String, Object> values,
            Map<String, String> rawValues,
            Set<String> invalid
    ) {
    }

    private static final int VISIBLE_ROWS = 8;
    private static final int ROW_TOP = 64;
    private static final int ROW_HEIGHT = 27;
    private static final int LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;
    private static final int SCROLL_X = 615;
    private static final int SCROLL_WIDTH = 4;

    private final Screen parent;
    private final KTConfigPage configPage;
    private final Map<String, Object> pendingValues = new HashMap<>();
    private final Map<String, Object> originalValues = new HashMap<>();
    private final Map<String, String> rawTextValues = new HashMap<>();
    private final Set<String> invalidEntries = new HashSet<>();
    private final Map<KTConfigEntry<?>, Integer> visibleRows = new HashMap<>();
    private final KineticSearch.Model<KTConfigEntry<?>> entryModel =
            new KineticSearch.Model<>(List.of(), KTConfigScreen::buildSearchData);
    private final GridScrollController entryScroll = new GridScrollController();
    private Component status;
    private EditBox searchBox;
    private KTConfigEntry<?> hoveredEntry;
    private String searchQuery = "";
    private boolean searchDirty;
    private final boolean showApplyTiming;

    public KTConfigScreen(Screen parent, KTConfigPage configPage) {
        super(configPage.title());
        this.parent = parent;
        this.configPage = configPage;
        this.showApplyTiming = configPage.showsApplyTiming();
        this.entryModel.setSource(configPage.entries());
        useCanvas(640, 360, 6);
        refreshFromSource();
        configureDraft(this::captureDraftState, this::restoreDraftState);
        KTServerConfigClient.request(configPage);
    }

    /**
     * Reloads every draft value after a specialized child editor saves the
     * same backing configuration. Child screens should call this before
     * returning so a later page save cannot restore stale values.
     */
    public void refreshFromSource() {
        pendingValues.clear();
        originalValues.clear();
        rawTextValues.clear();
        invalidEntries.clear();
        status = null;
        for (KTConfigEntry<?> entry : configPage.entries()) {
            if (!entry.isValue()) continue;
            Object value;
            try {
                value = entry.readSnapshot();
            } catch (Throwable throwable) {
                KineticCore.LOGGER.error("Failed to read config value {} from page {}",
                        entry.id(), configPage.id(), throwable);
                value = entry.defaultSnapshot();
            }
            if (!entry.accepts(value)) {
                KineticCore.LOGGER.warn("Invalid config value {} on page {}; using its default",
                        entry.id(), configPage.id());
                value = entry.defaultSnapshot();
            }
            Object snapshot = entry.snapshot(value);
            pendingValues.put(entry.id(), snapshot);
            originalValues.put(entry.id(), entry.snapshot(snapshot));
        }
    }

    private DraftState captureDraftState() {
        Map<String, Object> values = new HashMap<>();
        for (KTConfigEntry<?> entry : configPage.entries()) {
            if (!entry.isValue()) continue;
            Object value = pendingValues.get(entry.id());
            values.put(entry.id(), value == null ? null : entry.snapshot(value));
        }
        return new DraftState(values, new HashMap<>(rawTextValues), new HashSet<>(invalidEntries));
    }

    private void restoreDraftState(DraftState state) {
        if (state == null) return;
        pendingValues.clear();
        for (KTConfigEntry<?> entry : configPage.entries()) {
            if (!entry.isValue()) continue;
            Object value = state.values().get(entry.id());
            pendingValues.put(entry.id(), value == null ? null : entry.snapshot(value));
        }
        rawTextValues.clear();
        rawTextValues.putAll(state.rawValues());
        invalidEntries.clear();
        invalidEntries.addAll(state.invalid());
        status = null;
        if (minecraft != null) rebuildWidgets();
    }

    @Override
    protected void buildUi() {
        resetScrollableWidgets();
        visibleRows.clear();
        entryModel.refresh(searchQuery);
        entryScroll.update(entryModel.items().size(), VISIBLE_ROWS);

        searchBox = new EditBox(
                font, 38, 37, 430, 18,
                Component.translatable("gui.kineticcore.config.search_fields")
        );
        searchBox.setMaxLength(256);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(query -> {
            searchQuery = query == null ? "" : query;
            entryScroll.reset();
            searchDirty = true;
        });
        addRenderableWidget(searchBox);

        List<KTConfigEntry<?>> entries = entryModel.items();
        for (int index = 0; index < entries.size(); index++) {
            KTConfigEntry<?> entry = entries.get(index);
            int y = ROW_TOP + index * ROW_HEIGHT;
            visibleRows.put(entry, y);
            if (entry.isValue()) {
                addValueWidgets(entry, y);
            } else if (entry.type() == KTConfigEntry.Type.ACTION) {
                addActionWidget(entry, y);
            }
        }

        int footerY = 325;
        boolean editable = KTConfigApi.canEdit(configPage);
        Button resetAllButton = Button.builder(Component.translatable("gui.kineticcore.config.reset_all"), ignored -> resetAll())
                .bounds(166, footerY, 92, 20).build();
        resetAllButton.active = editable;
        if (!editable) resetAllButton.setTooltip(Tooltip.create(KTConfigApi.unavailableReason(configPage)));
        addRenderableWidget(resetAllButton);

        addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.config.back"), ignored -> onClose())
                .bounds(274, footerY, 92, 20).build());

        Button saveButton = Button.builder(Component.translatable("gui.kineticcore.config.save"), ignored -> saveAndClose())
                .bounds(382, footerY, 92, 20).build();
        saveButton.active = editable;
        if (!editable) saveButton.setTooltip(Tooltip.create(KTConfigApi.unavailableReason(configPage)));
        addRenderableWidget(saveButton);
    }

    private double entryPixelOffset() {
        return entryScroll.smoothOffset() * ROW_HEIGHT;
    }

    private <T extends AbstractWidget> T addEntryScrollableWidget(T widget) {
        return addScrollableWidget(
                widget,
                28,
                ROW_TOP,
                SCROLL_X - 2,
                ROW_TOP + LIST_HEIGHT,
                this::entryPixelOffset
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

    private void addValueWidgets(KTConfigEntry<?> entry, int y) {
        final int resetX = 554;
        final int resetWidth = 58;
        final int editorWidth = compactEditorWidth(entry.type());
        final int editorX = resetX - 8 - editorWidth;
        AbstractWidget editor;

        switch (entry.type()) {
            case BOOLEAN -> {
                boolean value = Boolean.TRUE.equals(pendingValues.get(entry.id()));
                editor = Button.builder(booleanText(value), button -> {
                    boolean next = !Boolean.TRUE.equals(pendingValues.get(entry.id()));
                    pendingValues.put(entry.id(), next);
                    button.setMessage(booleanText(next));
                }).bounds(editorX, y, editorWidth, 20).build();
            }
            case INTEGER -> {
                int min = entry.minimum().intValue();
                int max = entry.maximum().intValue();
                NumericEditBox box = NumericEditBox.integer(font, editorX, y, editorWidth, 20,
                        entry.label(), min < 0, min, max);
                box.setValue(rawTextValues.getOrDefault(entry.id(),
                        Integer.toString(((Number) pendingValues.get(entry.id())).intValue())));
                box.setResponder(raw -> {
                    Integer parsed = box.getIntValue();
                    setParsedValue(entry.id(), parsed, box);
                });
                setParsedValue(entry.id(), box.getIntValue(), box);
                editor = box;
            }
            case LONG -> {
                long min = entry.minimum().longValue();
                long max = entry.maximum().longValue();
                NumericEditBox box = NumericEditBox.longInteger(font, editorX, y, editorWidth, 20,
                        entry.label(), min < 0, null, null);
                box.setValue(rawTextValues.getOrDefault(entry.id(),
                        Long.toString(((Number) pendingValues.get(entry.id())).longValue())));
                box.setResponder(raw -> {
                    Long parsed = box.getLongValue();
                    setParsedValue(entry.id(), parsed != null && entry.accepts(parsed) ? parsed : null, box);
                });
                Long parsed = box.getLongValue();
                setParsedValue(entry.id(), parsed != null && entry.accepts(parsed) ? parsed : null, box);
                editor = box;
            }
            case DOUBLE -> {
                double min = entry.minimum().doubleValue();
                double max = entry.maximum().doubleValue();
                NumericEditBox box = NumericEditBox.decimal(font, editorX, y, editorWidth, 20,
                        entry.label(), min < 0, min, max);
                // Keep this config-specific: finite doubles rendered in plain
                // notation can exceed vanilla EditBox's default length.
                box.setMaxLength(350);
                box.setValue(rawTextValues.getOrDefault(entry.id(),
                        NumericEditBox.format(((Number) pendingValues.get(entry.id())).doubleValue())));
                box.setResponder(raw -> {
                    Double parsed = box.getDoubleValue();
                    setParsedValue(entry.id(), parsed, box);
                });
                setParsedValue(entry.id(), box.getDoubleValue(), box);
                editor = box;
            }
            case STRING -> {
                EditBox box = new EditBox(font, editorX, y, editorWidth, 20, entry.label());
                box.setMaxLength(32767);
                box.setValue(String.valueOf(pendingValues.get(entry.id())));
                box.setResponder(value -> pendingValues.put(entry.id(), value));
                editor = box;
            }
            case CHOICE -> {
                String value = String.valueOf(pendingValues.get(entry.id()));
                editor = Button.builder(Component.literal(value), button -> {
                    List<String> choices = entry.choices();
                    String current = String.valueOf(pendingValues.get(entry.id()));
                    int next = (choices.indexOf(current) + 1) % choices.size();
                    String selected = choices.get(next);
                    pendingValues.put(entry.id(), selected);
                    button.setMessage(Component.literal(selected));
                }).bounds(editorX, y, editorWidth, 20).build();
            }
            case STRING_LIST, ITEM_LIST, ITEM_RULE_LIST, ENTITY_LIST, INTEGER_LIST -> {
                List<?> values = listValue(entry.id());
                editor = Button.builder(
                        Component.translatable("gui.kineticcore.config.edit_list", Component.literal(String.valueOf(values.size())).withStyle(ChatFormatting.AQUA)),
                        ignored -> openListEditor(entry)
                ).bounds(editorX, y, editorWidth, 20).build();
            }
            case COLOR -> {
                int currentColor = ((Number) pendingValues.get(entry.id())).intValue() & 0xFFFFFF;
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
                                    pendingValues.put(entry.id(), selected & 0xFFFFFF);
                                    rawTextValues.remove(entry.id());
                                    invalidEntries.remove(entry.id());
                                    status = null;
                                    rebuildWidgets();
                                }
                        )
                );
            }
            default -> throw new IllegalStateException("Unsupported value type: " + entry.type());
        }

        boolean editable = KTConfigApi.canEdit(configPage);
        editor.active = editable;
        if (entry.type() == KTConfigEntry.Type.COLOR && editable) {
            editor.setTooltip(Tooltip.create(
                    entry.tooltip() != null
                            ? Component.translatable("gui.kineticcore.config.color_picker.tooltip").append(Component.literal(" ")).append(entry.tooltip())
                            : Component.translatable("gui.kineticcore.config.color_picker.tooltip")
            ));
        } else if (entry.tooltip() != null && editable) {
            editor.setTooltip(Tooltip.create(entry.tooltip()));
        } else if (!editable) {
            editor.setTooltip(Tooltip.create(KTConfigApi.unavailableReason(configPage)));
        }
        addEntryScrollableWidget(editor);
        Button reset = Button.builder(Component.translatable("gui.kineticcore.config.reset"), ignored -> {
            pendingValues.put(entry.id(), entry.defaultSnapshot());
            invalidEntries.remove(entry.id());
            rawTextValues.remove(entry.id());
            status = null;
            rebuildWidgets();
        }).bounds(resetX, y, resetWidth, 20).build();
        reset.active = editable;
        reset.setTooltip(Tooltip.create(
                editable
                        ? Component.translatable("gui.kineticcore.config.reset.tooltip")
                        : KTConfigApi.unavailableReason(configPage)
        ));
        addEntryScrollableWidget(reset);
    }

    private void addActionWidget(KTConfigEntry<?> entry, int y) {
        Button button = Button.builder(
                        Component.translatable("gui.kineticcore.config.open"),
                        ignored -> requestAction(entry))
                .bounds(480, y, 132, 20).build();
        boolean editable = KTConfigApi.canEdit(configPage);
        button.active = editable;
        if (entry.tooltip() != null && editable) {
            button.setTooltip(Tooltip.create(entry.tooltip()));
        } else if (!editable) {
            button.setTooltip(Tooltip.create(KTConfigApi.unavailableReason(configPage)));
        }
        addEntryScrollableWidget(button);
    }

    private void requestAction(KTConfigEntry<?> entry) {
        if (minecraft == null) return;
        if (!isDirty()) {
            runAction(entry);
            return;
        }

        minecraft.setScreen(new ConfirmScreen(
                shouldSave -> {
                    Minecraft.getInstance().setScreen(this);
                    if (shouldSave) {
                        SaveOutcome outcome = persistPendingValues();
                        if (outcome == SaveOutcome.FAILED) return;
                        if (shouldShowImmediateSavedToast(outcome)) showSavedToast();
                        runAction(entry);
                    }
                },
                Component.translatable("gui.kineticcore.config.unsaved_action.title"),
                unsavedMessage()
        ));
    }

    private void runAction(KTConfigEntry<?> entry) {
        if (!ensurePageEditable()) return;
        try {
            entry.runAction();
        } catch (Throwable throwable) {
            KineticCore.LOGGER.error("Config action {} on page {} failed",
                    entry.id(), configPage.id(), throwable);
            status = Component.translatable(
                    "gui.kineticcore.config.action_failed",
                    Component.literal(throwable.getClass().getSimpleName()).withStyle(ChatFormatting.RED)
            );
        }
    }

    private boolean isDirty() {
        if (!invalidEntries.isEmpty()) return true;
        for (KTConfigEntry<?> entry : configPage.entries()) {
            if (entry.isValue() && !Objects.equals(
                    pendingValues.get(entry.id()), originalValues.get(entry.id()))) {
                return true;
            }
        }
        return false;
    }

    private void openListEditor(KTConfigEntry<?> entry) {
        if (minecraft == null) return;
        List<?> values = listValue(entry.id());
        if (entry.type() == KTConfigEntry.Type.ENTITY_LIST) {
            List<String> entityIds = values.stream().map(String::valueOf).toList();
            minecraft.setScreen(new EntitySelectorScreen(
                    this,
                    entry.label(),
                    entityIds,
                    result -> {
                        pendingValues.put(entry.id(), new ArrayList<>(result));
                        invalidEntries.remove(entry.id());
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
                        pendingValues.put(entry.id(), new ArrayList<>(result));
                        invalidEntries.remove(entry.id());
                    }
            ));
            return;
        }
        boolean integerList = entry.type() == KTConfigEntry.Type.INTEGER_LIST;
        minecraft.setScreen(new KTConfigListScreen(
                this, entry.label(), entry.tooltip(), integerList, values,
                result -> {
                    pendingValues.put(entry.id(), new ArrayList<>(result));
                    invalidEntries.remove(entry.id());
                }
        ));
    }

    private List<?> listValue(String id) {
        Object value = pendingValues.get(id);
        return value instanceof List<?> list ? list : List.of();
    }

    private void setParsedValue(String id, Object value, EditBox box) {
        rawTextValues.put(id, box.getValue());
        if (value == null) {
            invalidEntries.add(id);
            box.setTextColor(0xFFFF5555);
        } else {
            invalidEntries.remove(id);
            pendingValues.put(id, value);
            box.setTextColor(0xFFE0E0E0);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!searchDirty) return;

        searchDirty = false;
        entryModel.refresh(searchQuery);
        entryScroll.update(entryModel.items().size(), VISIBLE_ROWS);
        rebuildWidgets();
        if (searchBox != null) {
            searchBox.setFocused(true);
            searchBox.setCursorPosition(searchQuery.length());
            setFocused(searchBox);
        }
    }

    private void resetAll() {
        for (KTConfigEntry<?> entry : configPage.entries()) {
            if (entry.isValue()) pendingValues.put(entry.id(), entry.defaultSnapshot());
        }
        invalidEntries.clear();
        rawTextValues.clear();
        status = Component.translatable("gui.kineticcore.config.reset_done");
        rebuildWidgets();
    }

    private void saveAndClose() {
        SaveOutcome outcome = persistPendingValues();
        if (outcome == SaveOutcome.FAILED) return;
        if (shouldShowImmediateSavedToast(outcome)) showSavedToast();
        commitDraft();
        Minecraft.getInstance().setScreen(parent);
    }

    private SaveOutcome persistPendingValues() {
        if (!ensurePageEditable()) return SaveOutcome.FAILED;

        for (KTConfigEntry<?> entry : configPage.entries()) {
            if (entry.isValue() && !entry.accepts(pendingValues.get(entry.id()))) {
                invalidEntries.add(entry.id());
            }
        }
        if (!invalidEntries.isEmpty()) {
            status = Component.translatable("gui.kineticcore.config.invalid", Component.literal(String.valueOf(invalidEntries.size())).withStyle(ChatFormatting.RED));
            return SaveOutcome.FAILED;
        }

        List<KTConfigEntry<?>> changedEntries = configPage.entries().stream()
                .filter(KTConfigEntry::isValue)
                .filter(entry -> !Objects.equals(
                        pendingValues.get(entry.id()), originalValues.get(entry.id())))
                .toList();
        if (changedEntries.isEmpty()) {
            status = null;
            return SaveOutcome.UNCHANGED;
        }

        if (configPage.scope() == KTConfigScope.SERVER_AUTHORITATIVE) {
            Map<String, Object> changedValues = new HashMap<>();
            for (KTConfigEntry<?> entry : changedEntries) {
                changedValues.put(entry.id(), entry.snapshot(pendingValues.get(entry.id())));
            }
            if (!KTServerConfigClient.save(configPage, changedValues)) {
                status = KTConfigApi.unavailableReason(configPage);
                return SaveOutcome.FAILED;
            }
        } else {
            List<KTConfigEntry<?>> appliedEntries = new ArrayList<>();
            try {
                for (KTConfigEntry<?> entry : changedEntries) {
                    entry.writeSnapshot(pendingValues.get(entry.id()));
                    appliedEntries.add(entry);
                }
                configPage.save();
            } catch (Throwable throwable) {
                KineticCore.LOGGER.error("Failed to save config page {}", configPage.id(), throwable);
                rollbackOriginalValues(appliedEntries);
                status = Component.translatable("gui.kineticcore.config.save_failed", Component.literal(throwable.getClass().getSimpleName()).withStyle(ChatFormatting.RED));
                return SaveOutcome.FAILED;
            }
        }

        for (KTConfigEntry<?> entry : changedEntries) {
            originalValues.put(entry.id(), entry.snapshot(pendingValues.get(entry.id())));
        }
        status = null;
        return SaveOutcome.SAVED;
    }

    private boolean ensurePageEditable() {
        if (KTConfigApi.canEdit(configPage)) return true;
        status = KTConfigApi.unavailableReason(configPage);
        GuiOverlay.toast("kineticcore_config_unavailable", status);
        return false;
    }

    private boolean shouldShowImmediateSavedToast(SaveOutcome outcome) {
        return outcome == SaveOutcome.UNCHANGED
                || (outcome == SaveOutcome.SAVED && configPage.scope() != KTConfigScope.SERVER_AUTHORITATIVE);
    }

    private void showSavedToast() {
        try {
            KTConfigApi.notifySaved(configPage);
        } catch (Throwable throwable) {
            KineticCore.LOGGER.debug("Could not show config saved toast", throwable);
        }
    }

    private void rollbackOriginalValues(List<KTConfigEntry<?>> appliedEntries) {
        for (KTConfigEntry<?> entry : appliedEntries) {
            try {
                entry.writeSnapshot(originalValues.get(entry.id()));
            } catch (Throwable rollbackFailure) {
                KineticCore.LOGGER.error("Failed to roll back config value {} on page {}",
                        entry.id(), configPage.id(), rollbackFailure);
            }
        }
    }


    void serverSnapshotUpdated(String pageId) {
        if (!configPage.id().equals(pageId)) return;
        KTServerConfigClient.applyCached(configPage);
        refreshFromSource();
        commitDraft();
        if (minecraft != null) rebuildWidgets();
    }

    private static Component booleanText(boolean value) {
        return Component.translatable(value
                ? "gui.kineticcore.config.enabled"
                : "gui.kineticcore.config.disabled");
    }

    private static String formatColor(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 18, 12, 604, 342);
        graphics.drawCenteredString(font, title, canvasWidth / 2, 24, 0xFFFFAA00);

        hoveredEntry = null;
        double pixelOffset = entryPixelOffset();
        enableCanvasScissor(graphics, 28, ROW_TOP, SCROLL_X - 2, ROW_TOP + LIST_HEIGHT);
        try {
            for (Map.Entry<KTConfigEntry<?>, Integer> row : visibleRows.entrySet()) {
                KTConfigEntry<?> entry = row.getKey();
                int y = row.getValue() - (int) Math.round(pixelOffset);
                if (y + ROW_HEIGHT <= ROW_TOP || y >= ROW_TOP + LIST_HEIGHT) continue;
                int tooltipWidth = entry.type() == KTConfigEntry.Type.DESCRIPTION ? 582 : 292;
                if (mouseY >= ROW_TOP && mouseY < ROW_TOP + LIST_HEIGHT
                        && GuiTheme.hovering(mouseX, mouseY, 30, y - 3, tooltipWidth, 23)) {
                    hoveredEntry = entry;
                }
                if (entry.type() == KTConfigEntry.Type.SECTION) {
                    graphics.fill(30, y - 3, 612, y + 20, 0x55222222);
                    graphics.drawString(font, entry.label(), 38, y + 4, 0xFFFFAA00, false);
                } else if (entry.type() == KTConfigEntry.Type.DESCRIPTION) {
                    String text = GuiTheme.trim(font, entry.label().getString(), 562);
                    graphics.drawString(font, text, 38, y + 5, 0xFFAAAAAA, false);
                } else {
                    int color = invalidEntries.contains(entry.id()) ? 0xFFFF5555 : 0xFFE0E0E0;
                    String text = GuiTheme.trim(font, entry.label().getString(), 282);
                    graphics.drawString(font, text, 38, y + 6, color, false);
                }
            }
        } finally {
            graphics.disableScissor();
        }

        if (entryModel.items().isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.kineticcore.config.no_fields"),
                    canvasWidth / 2,
                    176,
                    0xFFAAAAAA
            );
        }

        GuiTheme.scrollbar(
                entryScroll, graphics, mouseX, mouseY,
                SCROLL_X, ROW_TOP, SCROLL_WIDTH, LIST_HEIGHT, 18
        );

        if (status != null) {
            graphics.drawCenteredString(font, status, canvasWidth / 2, 309,
                    invalidEntries.isEmpty() ? 0xFFFFFF55 : 0xFFFF5555);
        } else if (showApplyTiming) {
            List<FormattedCharSequence> timingLines = font.split(configPage.applyDetail(), 570);
            int visibleLineCount = Math.min(2, timingLines.size());
            int firstY = visibleLineCount == 1 ? 309 : 298;
            for (int index = 0; index < visibleLineCount; index++) {
                graphics.drawCenteredString(
                        font,
                        timingLines.get(index),
                        canvasWidth / 2,
                        firstY + index * 11,
                        configPage.applyTiming().displayColor()
                );
            }
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
        if (hoveredEntry == null) return;
        Component tooltip = hoveredEntry.type() == KTConfigEntry.Type.DESCRIPTION
                ? hoveredEntry.label()
                : hoveredEntry.tooltip();
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
        renderSearchPlaceholder(
                graphics,
                searchBox
        );
    }

    private void renderSearchPlaceholder(
            GuiGraphics graphics,
            EditBox box
    ) {
        if (box == null
                || !box.visible
                || !box.getValue().isEmpty()
                || box.isFocused()) {
            return;
        }

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
        if (button == 0 && entryScroll.beginDrag(
                mouseX, mouseY, SCROLL_X, ROW_TOP, SCROLL_WIDTH, LIST_HEIGHT, 18, 2)) {
            return true;
        }
        return super.canvasMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (entryScroll.drag(mouseY, ROW_TOP, LIST_HEIGHT, 18)) {
            return true;
        }
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return entryScroll.release(button)
                || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= 28 && mouseX <= 620
                && mouseY >= ROW_TOP && mouseY < ROW_TOP + LIST_HEIGHT
                && entryScroll.scroll(delta)) {
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        discardDraft();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String buildSearchData(KTConfigEntry<?> entry) {
        StringBuilder data = new StringBuilder(entry.id())
                .append(' ')
                .append(entry.label().getString());
        if (entry.tooltip() != null) data.append(' ').append(entry.tooltip().getString());
        String raw = data.toString();
        return raw + ' ' + KineticSearch.pinyin(raw);
    }

    private Component unsavedMessage() {
        return Component.translatable("gui.kineticcore.config.unsaved_action.message")
                .copy()
                .append("\n")
                .append(configPage.applyDetail());
    }

}
