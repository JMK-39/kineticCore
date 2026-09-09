package dev.xyat.kineticcore.feature.firstjoin.client;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.config.client.KTServerConfigClient;
import dev.xyat.kineticcore.feature.firstjoin.config.PlayerConfig;
import dev.xyat.kineticcore.feature.firstjoin.config.PlayerConfigGui;
import dev.xyat.kineticcore.feature.worldinit.config.WorldInitConfigGui;
import dev.xyat.kineticcore.feature.worldinit.config.WorldInitConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public final class FirstJoinCommandListScreen extends KineticScreen {
    private static final int PANEL_X = 24;
    private static final int PANEL_Y = 18;
    private static final int PANEL_W = 592;
    private static final int PANEL_H = 324;
    private static final int LIST_X = 44;
    private static final int LIST_Y = 58;
    private static final int LIST_W = 538;
    private static final int ROW_H = 24;
    private static final int VISIBLE_ROWS = 10;
    private static final int LIST_H = ROW_H * VISIBLE_ROWS;
    private static final int SCROLL_X = LIST_X + LIST_W + 6;
    private static final int SCROLL_W = 4;
    private static final int MOVE_W = 34;
    private static final int DELETE_W = 54;
    private static final int BUTTON_GAP = 3;

    private final Screen parent;
    private final Supplier<List<String>> commandGetter;
    private final Consumer<List<String>> commandSetter;
    private final String serverPageId;
    private final String serverEntryId;
    private final Component addEditorTitle;
    private final Component editEditorTitle;
    private final Component emptyMessage;
    private final Component savedMessage;
    private final Component deletedMessage;
    private final Component saveFailedMessage;
    private final boolean showPlayerVariables;
    private final GridScrollController scroll = new GridScrollController();
    private final List<Button> upButtons = new ArrayList<>();
    private final List<Button> downButtons = new ArrayList<>();
    private final List<Button> deleteButtons = new ArrayList<>();
    private List<Component> deferredTooltip;

    public FirstJoinCommandListScreen(Screen parent) {
        this(
                parent,
                Component.translatable("gui.kineticcore.firstjoin.command_list.title"),
                Component.translatable("gui.kineticcore.firstjoin.command_edit.add_title"),
                Component.translatable("gui.kineticcore.firstjoin.command_edit.edit_title"),
                Component.translatable("gui.kineticcore.firstjoin.command_list.empty"),
                Component.translatable("msg.kineticcore.firstjoin.command_edit.saved"),
                Component.translatable("msg.kineticcore.firstjoin.command_list.deleted"),
                Component.translatable("msg.kineticcore.firstjoin.command_list.save_failed"),
                () -> PlayerConfig.firstJoinCommands,
                value -> PlayerConfig.firstJoinCommands = value,
                PlayerConfigGui.PAGE_ID,
                "commands",
                true
        );
    }

    public static FirstJoinCommandListScreen forWorldInit(Screen parent) {
        return new FirstJoinCommandListScreen(
                parent,
                Component.translatable("gui.kineticcore.worldinit.command_list.title"),
                Component.translatable("gui.kineticcore.worldinit.command_edit.add_title"),
                Component.translatable("gui.kineticcore.worldinit.command_edit.edit_title"),
                Component.translatable("gui.kineticcore.worldinit.command_list.empty"),
                Component.translatable("msg.kineticcore.worldinit.command_edit.saved"),
                Component.translatable("msg.kineticcore.worldinit.command_list.deleted"),
                Component.translatable("msg.kineticcore.worldinit.command_list.save_failed"),
                () -> WorldInitConfig.worldInitCommands,
                value -> WorldInitConfig.worldInitCommands = value,
                WorldInitConfigGui.PAGE_ID,
                "commands",
                false
        );
    }

    private FirstJoinCommandListScreen(
            Screen parent,
            Component title,
            Component addEditorTitle,
            Component editEditorTitle,
            Component emptyMessage,
            Component savedMessage,
            Component deletedMessage,
            Component saveFailedMessage,
            Supplier<List<String>> commandGetter,
            Consumer<List<String>> commandSetter,
            String serverPageId,
            String serverEntryId,
            boolean showPlayerVariables
    ) {
        super(title);
        this.parent = parent;
        this.addEditorTitle = addEditorTitle;
        this.editEditorTitle = editEditorTitle;
        this.emptyMessage = emptyMessage;
        this.savedMessage = savedMessage;
        this.deletedMessage = deletedMessage;
        this.saveFailedMessage = saveFailedMessage;
        this.commandGetter = commandGetter;
        this.commandSetter = commandSetter;
        this.serverPageId = serverPageId;
        this.serverEntryId = serverEntryId;
        this.commandSetter.accept(KTServerConfigClient.getStringList(serverPageId, serverEntryId, commandGetter.get()));
        this.showPlayerVariables = showPlayerVariables;
        useCanvas(640F, 360F, 6);
    }

    @Override
    protected void buildUi() {
        resetScrollableWidgets();
        upButtons.clear();
        downButtons.clear();
        deleteButtons.clear();
        updateScrollRange();

        int deleteX = LIST_X + LIST_W - DELETE_W - 4;
        int downX = deleteX - BUTTON_GAP - MOVE_W;
        int upX = downX - BUTTON_GAP - MOVE_W;
        List<String> commands = currentCommands();
        for (int index = 0; index < commands.size(); index++) {
            final int commandIndex = index;
            int y = LIST_Y + index * ROW_H + 2;
            upButtons.add(addRowWidget(Button.builder(
                            Component.literal("↑"),
                            button -> moveIndex(commandIndex, -1))
                    .bounds(upX, y, MOVE_W, ROW_H - 6)
                    .build()));
            downButtons.add(addRowWidget(Button.builder(
                            Component.literal("↓"),
                            button -> moveIndex(commandIndex, 1))
                    .bounds(downX, y, MOVE_W, ROW_H - 6)
                    .build()));
            deleteButtons.add(addRowWidget(Button.builder(
                            Component.translatable("gui.kineticcore.firstjoin.command_list.delete"),
                            button -> deleteCommand(commandIndex))
                    .bounds(deleteX, y, DELETE_W, ROW_H - 6)
                    .build()));
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.firstjoin.command_list.add"),
                        button -> openEditor(-1))
                .bounds(44, 314, 110, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.firstjoin.command_list.back"),
                        button -> closeToParent())
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

    List<String> currentCommands() {
        List<String> commands = commandGetter.get();
        return commands == null ? List.of() : commands;
    }

    Component editorTitle(int editingIndex) {
        return editingIndex >= 0 ? editEditorTitle : addEditorTitle;
    }

    boolean showPlayerVariables() {
        return showPlayerVariables;
    }

    void saveEditedCommand(int editingIndex, String command) {
        List<String> updated = new ArrayList<>(currentCommands());
        if (editingIndex >= 0 && editingIndex < updated.size()) {
            updated.set(editingIndex, command);
        } else {
            updated.add(command);
        }
        persist(updated);
        refreshAfterEdit();
        GuiOverlay.toast(savedMessage);
    }

    void refreshAfterEdit() {
        updateScrollRange();
        int lastIndex = Math.max(0, currentCommands().size() - 1);
        if (lastIndex >= scroll.smoothOffset() + VISIBLE_ROWS) {
            scroll.setOffset(lastIndex - VISIBLE_ROWS + 1);
        }
        updateRowButtons();
    }

    Component saveFailedMessage() {
        return saveFailedMessage;
    }

    private void updateScrollRange() {
        scroll.update(currentCommands().size(), VISIBLE_ROWS);
    }

    private void updateRowButtons() {
        int size = currentCommands().size();
        int buttonCount = Math.min(size, Math.min(upButtons.size(), Math.min(downButtons.size(), deleteButtons.size())));
        for (int index = 0; index < upButtons.size(); index++) {
            boolean visible = index < buttonCount;
            upButtons.get(index).visible = visible;
            downButtons.get(index).visible = visible;
            deleteButtons.get(index).visible = visible;
            if (visible) {
                upButtons.get(index).active = index > 0;
                downButtons.get(index).active = index < size - 1;
                deleteButtons.get(index).active = true;
            }
        }
    }

    private void moveIndex(int index, int direction) {
        int target = index + direction;
        List<String> commands = currentCommands();
        if (index < 0 || index >= commands.size() || target < 0 || target >= commands.size()) return;
        try {
            List<String> updated = new ArrayList<>(commands);
            String command = updated.remove(index);
            updated.add(target, command);
            persist(updated);
            if (target < scroll.smoothOffset()) scroll.setOffset(target);
            if (target >= scroll.smoothOffset() + VISIBLE_ROWS) scroll.setOffset(target - VISIBLE_ROWS + 1);
            updateRowButtons();
        } catch (Throwable throwable) {
            GuiOverlay.toast(saveFailedMessage);
        }
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        deferredTooltip = null;
        updateRowButtons();
        graphics.fillGradient(0, 0, canvasWidth, canvasHeight, 0xFF171717, 0xFF0E0E0E);
        GuiTheme.panel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        GuiTheme.panelAlt(graphics, LIST_X - 4, LIST_Y - 4, LIST_W + 8, LIST_H + 8);
        graphics.drawCenteredString(font, title, canvasWidth / 2, 30, 0xFFFFFF);

        renderRows(graphics, mouseX, mouseY);
        GuiTheme.scrollbar(scroll, graphics, mouseX, mouseY, SCROLL_X, LIST_Y, SCROLL_W, LIST_H, 18);

        if (currentCommands().isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    emptyMessage,
                    LIST_X + LIST_W / 2,
                    LIST_Y + LIST_H / 2,
                    0xFFFFFF
            );
        }
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> commands = currentCommands();
        scroll.update(commands.size(), VISIBLE_ROWS);
        double smoothOffset = scroll.smoothOffset();
        int first = Math.max(0, (int) Math.floor(smoothOffset));
        int end = Math.min(commands.size(), first + VISIBLE_ROWS + 2);
        int actionWidth = MOVE_W * 2 + DELETE_W + BUTTON_GAP * 2 + 12;

        enableCanvasScissor(graphics, LIST_X, LIST_Y, LIST_X + LIST_W, LIST_Y + LIST_H);
        try {
            for (int index = first; index < end; index++) {
                int y = LIST_Y + (int) Math.round((index - smoothOffset) * ROW_H);
                boolean hovered = mouseX >= LIST_X
                        && mouseX < LIST_X + LIST_W - actionWidth
                        && mouseY >= LIST_Y
                        && mouseY < LIST_Y + LIST_H
                        && mouseY >= y
                        && mouseY < y + ROW_H - 2;
                graphics.fill(LIST_X, y, LIST_X + LIST_W, y + ROW_H - 2, index % 2 == 0 ? 0xCC181818 : 0xCC111111);
                graphics.renderOutline(LIST_X, y, LIST_W, ROW_H - 2, hovered ? GuiTheme.current().accentHover() : 0xFF555555);

                String display = displayCommand(commands.get(index));
                int commandWidth = LIST_W - actionWidth - 14;
                graphics.drawString(
                        font,
                        GuiTheme.trim(font, display, commandWidth),
                        LIST_X + 7,
                        y + 7,
                        0xFFFFFF,
                        false
                );

                if (hovered) {
                    deferredTooltip = List.of(
                            Component.literal(display),
                            Component.translatable("gui.kineticcore.firstjoin.command_list.edit_hint")
                    );
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private String displayCommand(String command) {
        if (command == null) return "/";
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private boolean inList(double mouseX, double mouseY) {
        return mouseX >= LIST_X && mouseX < LIST_X + LIST_W
                && mouseY >= LIST_Y && mouseY < LIST_Y + LIST_H;
    }

    private int rowIndex(double mouseY) {
        if (mouseY < LIST_Y || mouseY >= LIST_Y + LIST_H) return -1;
        double contentY = mouseY - LIST_Y + scroll.smoothOffset() * ROW_H;
        int index = (int) Math.floor(contentY / ROW_H);
        return index >= 0 && index < currentCommands().size() ? index : -1;
    }

    private void openEditor(int index) {
        if (minecraft == null) return;
        minecraft.setScreen(new FirstJoinCommandEditScreen(this, index));
    }

    private void deleteCommand(int index) {
        List<String> commands = currentCommands();
        if (index < 0 || index >= commands.size()) return;
        try {
            List<String> updated = new ArrayList<>(commands);
            updated.remove(index);
            persist(updated);
            updateScrollRange();
            updateRowButtons();
            GuiOverlay.toast(deletedMessage);
        } catch (Throwable throwable) {
            GuiOverlay.toast(saveFailedMessage);
        }
    }

    private void persist(List<String> updated) {
        List<String> copy = new ArrayList<>(updated);
        commandSetter.accept(copy);
        if (!KTServerConfigClient.savePartial(serverPageId, Map.of(serverEntryId, copy))) {
            throw new IllegalStateException("Server config is not editable");
        }
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (super.canvasMouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && scroll.beginDrag(mouseX, mouseY, SCROLL_X, LIST_Y, SCROLL_W, LIST_H, 18, 2)) return true;
        if (button == 0 && inList(mouseX, mouseY)) {
            int index = rowIndex(mouseY);
            if (index >= 0) {
                openEditor(index);
                return true;
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
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        if (deferredTooltip != null) {
            showTooltip(deferredTooltip);
        }
    }

    private void closeToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
