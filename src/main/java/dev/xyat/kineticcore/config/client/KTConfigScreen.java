package dev.xyat.kineticcore.config.client;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.KineticCore;
import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.GuiToastUtil;
import dev.xyat.kineticcore.api.client.ItemListEditorScreen;
import dev.xyat.kineticcore.api.client.PinyinUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.color.ColorPickerApi;
import dev.xyat.kineticcore.api.client.gui.ConfigScrollbarTheme;
import dev.xyat.kineticcore.api.client.gui.GridScrollController;
import dev.xyat.kineticcore.api.client.gui.NumericEditBox;
import dev.xyat.kineticcore.api.client.gui.SearchableListModel;
import dev.xyat.kineticcore.api.client.entity.EntitySelectorScreen;
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
public final class KTConfigScreen extends ScaledScreen {
    private enum SaveOutcome {
        FAILED,
        UNCHANGED,
        SAVED
    }

    private static final int VISIBLE_ROWS = 8;
    private static final int ROW_TOP = 64;
    private static final int ROW_HEIGHT = 27;
    private static final int LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;
    private static final int SCROLL_X = 615;
    private static final int SCROLL_WIDTH = 5;

    private final Screen parent;
    private final KTConfigPage configPage;
    private final Map<String, Object> pendingValues = new HashMap<>();
    private final Map<String, Object> originalValues = new HashMap<>();
    private final Map<String, String> rawTextValues = new HashMap<>();
    private final Set<String> invalidEntries = new HashSet<>();
    private final Map<KTConfigEntry<?>, Integer> visibleRows = new HashMap<>();
    private final SearchableListModel<KTConfigEntry<?>> entryModel =
            new SearchableListModel<>(List.of(), KTConfigScreen::buildSearchData);
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
        configureResponsiveCanvas(640, 360, 6);
        refreshFromSource();
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

    @Override
    protected void initScaled() {
        visibleRows.clear();
        entryModel.refresh(searchQuery);
        entryScroll.update(entryModel.items().size(), VISIBLE_ROWS);

        searchBox = new EditBox(
                font, 38, 37, 574, 18,
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
        int first = entryScroll.offset();
        int last = Math.min(first + VISIBLE_ROWS, entries.size());
        for (int index = first; index < last; index++) {
            KTConfigEntry<?> entry = entries.get(index);
            int row = index - first;
            int y = ROW_TOP + row * ROW_HEIGHT;
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
                .bounds(116, footerY, 122, 20).build();
        resetAllButton.active = editable;
        if (!editable) resetAllButton.setTooltip(Tooltip.create(KTConfigApi.unavailableReason(configPage)));
        addRenderableWidget(resetAllButton);

        addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.config.back"), ignored -> onClose())
                .bounds(258, footerY, 122, 20).build());

        Button saveButton = Button.builder(Component.translatable("gui.kineticcore.config.save"), ignored -> saveAndClose())
                .bounds(400, footerY, 122, 20).build();
        saveButton.active = editable;
        if (!editable) saveButton.setTooltip(Tooltip.create(KTConfigApi.unavailableReason(configPage)));
        addRenderableWidget(saveButton);
    }

    private void addValueWidgets(KTConfigEntry<?> entry, int y) {
        final int editorX = 330;
        final int editorWidth = 210;
        final int resetX = 548;
        final int resetWidth = 64;
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
                editor = Button.builder(
                                Component.literal(formatColor(currentColor)),
                                ignored -> ColorPickerApi.openColorPicker(
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
                        )
                        .bounds(editorX, y, editorWidth, 20)
                        .build();
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
        addRenderableWidget(editor);
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
        addRenderableWidget(reset);
    }

    private void addActionWidget(KTConfigEntry<?> entry, int y) {
        Button button = Button.builder(
                        Component.translatable("gui.kineticcore.config.open"),
                        ignored -> requestAction(entry))
                .bounds(330, y, 282, 20).build();
        boolean editable = KTConfigApi.canEdit(configPage);
        button.active = editable;
        if (entry.tooltip() != null && editable) {
            button.setTooltip(Tooltip.create(entry.tooltip()));
        } else if (!editable) {
            button.setTooltip(Tooltip.create(KTConfigApi.unavailableReason(configPage)));
        }
        addRenderableWidget(button);
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
                        if (outcome == SaveOutcome.SAVED && configPage.scope() != KTConfigScope.SERVER_AUTHORITATIVE) showSavedToast();
                        runAction(entry);
                    }
                },
                Component.translatable("gui.kineticcore.config.unsaved_action.title"),
                unsavedMessage("gui.kineticcore.config.unsaved_action.message")
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
                this, entry.label(), integerList, values,
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
        if (outcome == SaveOutcome.SAVED && configPage.scope() != KTConfigScope.SERVER_AUTHORITATIVE) showSavedToast();
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
        GuiToastUtil.showToast("kineticcore_config_unavailable", status);
        return false;
    }

    private void showSavedToast() {
        try {
            Component message = configPage.applyNotice() == null
                    ? Component.translatable(
                            configPage.applyTiming().savedTranslationKey(),
                            configPage.title().copy().withStyle(ChatFormatting.GOLD)
                    )
                    : Component.translatable(
                            "gui.kineticcore.config.saved",
                            configPage.title().copy().withStyle(ChatFormatting.GOLD)
                    )
                            .copy()
                            .append(" — ")
                            .append(configPage.applyNotice());
            GuiToastUtil.showToast(message);
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
    protected void renderScaledBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiRenderUtil.drawStandardPanel(graphics, 18, 12, 604, 342);
        graphics.drawCenteredString(font, title, vWidth / 2, 24, 0xFFFFAA00);

        hoveredEntry = null;
        for (Map.Entry<KTConfigEntry<?>, Integer> row : visibleRows.entrySet()) {
            KTConfigEntry<?> entry = row.getKey();
            int y = row.getValue();
            int tooltipWidth = entry.type() == KTConfigEntry.Type.DESCRIPTION ? 582 : 292;
            if (GuiRenderUtil.isHovering(mouseX, mouseY, 30, y - 3, tooltipWidth, 23)) {
                hoveredEntry = entry;
            }
            if (entry.type() == KTConfigEntry.Type.SECTION) {
                graphics.fill(30, y - 3, 612, y + 20, 0x55222222);
                graphics.drawString(font, entry.label(), 38, y + 4, 0xFFFFAA00, false);
            } else if (entry.type() == KTConfigEntry.Type.DESCRIPTION) {
                String text = GuiRenderUtil.trimText(font, entry.label().getString(), 562);
                graphics.drawString(font, text, 38, y + 5, 0xFFAAAAAA, false);
            } else {
                int color = invalidEntries.contains(entry.id()) ? 0xFFFF5555 : 0xFFE0E0E0;
                String text = GuiRenderUtil.trimText(font, entry.label().getString(), 282);
                graphics.drawString(font, text, 38, y + 6, color, false);
            }
        }

        if (entryModel.items().isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.kineticcore.config.no_fields"),
                    vWidth / 2,
                    176,
                    0xFFAAAAAA
            );
        }

        ConfigScrollbarTheme.render(
                entryScroll, graphics, mouseX, mouseY,
                SCROLL_X, ROW_TOP, SCROLL_WIDTH, LIST_HEIGHT, 18
        );

        if (status != null) {
            graphics.drawCenteredString(font, status, vWidth / 2, 309,
                    invalidEntries.isEmpty() ? 0xFFFFFF55 : 0xFFFF5555);
        } else if (showApplyTiming) {
            List<FormattedCharSequence> timingLines = font.split(configPage.applyDetail(), 570);
            int visibleLineCount = Math.min(2, timingLines.size());
            int firstY = visibleLineCount == 1 ? 309 : 298;
            for (int index = 0; index < visibleLineCount; index++) {
                graphics.drawCenteredString(
                        font,
                        timingLines.get(index),
                        vWidth / 2,
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
            graphics.renderTooltip(font, font.split(tooltip, 400), mouseX, mouseY);
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
                "gui.kineticcore.config.search_fields"
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

    @Override
    protected boolean universalMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && entryScroll.beginDrag(
                mouseX, mouseY, SCROLL_X, ROW_TOP, SCROLL_WIDTH, LIST_HEIGHT, 18, 2)) {
            rebuildWidgets();
            return true;
        }
        return super.universalMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean universalMouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        int previousOffset = entryScroll.offset();
        if (entryScroll.drag(mouseY, ROW_TOP, LIST_HEIGHT, 18)) {
            if (entryScroll.offset() != previousOffset) rebuildWidgets();
            return true;
        }
        return super.universalMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean universalMouseReleased(double mouseX, double mouseY, int button) {
        return entryScroll.release(button)
                || super.universalMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean universalMouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= 28 && mouseX <= 620
                && mouseY >= ROW_TOP && mouseY < ROW_TOP + LIST_HEIGHT
                && entryScroll.scroll(delta)) {
            rebuildWidgets();
            return true;
        }
        return super.universalMouseScrolled(mouseX, mouseY, delta);
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
                    if (outcome == SaveOutcome.SAVED && configPage.scope() != KTConfigScope.SERVER_AUTHORITATIVE) showSavedToast();
                    client.setScreen(parent);
                },
                Component.translatable("gui.kineticcore.config.unsaved_action.title"),
                unsavedMessage("gui.kineticcore.config.unsaved_close.message")
        ));
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
        return raw + ' ' + PinyinUtil.getSearchData(raw);
    }

    private Component unsavedMessage(String translationKey) {
        return Component.translatable(translationKey)
                .copy()
                .append("\n")
                .append(configPage.applyDetail());
    }

}
