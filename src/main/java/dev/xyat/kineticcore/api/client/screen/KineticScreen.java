package dev.xyat.kineticcore.api.client.screen;

import dev.xyat.kineticcore.api.client.layout.GuiLayout;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

public abstract class KineticScreen extends Screen {
    private enum CanvasMode {
        FIT,
        FLUID
    }

    protected int canvasWidth;
    protected int canvasHeight;
    protected float canvasScale;
    protected int canvasX;
    protected int canvasY;

    protected float scaleMultiplier = 1f;
    protected float minScale = 0.1f;
    protected float maxScale = Float.MAX_VALUE;
    protected boolean renderRenderablesOnly;

    private float designWidth = 640f;
    private float designHeight = 360f;
    private int safeMargin;
    private CanvasMode canvasMode = CanvasMode.FIT;
    private GuiLayout.SafeArea safeArea = GuiLayout.SafeArea.of(1, 1, 0);
    private GuiLayout.Metrics metrics = GuiLayout.measure(1, 1, 640, 360);
    private final GuiOverlay overlays = new GuiOverlay();
    private final List<ScrollViewportWidget> scrollViewportWidgets = new ArrayList<>();
    private GuiSession.DraftSession draftSession = new GuiSession.DraftSession();

    private record ScrollViewportKey(int left, int top, int right, int bottom) {
    }

    private record ScrollViewportWidget(
            AbstractWidget widget,
            int baseY,
            int left,
            int top,
            int right,
            int bottom,
            DoubleSupplier pixelOffset
    ) {
        ScrollViewportKey key() {
            return new ScrollViewportKey(left, top, right, bottom);
        }
    }

    protected KineticScreen(Component title) {
        super(title);
    }

    protected final void useCanvas(float designWidth, float designHeight, int safeMargin) {
        this.designWidth = Math.max(1f, designWidth);
        this.designHeight = Math.max(1f, designHeight);
        this.safeMargin = Math.max(0, safeMargin);
        this.canvasMode = CanvasMode.FIT;
    }

    protected final void useFluidCanvas(float preferredWidth, float preferredHeight, int safeMargin) {
        this.designWidth = Math.max(1f, preferredWidth);
        this.designHeight = Math.max(1f, preferredHeight);
        this.safeMargin = Math.max(0, safeMargin);
        this.canvasMode = CanvasMode.FLUID;
    }

    protected final GuiLayout.SafeArea safeArea() {
        return safeArea;
    }

    protected final GuiLayout.Metrics layout() {
        return metrics;
    }

    protected final GuiLayout.Level layoutLevel() {
        return metrics.level();
    }

    protected final boolean isPortraitLayout() {
        return metrics.isPortrait();
    }

    protected final boolean isUltrawideLayout() {
        return metrics.isUltrawide();
    }

    protected final boolean isCompactLayout() {
        return metrics.isCompact();
    }

    protected final GuiOverlay overlays() {
        return overlays;
    }

    protected final void resetScrollableWidgets() {
        scrollViewportWidgets.clear();
    }

    protected final <T extends AbstractWidget> T addScrollableWidget(
            T widget,
            int left,
            int top,
            int right,
            int bottom,
            DoubleSupplier pixelOffset
    ) {
        addRenderableWidget(widget);
        scrollViewportWidgets.add(new ScrollViewportWidget(
                widget,
                widget.getY(),
                left,
                top,
                right,
                bottom,
                pixelOffset
        ));
        return widget;
    }

    private void updateScrollableWidgetPositions() {
        Map<ScrollViewportKey, Double> offsets = new HashMap<>();
        for (ScrollViewportWidget viewportWidget : scrollViewportWidgets) {
            double offset = offsets.computeIfAbsent(
                    viewportWidget.key(),
                    ignored -> Math.max(0D, viewportWidget.pixelOffset().getAsDouble())
            );
            viewportWidget.widget().setY(viewportWidget.baseY() - (int) Math.round(offset));
        }
    }

    private void renderScrollableWidgets(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        for (ScrollViewportWidget viewportWidget : scrollViewportWidgets) {
            AbstractWidget widget = viewportWidget.widget();
            if (!widget.visible) continue;
            int widgetTop = widget.getY();
            int widgetBottom = widgetTop + widget.getHeight();
            if (widgetBottom <= viewportWidget.top() || widgetTop >= viewportWidget.bottom()) continue;

            enableCanvasScissor(
                    graphics,
                    viewportWidget.left(),
                    viewportWidget.top(),
                    viewportWidget.right(),
                    viewportWidget.bottom()
            );
            try {
                widget.render(graphics, mouseX, mouseY, partialTick);
            } finally {
                graphics.disableScissor();
            }
        }
    }

    private List<AbstractWidget> hideScrollableWidgetsOutsideViewport(double mouseX, double mouseY) {
        List<AbstractWidget> hidden = new ArrayList<>();
        for (ScrollViewportWidget viewportWidget : scrollViewportWidgets) {
            AbstractWidget widget = viewportWidget.widget();
            if (!widget.visible) continue;
            boolean insideViewport = mouseX >= viewportWidget.left()
                    && mouseX < viewportWidget.right()
                    && mouseY >= viewportWidget.top()
                    && mouseY < viewportWidget.bottom();
            if (!insideViewport) {
                widget.visible = false;
                hidden.add(widget);
            }
        }
        return hidden;
    }

    private void restoreScrollableWidgetVisibility(List<AbstractWidget> widgets) {
        for (AbstractWidget widget : widgets) {
            widget.visible = true;
        }
    }


    protected final <T> void configureDraft(java.util.function.Supplier<T> capture, java.util.function.Consumer<T> restore) {
        draftSession.configureDraft(this, capture, restore, false);
    }

    protected final <T> void configureStandaloneDraft(java.util.function.Supplier<T> capture, java.util.function.Consumer<T> restore) {
        draftSession.configureDraft(this, capture, restore, true);
    }







    protected final void commitDraft() {
        draftSession.commitBaseline(this);
    }

    protected final void discardDraft() {
        draftSession.discardToBaseline();
    }

    protected final boolean hasUnsavedEdits() {
        return draftSession.isDirty();
    }


    protected final void showTooltip(Component component) {
        overlays.tooltip(component);
    }

    protected final void showTooltip(List<Component> lines) {
        overlays.tooltip(lines);
    }

    protected final void showTooltip(Component component, int maxWidth) {
        overlays.tooltip(component, maxWidth);
    }

    protected final void showTooltip(List<Component> lines, int maxWidth) {
        overlays.tooltip(lines, maxWidth);
    }

    protected final void showItemTooltip(ItemStack stack) {
        overlays.itemTooltip(stack);
    }

    protected final void openContextMenu(double virtualX, double virtualY, List<GuiOverlay.MenuItem> items) {
        overlays.openMenu(toScreenX(virtualX), toScreenY(virtualY), items);
    }

    protected final void openDialog(
            Component title,
            Component message,
            Component confirmText,
            Component cancelText,
            Runnable onConfirm,
            Runnable onCancel
    ) {
        overlays.openDialog(title, message, confirmText, cancelText, onConfirm, onCancel);
    }

    protected final KineticWidgets.Dropdown dropdown(
            int x,
            int y,
            int width,
            int height,
            List<Component> options,
            int selectedIndex,
            Consumer<Integer> responder
    ) {
        KineticWidgets.Dropdown dropdown = new KineticWidgets.Dropdown(
                x, y, width, height, options, selectedIndex, responder, control -> {
                    List<GuiOverlay.MenuItem> entries = new ArrayList<>();
                    List<Component> values = control.options();
                    for (int index = 0; index < values.size(); index++) {
                        int optionIndex = index;
                        entries.add(GuiOverlay.MenuItem.action(values.get(index), () -> control.choose(optionIndex)));
                    }
                    openContextMenu(x, y + height, entries);
                }
        );
        return dropdown;
    }

    @Override
    protected final void init() {
        super.init();
        updateMetrics();
        clearWidgets();
        buildUi();
    }

    protected abstract void buildUi();

    private void updateMetrics() {
        safeArea = GuiLayout.SafeArea.of(width, height, safeMargin);
        metrics = GuiLayout.measure(safeArea.width(), safeArea.height(), designWidth, designHeight);
        if (canvasMode == CanvasMode.FLUID) updateFluidMetrics();
        else updateFixedMetrics();
    }

    private void updateFixedMetrics() {
        float fitScale = Math.max(0.0001f, metrics.fitScale());
        float requested = fitScale * Math.max(0.0001f, scaleMultiplier);
        float lower = Math.max(0.0001f, minScale);
        float upper = Math.max(lower, maxScale);
        canvasScale = Math.min(fitScale, Math.max(lower, Math.min(requested, upper)));
        canvasWidth = Math.max(1, Math.round(designWidth));
        canvasHeight = Math.max(1, Math.round(designHeight));
        canvasX = safeArea.left() + Math.round((safeArea.width() - designWidth * canvasScale) / 2f);
        canvasY = safeArea.top() + Math.round((safeArea.height() - designHeight * canvasScale) / 2f);
    }

    private void updateFluidMetrics() {
        float lower = Math.max(0.0001f, minScale);
        float upper = Math.max(lower, maxScale);
        canvasScale = Math.max(lower, Math.min(Math.max(0.0001f, scaleMultiplier), upper));
        canvasX = safeArea.left();
        canvasY = safeArea.top();
        canvasWidth = Math.max(1, (int) Math.floor(safeArea.width() / canvasScale));
        canvasHeight = Math.max(1, (int) Math.floor(safeArea.height() / canvasScale));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        overlays.beginFrame();
        int virtualMouseX = (int) Math.floor(toVirtualX(mouseX));
        int virtualMouseY = (int) Math.floor(toVirtualY(mouseY));

        graphics.pose().pushPose();
        graphics.pose().translate(canvasX, canvasY, 0);
        graphics.pose().scale(canvasScale, canvasScale, 1f);
        try {
            updateScrollableWidgetPositions();
            renderCanvasBackground(graphics, virtualMouseX, virtualMouseY, partialTick);

            List<AbstractWidget> temporarilyHidden = new ArrayList<>();
            for (ScrollViewportWidget viewportWidget : scrollViewportWidgets) {
                AbstractWidget widget = viewportWidget.widget();
                if (widget.visible) {
                    widget.visible = false;
                    temporarilyHidden.add(widget);
                }
            }
            try {
                if (renderRenderablesOnly) {
                    for (Renderable renderable : renderables) {
                        renderable.render(graphics, virtualMouseX, virtualMouseY, partialTick);
                    }
                } else {
                    super.render(graphics, virtualMouseX, virtualMouseY, partialTick);
                }
            } finally {
                for (AbstractWidget widget : temporarilyHidden) {
                    widget.visible = true;
                }
            }

            renderScrollableWidgets(graphics, virtualMouseX, virtualMouseY, partialTick);
            renderCanvasForeground(graphics, virtualMouseX, virtualMouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }

        renderTooltips(graphics, virtualMouseX, virtualMouseY, mouseX, mouseY);
        renderScreenOverlay(graphics, virtualMouseX, virtualMouseY, mouseX, mouseY, partialTick);
        overlays.render(graphics, font, width, height, mouseX, mouseY);
    }

    protected void renderCanvasBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    protected void renderCanvasForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    protected void renderTooltips(
            GuiGraphics graphics,
            int virtualMouseX,
            int virtualMouseY,
            int screenMouseX,
            int screenMouseY
    ) {
    }

    protected void renderScreenOverlay(
            GuiGraphics graphics,
            int virtualMouseX,
            int virtualMouseY,
            int screenMouseX,
            int screenMouseY,
            float partialTick
    ) {
    }

    protected final double toVirtualX(double screenX) {
        return (screenX - canvasX) / canvasScale;
    }

    protected final double toVirtualY(double screenY) {
        return (screenY - canvasY) / canvasScale;
    }

    protected final int toScreenX(double virtualX) {
        return canvasX + (int) Math.floor(virtualX * canvasScale);
    }

    protected final int toScreenY(double virtualY) {
        return canvasY + (int) Math.floor(virtualY * canvasScale);
    }

    protected final int toScreenRight(double virtualX) {
        return canvasX + (int) Math.ceil(virtualX * canvasScale);
    }

    protected final int toScreenBottom(double virtualY) {
        return canvasY + (int) Math.ceil(virtualY * canvasScale);
    }

    protected final boolean isInsideCanvas(double screenX, double screenY) {
        double virtualX = toVirtualX(screenX);
        double virtualY = toVirtualY(screenY);
        return virtualX >= 0 && virtualX < canvasWidth && virtualY >= 0 && virtualY < canvasHeight;
    }

    public final void enableCanvasScissor(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.enableScissor(toScreenX(left), toScreenY(top), toScreenRight(right), toScreenBottom(bottom));
    }

    protected final void renderScaledList(
            ObjectSelectionList<?> list,
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (list == null || minecraft == null) return;
        GuiGraphics proxy = new GuiGraphics(minecraft, graphics.bufferSource()) {
            @Override
            public void enableScissor(int left, int top, int right, int bottom) {
                super.enableScissor(toScreenX(left), toScreenY(top), toScreenRight(right), toScreenBottom(bottom));
            }
        };
        proxy.pose().translate(canvasX, canvasY, 0);
        proxy.pose().scale(canvasScale, canvasScale, 1f);
        list.render(proxy, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double virtualMouseX = toVirtualX(mouseX);
        double virtualMouseY = toVirtualY(mouseY);
        if (overlays.mouseClicked(mouseX, mouseY, button, width, height, font)) {
            return true;
        }
        boolean handled = canvasMouseClicked(toVirtualX(mouseX), toVirtualY(mouseY), button);
        if (!handled) {
            setFocused(null);
        }
        return handled;
    }

    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        updateScrollableWidgetPositions();
        List<AbstractWidget> hidden = hideScrollableWidgetsOutsideViewport(mouseX, mouseY);
        try {
            return super.mouseClicked(mouseX, mouseY, button);
        } finally {
            restoreScrollableWidgetVisibility(hidden);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (overlays.blocksInput()) {
            return true;
        }
        return canvasMouseReleased(toVirtualX(mouseX), toVirtualY(mouseY), button);
    }

    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (overlays.blocksInput()) return true;
        return canvasMouseDragged(
                toVirtualX(mouseX),
                toVirtualY(mouseY),
                button,
                dragX / canvasScale,
                dragY / canvasScale
        );
    }

    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (overlays.blocksInput()) return true;
        return canvasMouseScrolled(toVirtualX(mouseX), toVirtualY(mouseY), delta);
    }

    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (GuiSession.routeSelectionListWheel(children(), mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!overlays.blocksInput()) canvasMouseMoved(toVirtualX(mouseX), toVirtualY(mouseY));
    }

    protected void canvasMouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (overlays.keyPressed(keyCode)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

    final GuiSession.DraftSession draftSession() {
        return draftSession;
    }

    final void adoptDraftSession(GuiSession.DraftSession sharedDraft) {
        if (sharedDraft != null && sharedDraft.enabled()) {
            this.draftSession = sharedDraft;
        }
    }

    final void discardPendingEditsForNavigation() {
        draftSession.discardToBaseline();
    }

    @Override
    public void onClose() {
        GuiSession.back(this);
    }
}
