package dev.xyat.kineticcore.config.client;

import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.NumericEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class KTConfigListScreen extends KineticScreen {
    private static final int VISIBLE_ROWS = 7;
    private static final int LIST_X = 44;
    private static final int LIST_Y = 80;
    private static final int ROW_HEIGHT = 32;
    private static final int LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;
    private static final int SCROLL_X = 590;
    private static final int SCROLL_WIDTH = 4;
    private static final int EDIT_X = 78;
    private static final int EDIT_WIDTH = 390;
    private static final int ROW_RIGHT = 580;
    private static final long DRAG_SCROLL_INTERVAL_NANOS = 120_000_000L;

    private final Screen parent;
    private final Component description;
    private final boolean integerList;
    private final Consumer<List<?>> resultConsumer;
    private final List<String> values = new ArrayList<>();
    private final List<String> originalValues = new ArrayList<>();
    private final GridScrollController listScroll = new GridScrollController();
    private final List<AbstractWidget> listWidgets = new ArrayList<>();
    private Component status;
    private int draggingIndex = -1;
    private int dragTargetIndex = -1;
    private double dragMouseX;
    private double dragMouseY;
    private double dragGrabOffsetX;
    private double dragGrabOffsetY;
    private long lastDragScrollNanos;

    KTConfigListScreen(
            Screen parent,
            Component title,
            Component description,
            boolean integerList,
            List<?> initialValues,
            Consumer<List<?>> resultConsumer
    ) {
        super(title);
        this.parent = parent;
        this.description = description;
        this.integerList = integerList;
        this.resultConsumer = resultConsumer;
        for (Object value : initialValues) values.add(String.valueOf(value));
        originalValues.addAll(values);
        useCanvas(640, 360, 6);
    }

    @Override
    protected void buildUi() {
        resetScrollableWidgets();
        listWidgets.clear();
        listScroll.update(values.size(), VISIBLE_ROWS);
        for (int index = 0; index < values.size(); index++) {
            int capturedIndex = index;
            int y = LIST_Y + index * ROW_HEIGHT;
            EditBox box;
            if (integerList) {
                box = NumericEditBox.integer(font, EDIT_X, y + 4, EDIT_WIDTH, 20, title,
                        true, Integer.MIN_VALUE, Integer.MAX_VALUE);
            } else {
                box = new EditBox(font, EDIT_X, y + 4, EDIT_WIDTH, 20, title);
                box.setMaxLength(32767);
            }
            box.setValue(values.get(index));
            box.setResponder(value -> values.set(capturedIndex, value));
            addListScrollableWidget(box);
            addListScrollableWidget(Button.builder(Component.translatable("gui.kineticcore.config.move_up"), ignored -> move(capturedIndex, -1))
                    .bounds(476, y + 4, 28, 20).build());
            addListScrollableWidget(Button.builder(Component.translatable("gui.kineticcore.config.move_down"), ignored -> move(capturedIndex, 1))
                    .bounds(510, y + 4, 28, 20).build());
            addListScrollableWidget(Button.builder(Component.translatable("gui.kineticcore.config.remove_short"), ignored -> remove(capturedIndex))
                    .bounds(544, y + 4, 34, 20).build());
        }

        int footerY = 326;
        addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.config.add"), ignored -> add())
                .bounds(166, footerY, 92, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.config.back"), ignored -> onClose())
                .bounds(274, footerY, 92, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> finish())
                .bounds(382, footerY, 92, 20).build());
    }

    private double listPixelOffset() {
        return listScroll.smoothOffset() * ROW_HEIGHT;
    }

    private <T extends AbstractWidget> T addListScrollableWidget(T widget) {
        listWidgets.add(widget);
        return addScrollableWidget(
                widget,
                LIST_X,
                LIST_Y,
                SCROLL_X - 2,
                LIST_Y + LIST_HEIGHT,
                this::listPixelOffset
        );
    }

    private void setListWidgetsVisible(boolean visible) {
        for (AbstractWidget widget : listWidgets) {
            widget.visible = visible;
        }
    }

    private void add() {
        values.add(integerList ? "0" : "");
        listScroll.update(values.size(), VISIBLE_ROWS);
        listScroll.setOffset(Math.max(0, values.size() - VISIBLE_ROWS));
        status = null;
        rebuildWidgets();
    }

    private void remove(int index) {
        if (index >= 0 && index < values.size()) values.remove(index);
        clearDragState();
        listScroll.update(values.size(), VISIBLE_ROWS);
        status = null;
        rebuildWidgets();
    }

    private void move(int index, int direction) {
        int target = index + direction;
        if (index < 0 || index >= values.size() || target < 0 || target >= values.size()) return;
        moveTo(index, target);
    }

    private void moveTo(int index, int target) {
        if (index < 0 || index >= values.size() || target < 0 || target >= values.size() || index == target) return;
        String value = values.remove(index);
        values.add(target, value);
        ensureVisible(target);
        status = null;
        rebuildWidgets();
    }

    private void ensureVisible(int index) {
        listScroll.update(values.size(), VISIBLE_ROWS);
        if (index < listScroll.offset()) {
            listScroll.setOffset(index);
        } else if (index >= listScroll.offset() + VISIBLE_ROWS) {
            listScroll.setOffset(index - VISIBLE_ROWS + 1);
        }
    }

    private int rowAt(double mouseY) {
        if (values.isEmpty()) return -1;
        double clampedY = Math.max(LIST_Y, Math.min(mouseY, LIST_Y + LIST_HEIGHT - 1));
        int index = (int) Math.floor((clampedY - LIST_Y + listPixelOffset()) / ROW_HEIGHT);
        return Math.max(0, Math.min(index, values.size() - 1));
    }

    private boolean inList(double mouseX, double mouseY) {
        return mouseX >= LIST_X && mouseX < ROW_RIGHT
                && mouseY >= LIST_Y && mouseY < LIST_Y + LIST_HEIGHT;
    }

    private void updateDragTarget(double mouseX, double mouseY) {
        if (draggingIndex < 0) return;

        dragMouseX = mouseX;
        dragMouseY = mouseY;
        long now = System.nanoTime();
        if (now - lastDragScrollNanos >= DRAG_SCROLL_INTERVAL_NANOS) {
            if (mouseY <= LIST_Y + 10 && listScroll.offset() > 0) {
                listScroll.setOffset(listScroll.offset() - 1);
                lastDragScrollNanos = now;
            } else if (mouseY >= LIST_Y + LIST_HEIGHT - 10 && listScroll.offset() < listScroll.maxOffset()) {
                listScroll.setOffset(listScroll.offset() + 1);
                lastDragScrollNanos = now;
            }
        }
        double ghostCenterY = mouseY - dragGrabOffsetY + ROW_HEIGHT / 2.0;
        dragTargetIndex = rowAt(ghostCenterY);
    }

    private int previewPosition(int index) {
        if (index == draggingIndex) return dragTargetIndex;
        if (draggingIndex < dragTargetIndex && index > draggingIndex && index <= dragTargetIndex) {
            return index - 1;
        }
        if (draggingIndex > dragTargetIndex && index >= dragTargetIndex && index < draggingIndex) {
            return index + 1;
        }
        return index;
    }

    private void clearDragState() {
        setListWidgetsVisible(true);
        draggingIndex = -1;
        dragTargetIndex = -1;
        dragMouseX = 0.0;
        dragMouseY = 0.0;
        dragGrabOffsetX = 0.0;
        dragGrabOffsetY = 0.0;
        lastDragScrollNanos = 0L;
    }

    private void finish() {
        if (!integerList) {
            resultConsumer.accept(new ArrayList<>(values));
            Minecraft.getInstance().setScreen(parent);
            return;
        }

        List<Integer> parsed = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                parsed.add(Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
                status = Component.translatable("gui.kineticcore.config.list_invalid");
                return;
            }
        }
        resultConsumer.accept(parsed);
        Minecraft.getInstance().setScreen(parent);
    }

    private void renderSnapshotButton(GuiGraphics graphics, int x, int y, int width, Component label, boolean lifted) {
        int border = lifted ? GuiTheme.current().accentHover() : GuiTheme.current().border();
        GuiTheme.panel(
                graphics,
                x,
                y,
                width,
                20,
                GuiTheme.current().panelAlt(),
                border
        );
        graphics.drawCenteredString(font, label, x + width / 2, y + 6, GuiTheme.current().text());
    }

    private void renderRowSnapshot(
            GuiGraphics graphics,
            int valueIndex,
            int rank,
            int rowX,
            int y,
            boolean lifted
    ) {
        int dx = rowX - LIST_X;
        int rowWidth = ROW_RIGHT - LIST_X - 4;
        int border = lifted ? GuiTheme.current().accentHover() : GuiTheme.current().border();
        int background = lifted ? GuiTheme.current().panelAlt() : GuiTheme.current().panel();
        GuiTheme.panel(graphics, rowX + 2, y + 1, rowWidth, ROW_HEIGHT - 2, background, border);
        graphics.drawString(
                font,
                Integer.toString(rank + 1),
                52 + dx,
                y + 10,
                rank == 0 ? GuiTheme.current().accentHover() : GuiTheme.current().mutedText(),
                false
        );
        GuiTheme.panel(
                graphics,
                EDIT_X + dx,
                y + 4,
                EDIT_WIDTH,
                20,
                GuiTheme.current().field(),
                lifted ? GuiTheme.current().accentHover() : GuiTheme.current().border()
        );
        graphics.drawString(
                font,
                GuiTheme.trim(font, values.get(valueIndex), EDIT_WIDTH - 8),
                EDIT_X + dx + 4,
                y + 10,
                GuiTheme.current().text(),
                false
        );
        renderSnapshotButton(
                graphics, 476 + dx, y + 4, 28,
                Component.translatable("gui.kineticcore.config.move_up"), lifted
        );
        renderSnapshotButton(
                graphics, 510 + dx, y + 4, 28,
                Component.translatable("gui.kineticcore.config.move_down"), lifted
        );
        renderSnapshotButton(
                graphics, 544 + dx, y + 4, 34,
                Component.translatable("gui.kineticcore.config.remove_short"), lifted
        );
    }

    private void renderDragPreview(GuiGraphics graphics) {
        if (draggingIndex < 0 || dragTargetIndex < 0) return;
        double pixelOffset = listPixelOffset();
        int offsetPixels = (int) Math.round(pixelOffset);

        enableCanvasScissor(graphics, LIST_X, LIST_Y, SCROLL_X - 2, LIST_Y + LIST_HEIGHT);
        try {
            for (int index = 0; index < values.size(); index++) {
                if (index == draggingIndex) continue;
                int previewIndex = previewPosition(index);
                int y = LIST_Y + previewIndex * ROW_HEIGHT - offsetPixels;
                if (y + ROW_HEIGHT <= LIST_Y || y >= LIST_Y + LIST_HEIGHT) continue;
                renderRowSnapshot(graphics, index, previewIndex, LIST_X, y, false);
            }

            int gapY = LIST_Y + dragTargetIndex * ROW_HEIGHT - offsetPixels;
            if (gapY + ROW_HEIGHT > LIST_Y && gapY < LIST_Y + LIST_HEIGHT) {
                graphics.fill(
                        LIST_X + 2,
                        gapY + 2,
                        ROW_RIGHT - 2,
                        gapY + ROW_HEIGHT - 2,
                        GuiTheme.current().field()
                );
                graphics.renderOutline(
                        LIST_X + 2,
                        gapY + 1,
                        ROW_RIGHT - LIST_X - 4,
                        ROW_HEIGHT - 2,
                        GuiTheme.current().accentHover()
                );
            }
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 20, 12, 600, 342);
        graphics.drawCenteredString(font, title, canvasWidth / 2, 23, GuiTheme.current().accentHover());

        if (description != null) {
            List<FormattedCharSequence> lines = font.split(description, 548);
            int count = Math.min(3, lines.size());
            int firstY = count <= 1 ? 47 : count == 2 ? 41 : 35;
            for (int index = 0; index < count; index++) {
                graphics.drawCenteredString(
                        font,
                        lines.get(index),
                        canvasWidth / 2,
                        firstY + index * 11,
                        GuiTheme.current().text()
                );
            }
        }

        if (draggingIndex >= 0) {
            renderDragPreview(graphics);
        } else {
            double pixelOffset = listPixelOffset();
            enableCanvasScissor(graphics, LIST_X, LIST_Y, SCROLL_X - 2, LIST_Y + LIST_HEIGHT);
            try {
                int first = Math.max(0, (int) Math.floor(listScroll.smoothOffset()));
                int last = Math.min(first + VISIBLE_ROWS + 2, values.size());
                for (int index = first; index < last; index++) {
                    int y = LIST_Y + index * ROW_HEIGHT - (int) Math.round(pixelOffset);
                    graphics.drawString(
                            font,
                            Integer.toString(index + 1),
                            52,
                            y + 10,
                            index == 0 ? GuiTheme.current().accentHover() : GuiTheme.current().mutedText(),
                            false
                    );
                }
            } finally {
                graphics.disableScissor();
            }
        }

        if (values.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.kineticcore.config.list_empty"),
                    canvasWidth / 2, LIST_Y + LIST_HEIGHT / 2 - 4, GuiTheme.current().mutedText());
        }
        GuiTheme.scrollbar(
                listScroll, graphics, mouseX, mouseY,
                SCROLL_X, LIST_Y, SCROLL_WIDTH, LIST_HEIGHT, 18
        );
        if (status != null) {
            graphics.drawCenteredString(font, status, canvasWidth / 2, 309, GuiTheme.current().danger());
        }
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (draggingIndex < 0 || draggingIndex >= values.size()) return;

        int ghostX = (int) Math.round(dragMouseX - dragGrabOffsetX);
        ghostX = Math.max(28, Math.min(72, ghostX));
        int ghostY = (int) Math.round(dragMouseY - dragGrabOffsetY);
        ghostY = Math.max(45, Math.min(309, ghostY));
        int rowWidth = ROW_RIGHT - LIST_X - 4;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 250.0F);
        try {
            graphics.fill(
                    ghostX + 5,
                    ghostY + 5,
                    ghostX + 2 + rowWidth + 5,
                    ghostY + ROW_HEIGHT + 3,
                    GuiTheme.current().shadow()
            );
            renderRowSnapshot(graphics, draggingIndex, dragTargetIndex, ghostX, ghostY, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && listScroll.beginDrag(
                mouseX, mouseY, SCROLL_X, LIST_Y, SCROLL_WIDTH, LIST_HEIGHT, 18, 2)) {
            return true;
        }
        if (button == 0 && Screen.hasControlDown() && inList(mouseX, mouseY)) {
            int index = rowAt(mouseY);
            if (index >= 0) {
                int sourceY = LIST_Y + index * ROW_HEIGHT - (int) Math.round(listPixelOffset());
                draggingIndex = index;
                dragTargetIndex = index;
                dragMouseX = mouseX;
                dragMouseY = mouseY;
                dragGrabOffsetX = mouseX - LIST_X;
                dragGrabOffsetY = mouseY - sourceY;
                lastDragScrollNanos = System.nanoTime();
                setFocused(null);
                setListWidgetsVisible(false);
                return true;
            }
        }
        return super.canvasMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingIndex >= 0) {
            updateDragTarget(mouseX, mouseY);
            return true;
        }
        if (listScroll.drag(mouseY, LIST_Y, LIST_HEIGHT, 18)) {
            return true;
        }
        return super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingIndex >= 0) {
            int from = draggingIndex;
            int target = dragTargetIndex;
            clearDragState();
            if (target >= 0 && target != from) {
                moveTo(from, target);
            }
            return true;
        }
        return listScroll.release(button)
                || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= LIST_X && mouseX <= SCROLL_X - 4
                && mouseY >= LIST_Y && mouseY < LIST_Y + LIST_HEIGHT
                && listScroll.scroll(delta)) {
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        clearDragState();
        Minecraft client = Minecraft.getInstance();
        if (values.equals(originalValues)) {
            client.setScreen(parent);
            return;
        }

        client.setScreen(new ConfirmScreen(
                shouldApply -> {
                    if (!shouldApply) {
                        client.setScreen(parent);
                        return;
                    }
                    client.setScreen(this);
                    finish();
                },
                Component.translatable("gui.kineticcore.config.unsaved_action.title"),
                Component.translatable("gui.kineticcore.config.unsaved_list.message")
        ));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
