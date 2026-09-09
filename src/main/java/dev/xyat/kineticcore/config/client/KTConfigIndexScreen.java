package dev.xyat.kineticcore.config.client;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KTConfigIndexScreen extends KineticScreen {
    private static final int LIST_X = 62;
    private static final int LIST_Y = 86;
    private static final int LIST_WIDTH = 510;
    private static final int LIST_HEIGHT = 252;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_STRIDE = 28;
    private static final int VISIBLE_ROWS = LIST_HEIGHT / ROW_STRIDE;
    private static final int SCROLL_X = 578;
    private static final int SCROLL_WIDTH = 4;
    private static final int MODULE_NAME_X = LIST_X + 9;
    private static final int MODULE_FUNCTION_X = LIST_X + 185;
    private static final int MODULE_NAME_COLOR = 0xFFFFAA00;
    private static final int MODULE_FUNCTION_COLOR = 0xFF55FFFF;

    private record ModuleGroup(
            String namespace,
            Component title,
            Component function,
            Component tooltip,
            List<KTConfigPage> pages
    ) {
        long entryCount() {
            return pages.stream()
                    .flatMap(page -> page.entries().stream())
                    .filter(entry -> entry.isValue() || entry.type() == KTConfigEntry.Type.ACTION)
                    .count();
        }
    }

    private final Screen parent;
    private final String ownerNamespace;
    private final KineticSearch.Model<ModuleGroup> moduleModel =
            new KineticSearch.Model<>(List.of(), KTConfigIndexScreen::buildModuleSearchData);
    private final GridScrollController listScroll = new GridScrollController();

    private List<ModuleGroup> registeredModules = List.of();
    private EditBox searchBox;
    private String searchQuery = "";
    private ModuleGroup hoveredModule;

    public KTConfigIndexScreen(Screen parent) {
        this(parent, null);
    }

    public KTConfigIndexScreen(Screen parent, String ownerNamespace) {
        super(Component.translatable("gui.kineticcore.config.installed_plugins"));
        this.parent = parent;
        this.ownerNamespace = ownerNamespace;
        moduleModel.setComparator(Comparator.comparing(ModuleGroup::namespace));
        useCanvas(640, 360, 6);
    }

    @Override
    protected void buildUi() {
        List<KTConfigPage> pages = KTConfigApi.pages();
        if (ownerNamespace != null && !ownerNamespace.isBlank()) {
            String prefix = ownerNamespace + ":";
            pages = pages.stream().filter(page -> page.id().startsWith(prefix)).toList();
        }
        registeredModules = groupPages(pages);
        moduleModel.setSource(registeredModules);
        moduleModel.refresh(searchQuery);
        updateScrollRange();

        searchBox = new EditBox(
                font,
                LIST_X,
                48,
                430,
                20,
                Component.translatable("gui.kineticcore.config.search_plugins")
        );
        searchBox.setMaxLength(256);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(this::updateSearch);
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"),
                        ignored -> onClose())
                .bounds(514, 24, 72, 20)
                .build());
    }

    private void updateSearch(String query) {
        searchQuery = query == null ? "" : query;
        moduleModel.refresh(searchQuery);
        listScroll.reset();
        updateScrollRange();
    }

    private void updateScrollRange() {
        listScroll.update(moduleModel.items().size(), VISIBLE_ROWS);
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        GuiTheme.panel(graphics, 42, 18, 556, 330);
        graphics.drawCenteredString(font, title, canvasWidth / 2, 28, 0xFFFFAA00);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "gui.kineticcore.config.module_count",
                        Component.literal(String.valueOf(moduleModel.items().size())).withStyle(ChatFormatting.GREEN),
                        Component.literal(String.valueOf(registeredModules.size())).withStyle(ChatFormatting.YELLOW)
                ),
                canvasWidth / 2,
                72,
                0xFFAAAAAA
        );

        GuiTheme.panelAlt(
                graphics,
                LIST_X - 3,
                LIST_Y - 3,
                LIST_WIDTH + 25,
                LIST_HEIGHT + 6
        );
        renderModuleRows(graphics, mouseX, mouseY);

        GuiTheme.scrollbar(
                listScroll,
                graphics,
                mouseX,
                mouseY,
                SCROLL_X,
                LIST_Y,
                SCROLL_WIDTH,
                LIST_HEIGHT,
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
        renderSearchPlaceholder(graphics, searchBox);
    }

    private void renderSearchPlaceholder(GuiGraphics graphics, EditBox box) {
        if (box == null || !box.visible || !box.getValue().isEmpty() || box.isFocused()) return;
        String text = font.plainSubstrByWidth(
                Component.translatable("gui.kineticcore.config.search_plugins").getString(),
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

    private void renderModuleRows(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ModuleGroup> modules = moduleModel.items();
        hoveredModule = moduleAt(mouseX, mouseY);

        if (modules.isEmpty()) {
            Component message = registeredModules.isEmpty()
                    ? Component.translatable("gui.kineticcore.config.plugins_empty")
                    : Component.translatable("gui.kineticcore.config.no_plugin_results");
            graphics.drawCenteredString(
                    font,
                    message,
                    LIST_X + LIST_WIDTH / 2,
                    LIST_Y + LIST_HEIGHT / 2 - font.lineHeight / 2,
                    0xFFAAAAAA
            );
            return;
        }

        double smoothOffset = listScroll.smoothOffset();
        int first = Math.max(0, (int) Math.floor(smoothOffset));
        int last = Math.min(first + VISIBLE_ROWS + 2, modules.size());

        enableCanvasScissor(
                graphics,
                LIST_X,
                LIST_Y,
                LIST_X + LIST_WIDTH,
                LIST_Y + LIST_HEIGHT
        );
        try {
            for (int index = first; index < last; index++) {
                ModuleGroup module = modules.get(index);
                int y = LIST_Y + (int) Math.round((index - smoothOffset) * ROW_STRIDE);
                boolean hovered = module == hoveredModule;

                GuiTheme.panel(
                        graphics,
                        LIST_X,
                        y,
                        LIST_WIDTH,
                        ROW_HEIGHT,
                        hovered ? 0xFF343434 : 0xFF242424,
                        hovered ? 0xFFFFAA00 : 0xFF555555
                );

                String nameText = GuiTheme.trim(
                        font,
                        module.title().getString(),
                        MODULE_FUNCTION_X - MODULE_NAME_X - 12
                );
                graphics.drawString(
                        font,
                        nameText,
                        MODULE_NAME_X,
                        y + 4,
                        MODULE_NAME_COLOR,
                        false
                );

                String functionText = GuiTheme.trim(
                        font,
                        module.function().getString(),
                        LIST_X + LIST_WIDTH - 9 - MODULE_FUNCTION_X
                );
                if (!functionText.isBlank()) {
                    graphics.drawString(
                            font,
                            functionText,
                            MODULE_FUNCTION_X,
                            y + 4,
                            MODULE_FUNCTION_COLOR,
                            false
                    );
                }

                Component count = Component.translatable(
                        "gui.kineticcore.config.entry_count",
                        Component.literal(String.valueOf(module.entryCount())).withStyle(ChatFormatting.AQUA)
                );
                int countX = LIST_X + LIST_WIDTH - 9 - font.width(count);
                graphics.drawString(font, module.namespace(), LIST_X + 9, y + 16, 0xFF999999, false);
                graphics.drawString(font, count, countX, y + 16, 0xFF999999, false);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private ModuleGroup moduleAt(double mouseX, double mouseY) {
        if (!GuiTheme.hovering(mouseX, mouseY, LIST_X, LIST_Y, LIST_WIDTH, LIST_HEIGHT)) {
            return null;
        }
        double contentY = mouseY - LIST_Y + listScroll.smoothOffset() * ROW_STRIDE;
        int index = (int) Math.floor(contentY / ROW_STRIDE);
        double withinRow = contentY - index * ROW_STRIDE;
        if (withinRow < 0D || withinRow >= ROW_HEIGHT) return null;
        List<ModuleGroup> modules = moduleModel.items();
        return index >= 0 && index < modules.size() ? modules.get(index) : null;
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        boolean clickedWidget = super.canvasMouseClicked(mouseX, mouseY, button);

        if (button == 0 && listScroll.beginDrag(
                mouseX,
                mouseY,
                SCROLL_X,
                LIST_Y,
                SCROLL_WIDTH,
                LIST_HEIGHT,
                18,
                2
        )) {
            return true;
        }

        if (button == 0 && minecraft != null) {
            ModuleGroup selected = moduleAt(mouseX, mouseY);
            if (selected != null) {
                minecraft.setScreen(new KTModuleConfigScreen(
                        this,
                        selected.title(),
                        selected.pages()
                ));
                return true;
            }
        }

        return clickedWidget;
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        return listScroll.drag(mouseY, LIST_Y, LIST_HEIGHT, 18)
                || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return listScroll.release(button)
                || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (GuiTheme.hovering(
                mouseX,
                mouseY,
                LIST_X,
                LIST_Y,
                SCROLL_X + SCROLL_WIDTH - LIST_X,
                LIST_HEIGHT
        ) && listScroll.scroll(delta)) {
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
        if (hoveredModule == null) return;

        List<Component> lines = new ArrayList<>();
        if (hoveredModule.tooltip() != null && !hoveredModule.tooltip().getString().isBlank()) {
            lines.add(hoveredModule.tooltip());
        }
        lines.add(Component.translatable(
                "gui.kineticcore.config.module_summary",
                Component.literal(String.valueOf(hoveredModule.entryCount())).withStyle(ChatFormatting.AQUA)
        ));
        showTooltip(lines, 340);
    }

    private static List<ModuleGroup> groupPages(List<KTConfigPage> pages) {
        Map<String, List<KTConfigPage>> grouped = new LinkedHashMap<>();
        for (KTConfigPage page : pages) {
            String namespace = namespaceOf(page.id());
            grouped.computeIfAbsent(namespace, ignored -> new ArrayList<>()).add(page);
        }

        List<ModuleGroup> result = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<KTConfigPage>> entry : grouped.entrySet()) {
            String namespace = entry.getKey();
            result.add(new ModuleGroup(
                    namespace,
                    KTModuleDisplay.moduleName(namespace),
                    KTModuleDisplay.moduleFunction(namespace),
                    KTModuleDisplay.moduleTooltip(namespace),
                    List.copyOf(entry.getValue())
            ));
        }
        return List.copyOf(result);
    }

    static Component moduleTitle(String namespace) {
        return KTModuleDisplay.moduleName(namespace);
    }

    private static String namespaceOf(String pageId) {
        int separator = pageId.indexOf(':');
        return separator > 0 ? pageId.substring(0, separator) : pageId;
    }

    private static String buildModuleSearchData(ModuleGroup module) {
        StringBuilder data = new StringBuilder(module.namespace())
                .append(' ')
                .append(module.title().getString());
        if (!module.function().getString().isBlank()) {
            data.append(' ').append(module.function().getString());
        }
        if (module.tooltip() != null) {
            data.append(' ').append(module.tooltip().getString());
        }
        for (KTConfigPage page : module.pages()) {
            data.append(' ').append(page.id()).append(' ').append(page.title().getString());
            if (page.description() != null) data.append(' ').append(page.description().getString());
            for (KTConfigEntry<?> entry : page.entries()) {
                data.append(' ').append(entry.id()).append(' ').append(entry.label().getString());
                if (entry.tooltip() != null) data.append(' ').append(entry.tooltip().getString());
            }
        }
        String raw = data.toString();
        return raw + ' ' + KineticSearch.pinyin(raw);
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
