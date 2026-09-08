package dev.xyat.kineticcore.config.client;

import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.gui.ConfigScrollbarTheme;
import dev.xyat.kineticcore.api.client.gui.GridScrollController;
import dev.xyat.kineticcore.api.client.gui.NumericEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class KTConfigListScreen extends ScaledScreen {
    private static final int VISIBLE_ROWS = 8;
    private static final int LIST_Y = 55;
    private static final int ROW_HEIGHT = 30;
    private static final int LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;
    private static final int SCROLL_X = 578;
    private static final int SCROLL_WIDTH = 6;

    private final Screen parent;
    private final boolean integerList;
    private final Consumer<List<?>> resultConsumer;
    private final List<String> values = new ArrayList<>();
    private final List<String> originalValues = new ArrayList<>();
    private final GridScrollController listScroll = new GridScrollController();
    private Component status;

    KTConfigListScreen(
            Screen parent,
            Component title,
            boolean integerList,
            List<?> initialValues,
            Consumer<List<?>> resultConsumer
    ) {
        super(title);
        this.parent = parent;
        this.integerList = integerList;
        this.resultConsumer = resultConsumer;
        for (Object value : initialValues) values.add(String.valueOf(value));
        originalValues.addAll(values);
        configureResponsiveCanvas(640, 360, 6);
    }

    @Override
    protected void initScaled() {
        listScroll.update(values.size(), VISIBLE_ROWS);
        int first = listScroll.offset();
        int last = Math.min(first + VISIBLE_ROWS, values.size());
        for (int index = first; index < last; index++) {
            int capturedIndex = index;
            int local = index - first;
            int y = LIST_Y + local * ROW_HEIGHT;
            EditBox box;
            if (integerList) {
                box = NumericEditBox.integer(font, 124, y, 300, 20, title,
                        true, Integer.MIN_VALUE, Integer.MAX_VALUE);
            } else {
                box = new EditBox(font, 124, y, 300, 20, title);
                box.setMaxLength(32767);
            }
            box.setValue(values.get(index));
            box.setResponder(value -> values.set(capturedIndex, value));
            addRenderableWidget(box);
            addRenderableWidget(Button.builder(Component.literal("↑"), ignored -> move(capturedIndex, -1))
                    .bounds(434, y, 28, 20).build());
            addRenderableWidget(Button.builder(Component.literal("↓"), ignored -> move(capturedIndex, 1))
                    .bounds(468, y, 28, 20).build());
            addRenderableWidget(Button.builder(Component.literal("×"), ignored -> remove(capturedIndex))
                    .bounds(502, y, 34, 20).build());
        }

        int footerY = 325;
        addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.config.add"), ignored -> add())
                .bounds(166, footerY, 92, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.config.back"), ignored -> onClose())
                .bounds(274, footerY, 92, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> finish())
                .bounds(382, footerY, 92, 20).build());
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
        listScroll.update(values.size(), VISIBLE_ROWS);
        status = null;
        rebuildWidgets();
    }

    private void move(int index, int direction) {
        int target = index + direction;
        if (index < 0 || index >= values.size() || target < 0 || target >= values.size()) return;
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

    @Override
    protected void renderScaledBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiRenderUtil.drawStandardPanel(graphics, 20, 12, 600, 342);
        graphics.drawCenteredString(font, title, vWidth / 2, 25, 0xFFFFAA00);
        int first = listScroll.offset();
        int last = Math.min(first + VISIBLE_ROWS, values.size());
        for (int index = first; index < last; index++) {
            int y = LIST_Y + (index - first) * ROW_HEIGHT;
            graphics.drawString(font, Integer.toString(index + 1), 50, y + 6, 0xFFAAAAAA, false);
        }
        if (values.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.kineticcore.config.list_empty"),
                    vWidth / 2, vHeight / 2, 0xFFAAAAAA);
        }
        ConfigScrollbarTheme.render(
                listScroll, graphics, mouseX, mouseY,
                SCROLL_X, LIST_Y, SCROLL_WIDTH, LIST_HEIGHT, 18
        );
        if (status != null) graphics.drawCenteredString(font, status, vWidth / 2, 307, 0xFFFF5555);
    }

    @Override
    protected boolean universalMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && listScroll.beginDrag(
                mouseX, mouseY, SCROLL_X, LIST_Y, SCROLL_WIDTH, LIST_HEIGHT, 18, 2)) {
            rebuildWidgets();
            return true;
        }
        return super.universalMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean universalMouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        int previousOffset = listScroll.offset();
        if (listScroll.drag(mouseY, LIST_Y, LIST_HEIGHT, 18)) {
            if (listScroll.offset() != previousOffset) rebuildWidgets();
            return true;
        }
        return super.universalMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean universalMouseReleased(double mouseX, double mouseY, int button) {
        return listScroll.release(button)
                || super.universalMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean universalMouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= 42 && mouseX <= 586
                && mouseY >= LIST_Y && mouseY < LIST_Y + LIST_HEIGHT
                && listScroll.scroll(delta)) {
            rebuildWidgets();
            return true;
        }
        return super.universalMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
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