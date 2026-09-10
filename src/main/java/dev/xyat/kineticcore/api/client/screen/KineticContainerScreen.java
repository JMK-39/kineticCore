package dev.xyat.kineticcore.api.client.screen;

import dev.xyat.kineticcore.api.client.layout.GuiLayout;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public abstract class KineticContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private float preferredWidth = 640f;
    private float preferredHeight = 360f;
    private int safeMargin = 6;
    private float uiScale = 1f;
    private int uiX;
    private int uiY;
    private int uiWidth = 1;
    private int uiHeight = 1;
    private final GuiOverlay overlays = new GuiOverlay();
    private GuiSession.DraftSession draftSession = new GuiSession.DraftSession();

    protected KineticContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    protected final void useResponsiveContainer(float preferredWidth, float preferredHeight, int safeMargin) {
        this.preferredWidth = Math.max(1f, preferredWidth);
        this.preferredHeight = Math.max(1f, preferredHeight);
        this.safeMargin = Math.max(0, safeMargin);
    }

    protected final int uiWidth() {
        return uiWidth;
    }

    protected final int uiHeight() {
        return uiHeight;
    }

    protected final float uiScale() {
        return uiScale;
    }

    protected final double toVirtualX(double screenX) {
        return (screenX - uiX) / uiScale;
    }

    protected final double toVirtualY(double screenY) {
        return (screenY - uiY) / uiScale;
    }

    protected final GuiOverlay overlays() {
        return overlays;
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

    protected final <S> void configureDraft(java.util.function.Supplier<S> capture, java.util.function.Consumer<S> restore) {
        draftSession.configureDraft(this, capture, restore, false);
    }

    protected final <S> void configureStandaloneDraft(java.util.function.Supplier<S> capture, java.util.function.Consumer<S> restore) {
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


    @Override
    protected void init() {
        updateMetrics();
        int screenWidth = this.width;
        int screenHeight = this.height;
        this.width = uiWidth;
        this.height = uiHeight;
        try {
            super.init();
        } finally {
            this.width = screenWidth;
            this.height = screenHeight;
        }
    }

    private void updateMetrics() {
        GuiLayout.SafeArea safeArea = GuiLayout.SafeArea.of(width, height, safeMargin);
        GuiLayout.Metrics metrics = GuiLayout.measure(
                safeArea.width(),
                safeArea.height(),
                preferredWidth,
                preferredHeight
        );
        uiScale = Math.max(0.05f, Math.min(1f, metrics.fitScale()));
        uiX = safeArea.left();
        uiY = safeArea.top();
        uiWidth = Math.max(1, (int) Math.floor(safeArea.width() / uiScale));
        uiHeight = Math.max(1, (int) Math.floor(safeArea.height() / uiScale));
    }

    @Override
    public final void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        overlays.beginFrame();
        int virtualMouseX = (int) Math.floor(toVirtualX(mouseX));
        int virtualMouseY = (int) Math.floor(toVirtualY(mouseY));

        graphics.pose().pushPose();
        graphics.pose().translate(uiX, uiY, 0);
        graphics.pose().scale(uiScale, uiScale, 1f);
        try {
            super.render(graphics, virtualMouseX, virtualMouseY, partialTick);
            requestContainerTooltips(graphics, virtualMouseX, virtualMouseY, mouseX, mouseY);
            renderUiForeground(graphics, virtualMouseX, virtualMouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }

        renderScreenOverlay(graphics, virtualMouseX, virtualMouseY, mouseX, mouseY, partialTick);
        overlays.render(graphics, font, width, height, mouseX, mouseY);
    }

    protected void requestContainerTooltips(
            GuiGraphics graphics,
            int virtualMouseX,
            int virtualMouseY,
            int screenMouseX,
            int screenMouseY
    ) {
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            showItemTooltip(hoveredSlot.getItem());
        }
    }

    protected void renderUiForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double virtualMouseX = toVirtualX(mouseX);
        double virtualMouseY = toVirtualY(mouseY);
        if (overlays.mouseClicked(mouseX, mouseY, button, width, height, font)) {
            return true;
        }
        boolean handled = super.mouseClicked(toVirtualX(mouseX), toVirtualY(mouseY), button);
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (overlays.blocksInput()) {
            return true;
        }
        boolean handled = super.mouseReleased(toVirtualX(mouseX), toVirtualY(mouseY), button);
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (overlays.blocksInput()) return true;
        return super.mouseDragged(
                toVirtualX(mouseX),
                toVirtualY(mouseY),
                button,
                dragX / uiScale,
                dragY / uiScale
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (overlays.blocksInput()) return true;
        double virtualMouseX = toVirtualX(mouseX);
        double virtualMouseY = toVirtualY(mouseY);
        if (GuiSession.routeSelectionListWheel(children(), virtualMouseX, virtualMouseY, delta)) return true;
        return super.mouseScrolled(virtualMouseX, virtualMouseY, delta);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!overlays.blocksInput()) super.mouseMoved(toVirtualX(mouseX), toVirtualY(mouseY));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (overlays.keyPressed(keyCode)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            commitDraft();
            onClose();
            return true;
        }
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean handled = super.charTyped(codePoint, modifiers);
        return handled;
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
