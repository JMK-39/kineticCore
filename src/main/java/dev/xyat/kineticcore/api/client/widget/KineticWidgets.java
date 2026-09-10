package dev.xyat.kineticcore.api.client.widget;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.util.Mth;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class KineticWidgets {
    private KineticWidgets() {
    }

    private static final class ScrollUtil {
        public static final int SCROLLBAR_VISUAL_WIDTH = 4;
        public static final int DEFAULT_TRACK_COLOR = 0xFF171717;
        public static final int DEFAULT_THUMB_COLOR = 0xFFFF9800;
        public static final int DEFAULT_THUMB_HOVER_COLOR = 0xFFFFD700;


        private static int visualScrollbarWidth(int requestedWidth) {
            return Math.min(SCROLLBAR_VISUAL_WIDTH, Math.max(1, requestedWidth));
        }

        private static int visualScrollbarX(int requestedX, int requestedWidth) {
            return requestedX + Math.max(0, requestedWidth - visualScrollbarWidth(requestedWidth));
        }

        /**
         * 计算滑块高度
         */
        public static int calculateThumbHeight(int trackHeight, int visibleItems, int totalItems, int minHeight) {
            if (totalItems <= 0) return minHeight;
            return Math.max(minHeight, (int) ((float) visibleItems / totalItems * trackHeight));
        }

        /**
         * 根据鼠标位置计算滚动偏移量
         */
        public static int calculateScrollOffset(double mouseY, int trackY, int trackHeight, int thumbHeight, int maxScroll) {
            return (int) Math.round(calculateScrollOffsetPrecise(mouseY, trackY, trackHeight, thumbHeight, maxScroll));
        }

        public static double calculateScrollOffsetPrecise(double mouseY, int trackY, int trackHeight, int thumbHeight, int maxScroll) {
            double relativeY = mouseY - trackY - (thumbHeight / 2.0D);
            double scrollableHeight = trackHeight - thumbHeight;
            if (scrollableHeight <= 0D || maxScroll <= 0) {
                return 0D;
            }
            return Mth.clamp((relativeY / scrollableHeight) * maxScroll, 0D, maxScroll);
        }

        /**
         * Renders the default scrollbar theme.
         */
        public static void renderScrollbar(GuiGraphics g, int barX, int barY, int barWidth, int trackHeight, int thumbHeight, int maxScroll, int currentScroll, boolean isDragging) {
            renderScrollbar(g, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, currentScroll, isDragging, DEFAULT_TRACK_COLOR, DEFAULT_THUMB_COLOR, DEFAULT_THUMB_HOVER_COLOR);
        }

        public static void renderScrollbar(GuiGraphics g, int barX, int barY, int barWidth, int trackHeight, int thumbHeight, int maxScroll, double currentScroll, boolean isDragging) {
            renderScrollbar(g, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, currentScroll, isDragging, DEFAULT_TRACK_COLOR, DEFAULT_THUMB_COLOR, DEFAULT_THUMB_HOVER_COLOR);
        }

        /**
         * Renders the default scrollbar theme with automatic thumb hover highlighting.
         */
        public static void renderScrollbar(
                GuiGraphics g,
                double mouseX,
                double mouseY,
                int barX,
                int barY,
                int barWidth,
                int trackHeight,
                int thumbHeight,
                int maxScroll,
                int currentScroll,
                boolean isDragging
        ) {
            renderScrollbar(g, mouseX, mouseY, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, (double) currentScroll, isDragging);
        }

        public static void renderScrollbar(
                GuiGraphics g,
                double mouseX,
                double mouseY,
                int barX,
                int barY,
                int barWidth,
                int trackHeight,
                int thumbHeight,
                int maxScroll,
                double currentScroll,
                boolean isDragging
        ) {
            int safeMaxScroll = Math.max(0, maxScroll);
            if (safeMaxScroll <= 0 || trackHeight <= 0 || barWidth <= 0) return;
            int safeThumbHeight = Mth.clamp(thumbHeight, 1, trackHeight);
            double safeCurrentScroll = Math.max(0D, Math.min(currentScroll, safeMaxScroll));
            boolean hovered = isHoveringThumb(
                    mouseX,
                    mouseY,
                    barX,
                    barY,
                    barWidth,
                    trackHeight,
                    safeThumbHeight,
                    safeMaxScroll,
                    safeCurrentScroll
            );
            renderScrollbar(
                    g,
                    barX,
                    barY,
                    barWidth,
                    trackHeight,
                    safeThumbHeight,
                    safeMaxScroll,
                    safeCurrentScroll,
                    isDragging || hovered,
                    DEFAULT_TRACK_COLOR,
                    DEFAULT_THUMB_COLOR,
                    DEFAULT_THUMB_HOVER_COLOR
            );
        }

        /**
         * 渲染可自定义颜色的滚动条。
         */
        public static void renderScrollbar(GuiGraphics g, int barX, int barY, int barWidth, int trackHeight, int thumbHeight, int maxScroll, int currentScroll, boolean highlighted, int trackColor, int thumbColor, int highlightColor) {
            renderScrollbar(g, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, (double) currentScroll, highlighted, trackColor, thumbColor, highlightColor);
        }

        public static void renderScrollbar(GuiGraphics g, int barX, int barY, int barWidth, int trackHeight, int thumbHeight, int maxScroll, double currentScroll, boolean highlighted, int trackColor, int thumbColor, int highlightColor) {
            if (maxScroll <= 0 || trackHeight <= 0 || barWidth <= 0) return;
            double safeScroll = Math.max(0D, Math.min(currentScroll, maxScroll));
            int safeThumbHeight = Mth.clamp(thumbHeight, 1, trackHeight);
            int thumbY = barY + (int) Math.round(safeScroll / maxScroll * (trackHeight - safeThumbHeight));
            int visualWidth = visualScrollbarWidth(barWidth);
            int visualX = visualScrollbarX(barX, barWidth);
            g.fill(visualX, barY, visualX + visualWidth, barY + trackHeight, trackColor);
            g.fill(visualX, thumbY, visualX + visualWidth, thumbY + safeThumbHeight, highlighted ? highlightColor : thumbColor);
        }

        /**
         * 鼠标是否悬浮在滚动条滑块上。
         */
        public static boolean isHoveringThumb(double mouseX, double mouseY, int barX, int barY, int barWidth, int trackHeight, int thumbHeight, int maxScroll, int currentScroll) {
            return isHoveringThumb(mouseX, mouseY, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, (double) currentScroll);
        }

        public static boolean isHoveringThumb(double mouseX, double mouseY, int barX, int barY, int barWidth, int trackHeight, int thumbHeight, int maxScroll, double currentScroll) {
            if (maxScroll <= 0 || trackHeight <= 0 || barWidth <= 0) return false;
            double safeScroll = Math.max(0D, Math.min(currentScroll, maxScroll));
            int safeThumbHeight = Mth.clamp(thumbHeight, 1, trackHeight);
            int thumbY = barY + (int) Math.round(safeScroll / maxScroll * (trackHeight - safeThumbHeight));
            int visualWidth = visualScrollbarWidth(barWidth);
            int visualX = visualScrollbarX(barX, barWidth);
            return mouseX >= visualX && mouseX <= visualX + visualWidth && mouseY >= thumbY && mouseY <= thumbY + safeThumbHeight;
        }
    }

    public static final class Scroll {
        private Scroll() {
        }


        public static final class State {
            private double current;
            private double target;
            private double max;
            private long lastAnimationNanos = System.nanoTime();
            private boolean initialized;

            public double update(double target, double max) {
                return update(target, max, false);
            }

            public double update(double target, double max, boolean immediate) {
                this.max = Math.max(0D, max);
                this.target = clamp(target);

                long now = System.nanoTime();
                if (!initialized || immediate) {
                    current = this.target;
                    initialized = true;
                    lastAnimationNanos = now;
                    return current;
                }

                advance(now);
                return current;
            }

            public double follow(int logicalTarget, double max) {
                return follow(logicalTarget, max, false);
            }

            public double follow(double logicalTarget, double max) {
                return follow(logicalTarget, max, false);
            }

            public double follow(double logicalTarget, double max, boolean immediate) {
                this.max = Math.max(0D, max);
                if (!initialized) {
                    snap(logicalTarget, this.max);
                    return current;
                }

                if (Math.abs(target - logicalTarget) > 1.0E-6D) {
                    target = clamp(logicalTarget);
                }

                if (immediate) {
                    current = target;
                    lastAnimationNanos = System.nanoTime();
                    return current;
                }

                advance(System.nanoTime());
                return current;
            }

            public double follow(int logicalTarget, double max, boolean immediate) {
                this.max = Math.max(0D, max);
                if (!initialized) {
                    snap(logicalTarget, this.max);
                    return current;
                }

                if ((int) Math.round(target) != logicalTarget) {
                    target = clamp(logicalTarget);
                }

                if (immediate) {
                    current = target;
                    lastAnimationNanos = System.nanoTime();
                    return current;
                }

                advance(System.nanoTime());
                return current;
            }

            public double wheel(
                    double logicalTarget,
                    double delta,
                    double step,
                    double max
            ) {
                this.max = Math.max(0D, max);
                if (!initialized) {
                    snap(logicalTarget, this.max);
                } else if (Math.abs(target - logicalTarget) > 1.0E-6D) {
                    target = clamp(logicalTarget);
                }

                if (delta != 0D) {
                    target = clamp(target - delta * Math.max(0D, step));
                }

                return target;
            }

            public int wheel(
                    int logicalTarget,
                    double delta,
                    double step,
                    double max
            ) {
                this.max = Math.max(0D, max);
                if (!initialized) {
                    snap(logicalTarget, this.max);
                } else if ((int) Math.round(target) != logicalTarget) {
                    target = clamp(logicalTarget);
                }

                if (delta != 0D) {
                    target = clamp(target - delta * Math.max(0D, step));
                }

                return targetInt();
            }

            public void snap(double value, double max) {
                this.max = Math.max(0D, max);
                this.target = clamp(value);
                this.current = this.target;
                this.initialized = true;
                this.lastAnimationNanos = System.nanoTime();
            }

            public double current() {
                if (!initialized) return 0D;
                advance(System.nanoTime());
                return current;
            }

            public double target() {
                return target;
            }

            public int targetInt() {
                return (int) Math.round(target);
            }

            private void advance(long now) {
                double deltaSeconds = Math.min(
                        0.05D,
                        Math.max(
                                0D,
                                (now - lastAnimationNanos)
                                        / 1_000_000_000.0D
                        )
                );
                lastAnimationNanos = now;

                target = clamp(target);
                current = clamp(current);

                double difference = target - current;
                if (Math.abs(difference) <= 1.0E-3D) {
                    current = target;
                    return;
                }

                double factor =
                        1D - Math.exp(-18D * deltaSeconds);

                current = clamp(
                        current + difference * factor
                );
            }

            private double clamp(double value) {
                return Math.max(
                        0D,
                        Math.min(value, max)
                );
            }
        }

        public static int calculateThumbHeight(int trackHeight, int visibleItems, int totalItems, int minHeight) {
            return ScrollUtil.calculateThumbHeight(trackHeight, visibleItems, totalItems, minHeight);
        }

        public static int calculateScrollOffset(double mouseY, int trackY, int trackHeight, int thumbHeight, int maxScroll) {
            return ScrollUtil.calculateScrollOffset(mouseY, trackY, trackHeight, thumbHeight, maxScroll);
        }

        public static double calculateScrollOffsetPrecise(double mouseY, int trackY, int trackHeight, int thumbHeight, int maxScroll) {
            return ScrollUtil.calculateScrollOffsetPrecise(mouseY, trackY, trackHeight, thumbHeight, maxScroll);
        }

        public static void renderScrollbar(GuiGraphics g, int barX, int barY, int barWidth, int trackHeight, int thumbHeight, int maxScroll, int currentScroll, boolean isDragging) {
            ScrollUtil.renderScrollbar(g, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, currentScroll, isDragging);
        }

        public static void renderScrollbar(GuiGraphics g, int barX, int barY, int barWidth, int trackHeight, int thumbHeight, int maxScroll, double currentScroll, boolean isDragging) {
            ScrollUtil.renderScrollbar(g, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, currentScroll, isDragging);
        }

        public static void renderScrollbar(
                GuiGraphics g,
                double mouseX,
                double mouseY,
                int barX,
                int barY,
                int barWidth,
                int trackHeight,
                int thumbHeight,
                int maxScroll,
                int currentScroll,
                boolean isDragging
        ) {
            ScrollUtil.renderScrollbar(g, mouseX, mouseY, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, currentScroll, isDragging);
        }

        public static void renderScrollbar(
                GuiGraphics g,
                double mouseX,
                double mouseY,
                int barX,
                int barY,
                int barWidth,
                int trackHeight,
                int thumbHeight,
                int maxScroll,
                double currentScroll,
                boolean isDragging
        ) {
            ScrollUtil.renderScrollbar(g, mouseX, mouseY, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, currentScroll, isDragging);
        }

        public static void renderScrollbar(
                GuiGraphics g,
                int barX,
                int barY,
                int barWidth,
                int trackHeight,
                int thumbHeight,
                int maxScroll,
                int currentScroll,
                boolean highlighted,
                int trackColor,
                int thumbColor,
                int highlightColor
        ) {
            ScrollUtil.renderScrollbar(g, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, currentScroll, highlighted, trackColor, thumbColor, highlightColor);
        }

        public static void renderScrollbar(
                GuiGraphics g,
                int barX,
                int barY,
                int barWidth,
                int trackHeight,
                int thumbHeight,
                int maxScroll,
                double currentScroll,
                boolean highlighted,
                int trackColor,
                int thumbColor,
                int highlightColor
        ) {
            ScrollUtil.renderScrollbar(g, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, currentScroll, highlighted, trackColor, thumbColor, highlightColor);
        }

        public static boolean isHoveringThumb(double mouseX, double mouseY, int barX, int barY, int barWidth, int trackHeight, int thumbHeight, int maxScroll, int currentScroll) {
            return ScrollUtil.isHoveringThumb(mouseX, mouseY, barX, barY, barWidth, trackHeight, thumbHeight, maxScroll, currentScroll);
        }
    }

    public abstract static class SmoothSelectionList<E extends ObjectSelectionList.Entry<E>>
            extends ObjectSelectionList<E> {
        private static final int SCROLLBAR_WIDTH = 4;
        private static final int VANILLA_SCROLLBAR_WIDTH = 6;

        private final Scroll.State smoothScrollState = new Scroll.State();
        private final int kineticListTop;
        private final int kineticListBottom;
        private final int kineticItemHeight;
        private double targetScrollAmount;
        private boolean smoothScrollInitialized;
        private boolean scrollbarDragging;
        private double scrollbarDragGrabOffset;

        protected SmoothSelectionList(
                Minecraft minecraft,
                int width,
                int height,
                int y0,
                int y1,
                int itemHeight
        ) {
            super(minecraft, width, height, y0, y1, itemHeight);
            this.kineticListTop = y0;
            this.kineticListBottom = y1;
            this.kineticItemHeight = Math.max(1, itemHeight);
        }

        @Override
        public void setScrollAmount(double amount) {
            double max = Math.max(0D, getMaxScroll());
            targetScrollAmount = Mth.clamp(amount, 0D, max);
            if (!smoothScrollInitialized) {
                smoothScrollState.snap(targetScrollAmount, max);
                super.setScrollAmount(targetScrollAmount);
                smoothScrollInitialized = true;
            }
        }

        public final void snapScrollAmount(double amount) {
            double max = Math.max(0D, getMaxScroll());
            targetScrollAmount = Mth.clamp(amount, 0D, max);
            smoothScrollState.snap(targetScrollAmount, max);
            super.setScrollAmount(targetScrollAmount);
            smoothScrollInitialized = true;
        }

        public final double targetScrollAmount() {
            return targetScrollAmount;
        }

        private int scrollbarX() {
            return getScrollbarPosition() + Math.max(0, VANILLA_SCROLLBAR_WIDTH - SCROLLBAR_WIDTH);
        }

        private int scrollbarTrackHeight() {
            return Math.max(1, kineticListBottom - kineticListTop);
        }

        private int scrollbarThumbHeight() {
            int trackHeight = scrollbarTrackHeight();
            int contentHeight = Math.max(trackHeight, getMaxPosition());
            return Mth.clamp(
                    (int) Math.round((double) trackHeight * trackHeight / contentHeight),
                    Math.min(20, trackHeight),
                    trackHeight
            );
        }

        private boolean beginScrollbarDrag(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            double max = Math.max(0D, getMaxScroll());
            if (max <= 0D) return false;

            int barX = scrollbarX();
            int trackHeight = scrollbarTrackHeight();
            if (mouseX < barX || mouseX > barX + SCROLLBAR_WIDTH
                    || mouseY < kineticListTop || mouseY > kineticListBottom) {
                return false;
            }

            int thumbHeight = scrollbarThumbHeight();
            double currentScroll = Mth.clamp(super.getScrollAmount(), 0D, max);
            double travel = Math.max(0D, trackHeight - thumbHeight);
            double thumbTop = kineticListTop + (travel <= 0D ? 0D : currentScroll / max * travel);

            scrollbarDragging = true;
            if (mouseY >= thumbTop && mouseY <= thumbTop + thumbHeight) {
                scrollbarDragGrabOffset = mouseY - thumbTop;
            } else {
                scrollbarDragGrabOffset = thumbHeight / 2.0D;
                applyScrollbarDrag(mouseY);
            }
            return true;
        }

        private void applyScrollbarDrag(double mouseY) {
            double max = Math.max(0D, getMaxScroll());
            int trackHeight = scrollbarTrackHeight();
            int thumbHeight = scrollbarThumbHeight();
            double travel = Math.max(0D, trackHeight - thumbHeight);
            if (max <= 0D || travel <= 0D) {
                snapScrollAmount(0D);
                return;
            }

            double thumbTop = Mth.clamp(
                    mouseY - kineticListTop - scrollbarDragGrabOffset,
                    0D,
                    travel
            );
            snapScrollAmount(thumbTop / travel * max);
        }

        private boolean dragScrollbar(double mouseY, int button) {
            if (button != 0 || !scrollbarDragging) return false;
            applyScrollbarDrag(mouseY);
            return true;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            double max = Math.max(0D, getMaxScroll());
            if (!smoothScrollInitialized) {
                targetScrollAmount = Mth.clamp(
                        super.getScrollAmount(),
                        0D,
                        max
                );
                smoothScrollState.snap(targetScrollAmount, max);
                smoothScrollInitialized = true;
            }

            super.setScrollAmount(
                    smoothScrollState.update(
                            targetScrollAmount,
                            max,
                            scrollbarDragging
                    )
            );
            graphics.enableScissor(
                    this.getLeft(),
                    kineticListTop,
                    this.getLeft() + this.width,
                    kineticListBottom
            );
            try {
                super.render(
                        graphics,
                        mouseX,
                        mouseY,
                        partialTick
                );
            } finally {
                graphics.disableScissor();
            }

            if (max > 0D) {
                int trackHeight = scrollbarTrackHeight();
                graphics.fill(
                        getScrollbarPosition(),
                        kineticListTop,
                        getScrollbarPosition() + VANILLA_SCROLLBAR_WIDTH,
                        kineticListBottom,
                        GuiTheme.current().background()
                );
                ScrollUtil.renderScrollbar(
                        graphics,
                        mouseX,
                        mouseY,
                        scrollbarX(),
                        kineticListTop,
                        SCROLLBAR_WIDTH,
                        trackHeight,
                        scrollbarThumbHeight(),
                        (int) Math.ceil(max),
                        super.getScrollAmount(),
                        scrollbarDragging
                );
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (beginScrollbarDrag(mouseX, mouseY, button)) return true;

            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            if (handled && button == 0) {
                double max = Math.max(0D, getMaxScroll());
                targetScrollAmount = Mth.clamp(super.getScrollAmount(), 0D, max);
                smoothScrollState.snap(targetScrollAmount, max);
                super.setScrollAmount(targetScrollAmount);
                smoothScrollInitialized = true;
            }
            return handled;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (delta == 0D || !this.isMouseOver(mouseX, mouseY)) return false;
            double max = Math.max(0D, getMaxScroll());
            if (max <= 0D) return false;
            if (!smoothScrollInitialized) {
                targetScrollAmount = Mth.clamp(super.getScrollAmount(), 0D, max);
                smoothScrollState.snap(targetScrollAmount, max);
                smoothScrollInitialized = true;
            }
            targetScrollAmount = Mth.clamp(
                    targetScrollAmount - delta * (kineticItemHeight / 3.0D),
                    0D,
                    max
            );
            return true;
        }

        @Override
        public boolean mouseDragged(
                double mouseX,
                double mouseY,
                int button,
                double dragX,
                double dragY
        ) {
            if (dragScrollbar(mouseY, button)) return true;

            boolean handled = super.mouseDragged(
                    mouseX,
                    mouseY,
                    button,
                    dragX,
                    dragY
            );
            if (handled && button == 0) {
                double max = Math.max(0D, getMaxScroll());
                targetScrollAmount = Mth.clamp(super.getScrollAmount(), 0D, max);
                smoothScrollState.snap(targetScrollAmount, max);
                super.setScrollAmount(targetScrollAmount);
                smoothScrollInitialized = true;
            }
            return handled;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && scrollbarDragging) {
                scrollbarDragging = false;
                scrollbarDragGrabOffset = 0D;
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }
    }

    public static final class GridScrollController {
        private int offset;
        private double currentOffset;
        private double targetOffset;
        private long lastAnimationNanos = System.nanoTime();
        private int maxOffset;
        private int totalItems;
        private int visibleItems;
        private boolean dragging;
        private int dragGrabOffset;

        public void update(int totalItems, int visibleItems) {
            this.totalItems = Math.max(0, totalItems);
            this.visibleItems = Math.max(1, visibleItems);
            this.maxOffset = Math.max(
                    0,
                    this.totalItems - this.visibleItems
            );
            advanceAnimation();
            currentOffset = clamp(currentOffset);
            targetOffset = clamp(targetOffset);
            offset = clamp((int) Math.round(targetOffset));
        }

        public void updateRange(
                int maxOffset,
                int totalItems,
                int visibleItems
        ) {
            this.totalItems = Math.max(0, totalItems);
            this.visibleItems = Math.max(1, visibleItems);
            this.maxOffset = Math.max(0, maxOffset);
            advanceAnimation();
            currentOffset = clamp(currentOffset);
            targetOffset = clamp(targetOffset);
            offset = clamp((int) Math.round(targetOffset));
        }

        public void restoreOffset(int offset) {
            setOffset(offset);
        }

        public int offset() {
            advanceAnimation();
            return offset;
        }

        public double smoothOffset() {
            advanceAnimation();
            return currentOffset;
        }

        public int indexOffset() {
            return offset();
        }

        public int smoothIndexOffset() {
            advanceAnimation();
            return clamp((int) Math.floor(currentOffset + 1.0E-6D));
        }

        public double fractionalOffset() {
            advanceAnimation();
            return currentOffset - Math.floor(currentOffset);
        }

        public int visualShift(int unitPixels) {
            return (int) Math.round(fractionalOffset() * Math.max(0, unitPixels));
        }

        public int maxOffset() {
            return maxOffset;
        }

        public boolean canScroll() {
            return maxOffset > 0;
        }

        public void setOffset(int offset) {
            int clamped = clamp(offset);
            this.offset = clamped;
            this.currentOffset = clamped;
            this.targetOffset = clamped;
            this.lastAnimationNanos = System.nanoTime();
        }

        public void reset() {
            offset = 0;
            currentOffset = 0D;
            targetOffset = 0D;
            lastAnimationNanos = System.nanoTime();
            dragging = false;
            dragGrabOffset = 0;
        }

        public boolean scroll(double delta) {
            return scroll(delta, 1.0D / 3.0D);
        }

        public boolean scroll(double delta, int step) {
            return scroll(delta, (double) Math.max(1, step));
        }

        public boolean scroll(double delta, double step) {
            if (!canScroll() || delta == 0D) return false;

            advanceAnimation();
            double safeStep = Math.max(0.05D, step);
            targetOffset = clamp(targetOffset - delta * safeStep);
            offset = clamp((int) Math.round(targetOffset));
            return true;
        }

        public boolean beginDrag(
                double mouseX,
                double mouseY,
                int x,
                int y,
                int width,
                int height,
                int minThumbHeight,
                int hitPadding
        ) {
            if (!canScroll()) return false;
            if (mouseX < x - hitPadding
                    || mouseX > x + width + hitPadding
                    || mouseY < y
                    || mouseY > y + height) return false;

            int currentThumbHeight = thumbHeight(
                    height,
                    minThumbHeight
            );

            int currentThumbTop = thumbTop(
                    y,
                    height,
                    minThumbHeight
            );

            dragging = true;

            if (mouseY >= currentThumbTop
                    && mouseY <= currentThumbTop + currentThumbHeight) {
                dragGrabOffset = (int) Math.round(
                        mouseY - currentThumbTop
                );
            } else {
                dragGrabOffset = currentThumbHeight / 2;
                updateFromMouse(
                        mouseY,
                        y,
                        height,
                        minThumbHeight
                );
            }

            return true;
        }

        public boolean drag(double mouseY, int y, int height, int minThumbHeight) {
            if (!dragging) return false;
            updateFromMouse(mouseY, y, height, minThumbHeight);
            return true;
        }

        public boolean beginHorizontalDrag(
                double mouseX,
                double mouseY,
                int x,
                int y,
                int width,
                int height,
                int minThumbWidth,
                int hitPadding
        ) {
            if (!canScroll()) return false;
            if (mouseX < x
                    || mouseX > x + width
                    || mouseY < y - hitPadding
                    || mouseY > y + height + hitPadding) return false;

            int currentThumbWidth = thumbWidth(
                    width,
                    minThumbWidth
            );

            int currentThumbLeft = thumbLeft(
                    x,
                    width,
                    minThumbWidth
            );

            dragging = true;

            if (mouseX >= currentThumbLeft
                    && mouseX <= currentThumbLeft + currentThumbWidth) {
                dragGrabOffset = (int) Math.round(
                        mouseX - currentThumbLeft
                );
            } else {
                dragGrabOffset = currentThumbWidth / 2;
                updateFromMouseHorizontal(
                        mouseX,
                        x,
                        width,
                        minThumbWidth
                );
            }

            return true;
        }

        public boolean dragHorizontal(
                double mouseX,
                int x,
                int width,
                int minThumbWidth
        ) {
            if (!dragging) return false;

            updateFromMouseHorizontal(
                    mouseX,
                    x,
                    width,
                    minThumbWidth
            );
            return true;
        }

        public boolean release(int button) {
            if (button != 0 || !dragging) return false;
            dragging = false;
            dragGrabOffset = 0;
            return true;
        }

        public void render(
                GuiGraphics graphics,
                int x,
                int y,
                int width,
                int height,
                int minThumbHeight
        ) {
            if (canScroll()) {
                int thumbHeight = thumbHeight(height, minThumbHeight);
                ScrollUtil.renderScrollbar(
                        graphics, x, y, width, height,
                        thumbHeight, maxOffset, smoothOffset(), dragging
                );
            }
        }

        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                int x,
                int y,
                int width,
                int height,
                int minThumbHeight
        ) {
            render(
                    graphics,
                    mouseX,
                    mouseY,
                    x,
                    y,
                    width,
                    height,
                    minThumbHeight,
                    ScrollUtil.DEFAULT_TRACK_COLOR,
                    ScrollUtil.DEFAULT_THUMB_COLOR,
                    ScrollUtil.DEFAULT_THUMB_HOVER_COLOR
            );
        }

        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                int x,
                int y,
                int width,
                int height,
                int minThumbHeight,
                int trackColor,
                int thumbColor,
                int hoverColor
        ) {
            if (canScroll()) {
                int thumbHeight = thumbHeight(height, minThumbHeight);
                boolean hover = ScrollUtil.isHoveringThumb(
                        mouseX, mouseY, x, y, width, height,
                        thumbHeight, maxOffset, smoothOffset()
                );

                ScrollUtil.renderScrollbar(
                        graphics, x, y, width, height,
                        thumbHeight, maxOffset, smoothOffset(),
                        dragging || hover,
                        trackColor, thumbColor, hoverColor
                );
            }
        }

        public void renderFramed(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                int x,
                int y,
                int width,
                int height,
                int minThumbHeight,
                int borderColor,
                int trackColor,
                int thumbColor,
                int hoverColor
        ) {
            if (!canScroll()) return;

            int currentThumbHeight = thumbHeight(
                    height,
                    minThumbHeight
            );

            int currentThumbTop = thumbTop(
                    y,
                    height,
                    minThumbHeight
            );

            int visualWidth = ScrollUtil.visualScrollbarWidth(width);
            int visualX = ScrollUtil.visualScrollbarX(x, width);
            boolean hovered =
                    mouseX >= visualX
                            && mouseX <= visualX + visualWidth
                            && mouseY >= currentThumbTop
                            && mouseY <= currentThumbTop + currentThumbHeight;

            graphics.fill(
                    visualX,
                    y,
                    visualX + visualWidth,
                    y + height,
                    trackColor
            );

            graphics.fill(
                    visualX,
                    currentThumbTop,
                    visualX + visualWidth,
                    currentThumbTop + currentThumbHeight,
                    dragging || hovered
                            ? hoverColor
                            : thumbColor
            );
        }

        public void renderHorizontal(
                GuiGraphics graphics,
                int x,
                int y,
                int width,
                int height,
                int minThumbWidth
        ) {
            renderHorizontal(
                    graphics,
                    Integer.MIN_VALUE,
                    Integer.MIN_VALUE,
                    x,
                    y,
                    width,
                    height,
                    minThumbWidth
            );
        }

        public void renderHorizontal(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                int x,
                int y,
                int width,
                int height,
                int minThumbWidth
        ) {
            renderHorizontal(
                    graphics,
                    mouseX,
                    mouseY,
                    x,
                    y,
                    width,
                    height,
                    minThumbWidth,
                    ScrollUtil.DEFAULT_TRACK_COLOR,
                    ScrollUtil.DEFAULT_THUMB_COLOR,
                    ScrollUtil.DEFAULT_THUMB_HOVER_COLOR
            );
        }

        public void renderHorizontal(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                int x,
                int y,
                int width,
                int height,
                int minThumbWidth,
                int trackColor,
                int thumbColor,
                int hoverColor
        ) {
            if (!canScroll()) return;

            int currentThumbWidth = thumbWidth(
                    width,
                    minThumbWidth
            );

            int currentThumbLeft = thumbLeft(
                    x,
                    width,
                    minThumbWidth
            );

            boolean hovered =
                    mouseX >= currentThumbLeft
                            && mouseX <= currentThumbLeft + currentThumbWidth
                            && mouseY >= y
                            && mouseY <= y + height;

            graphics.fill(
                    x,
                    y,
                    x + width,
                    y + height,
                    trackColor
            );

            graphics.fill(
                    currentThumbLeft,
                    y,
                    currentThumbLeft + currentThumbWidth,
                    y + height,
                    dragging || hovered
                            ? hoverColor
                            : thumbColor
            );
        }

        private int thumbTop(
                int y,
                int height,
                int minThumbHeight
        ) {
            int currentThumbHeight = thumbHeight(
                    height,
                    minThumbHeight
            );

            int travel = Math.max(
                    0,
                    height - currentThumbHeight
            );

            if (maxOffset <= 0 || travel == 0) {
                return y;
            }

            advanceAnimation();
            return y + (int) Math.round(
                    travel * (currentOffset / maxOffset)
            );
        }

        private int thumbLeft(
                int x,
                int width,
                int minThumbWidth
        ) {
            int currentThumbWidth = thumbWidth(
                    width,
                    minThumbWidth
            );

            int travel = Math.max(
                    0,
                    width - currentThumbWidth
            );

            if (maxOffset <= 0 || travel == 0) {
                return x;
            }

            advanceAnimation();
            return x + (int) Math.round(
                    travel * (currentOffset / maxOffset)
            );
        }

        private int thumbHeight(int height, int minThumbHeight) {
            return ScrollUtil.calculateThumbHeight(
                    height, visibleItems, totalItems, minThumbHeight
            );
        }

        private int thumbWidth(
                int width,
                int minThumbWidth
        ) {
            if (totalItems <= 0) {
                return width;
            }

            int calculated = Math.round(
                    width
                            * Math.min(
                            1f,
                            visibleItems / (float) totalItems
                    )
            );

            return Math.max(
                    minThumbWidth,
                    Math.min(width, calculated)
            );
        }

        private void updateFromMouseHorizontal(
                double mouseX,
                int x,
                int width,
                int minThumbWidth
        ) {
            int currentThumbWidth = thumbWidth(
                    width,
                    minThumbWidth
            );

            int travel = Math.max(
                    1,
                    width - currentThumbWidth
            );

            double relative =
                    mouseX - x - dragGrabOffset;

            double ratio =
                    relative / travel;

            ratio = Math.max(
                    0D,
                    Math.min(1D, ratio)
            );

            double next = clamp(ratio * maxOffset);
            currentOffset = next;
            targetOffset = next;
            offset = clamp((int) Math.round(next));
            lastAnimationNanos = System.nanoTime();
        }

        private void updateFromMouse(
                double mouseY,
                int y,
                int height,
                int minThumbHeight
        ) {
            int currentThumbHeight = thumbHeight(
                    height,
                    minThumbHeight
            );

            int travel = Math.max(
                    1,
                    height - currentThumbHeight
            );

            double relative =
                    mouseY - y - dragGrabOffset;

            double ratio =
                    relative / travel;

            ratio = Math.max(
                    0D,
                    Math.min(1D, ratio)
            );

            double next = clamp(ratio * maxOffset);
            currentOffset = next;
            targetOffset = next;
            offset = clamp((int) Math.round(next));
            lastAnimationNanos = System.nanoTime();
        }

        private void advanceAnimation() {
            long now = System.nanoTime();
            double deltaSeconds = Math.min(0.05D, Math.max(0D, (now - lastAnimationNanos) / 1_000_000_000.0D));
            lastAnimationNanos = now;

            targetOffset = clamp(targetOffset);
            if (dragging) {
                currentOffset = targetOffset;
            } else {
                double difference = targetOffset - currentOffset;
                if (Math.abs(difference) <= 1.0E-3D) {
                    currentOffset = targetOffset;
                } else {
                    double factor = 1D - Math.exp(-18D * deltaSeconds);
                    currentOffset += difference * factor;
                }
            }
            currentOffset = clamp(currentOffset);
            offset = clamp((int) Math.round(targetOffset));
        }

        private double clamp(double value) {
            return Math.max(0D, Math.min(value, maxOffset));
        }

        private int clamp(int value) {
            return Math.max(0, Math.min(value, maxOffset));
        }
    }

    public static class NumericEditBox extends EditBox {
        public enum Type {
            INTEGER,
            LONG,
            DECIMAL
        }

        private final Type type;
        private final boolean allowNegative;
        private final Double minValue;
        private final Double maxValue;

        public NumericEditBox(
                Font font,
                int x,
                int y,
                int width,
                int height,
                Component message,
                Type type,
                boolean allowNegative,
                Double minValue,
                Double maxValue
        ) {
            super(font, x, y, width, height, message);
            this.type = type;
            this.allowNegative = allowNegative;
            this.minValue = minValue;
            this.maxValue = maxValue;
            setFilter(this::isAllowedText);
        }

        public static NumericEditBox integer(
                Font font,
                int x,
                int y,
                int width,
                int height,
                Component message,
                boolean allowNegative,
                Integer minValue,
                Integer maxValue
        ) {
            return new NumericEditBox(
                    font, x, y, width, height, message,
                    Type.INTEGER, allowNegative,
                    minValue == null ? null : minValue.doubleValue(),
                    maxValue == null ? null : maxValue.doubleValue()
            );
        }

        public static NumericEditBox longInteger(
                Font font,
                int x,
                int y,
                int width,
                int height,
                Component message,
                boolean allowNegative,
                Long minValue,
                Long maxValue
        ) {
            return new NumericEditBox(
                    font, x, y, width, height, message,
                    Type.LONG, allowNegative,
                    minValue == null ? null : minValue.doubleValue(),
                    maxValue == null ? null : maxValue.doubleValue()
            );
        }

        public static NumericEditBox decimal(
                Font font,
                int x,
                int y,
                int width,
                int height,
                Component message,
                boolean allowNegative,
                Double minValue,
                Double maxValue
        ) {
            return new NumericEditBox(
                    font, x, y, width, height, message,
                    Type.DECIMAL, allowNegative, minValue, maxValue
            );
        }

        public Integer getIntValue() {
            String raw = getValue().trim();
            if (raw.isEmpty() || "-".equals(raw)) return null;

            try {
                int value = Integer.parseInt(raw);
                return isInRange(value) ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        public Long getLongValue() {
            String raw = getValue().trim();
            if (raw.isEmpty() || "-".equals(raw)) return null;

            try {
                long value = Long.parseLong(raw);
                return isInRange(value) ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        public Double getDoubleValue() {
            String raw = getValue().trim();
            if (raw.isEmpty() || "-".equals(raw) || ".".equals(raw) || "-.".equals(raw)) return null;

            try {
                double value = Double.parseDouble(raw);
                return Double.isFinite(value) && isInRange(value) ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        public void setIntValue(int value) {
            setValue(Integer.toString(value));
        }

        public void setLongValue(long value) {
            setValue(Long.toString(value));
        }

        public static String format(double value) {
            if (!Double.isFinite(value)) return Double.toString(value);
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }

        private boolean isAllowedText(String value) {
            if (value == null || value.isEmpty()) return true;
            if ("-".equals(value)) return allowNegative;

            int start = value.charAt(0) == '-' ? 1 : 0;
            if (start == 1 && !allowNegative) return false;

            if (type == Type.INTEGER || type == Type.LONG) {
                for (int i = start; i < value.length(); i++) {
                    if (!Character.isDigit(value.charAt(i))) return false;
                }
                return true;
            }

            boolean dotSeen = false;
            for (int i = start; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '.') {
                    if (dotSeen) return false;
                    dotSeen = true;
                } else if (!Character.isDigit(c)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isInRange(double value) {
            if (minValue != null && value < minValue) return false;
            return maxValue == null || value <= maxValue;
        }
    }

    public static class AutoCompleteBox extends EditBox {
        private final Supplier<List<String>> dictionarySupplier;
        private List<String> suggestions = new ArrayList<>();
        private Consumer<String> externalResponder;
        private Consumer<String> selectionResponder;

        private final GridScrollController suggestionScroll =
                new GridScrollController();

        private int selectedIndex = -1;
        private int maxSuggestionWidth = 0;
        private static final int MAX_VISIBLE = 8;

        public AutoCompleteBox(Font font, int x, int y, int width, int height, Component message, Supplier<List<String>> dictionarySupplier) {
            super(font, x, y, width, height, message);
            this.dictionarySupplier = dictionarySupplier;
            this.setMaxLength(1024);
            this.setBordered(true);

            super.setResponder(val -> {
                this.updateSuggestions(val);
                if (this.externalResponder != null) this.externalResponder.accept(val);
            });
        }

        @Override
        public void setResponder(@NotNull Consumer<String> responder) {
            this.externalResponder = responder;
        }

        public void setSelectionResponder(Consumer<String> responder) {
            this.selectionResponder = responder;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            if (super.isMouseOver(mouseX, mouseY)) return true;
            if (isFocused() && !suggestions.isEmpty()) {
                int x = this.getX() - 4;
                int y = this.getY() + this.getHeight() + 4;
                int listH = Math.min(suggestions.size(), MAX_VISIBLE) * 12;
                int w = this.maxSuggestionWidth + (suggestions.size() > MAX_VISIBLE ? 10 : 0);
                return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + listH;
            }
            return false;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (focused) updateSuggestions(this.getValue());
            else clearSuggestions();
        }

        public void clearSuggestions() {
            this.suggestions.clear();
            this.suggestionScroll.reset();
            this.selectedIndex = -1;
            this.maxSuggestionWidth = 0;
        }

        public void loadSuggestions() {
            updateSuggestions(this.getValue());
        }

        private void updateSuggestions(String input) {
            List<String> allItems = dictionarySupplier.get();
            if (input.isEmpty()) {
                suggestions = new ArrayList<>(allItems);
            } else {
                String lower = input.toLowerCase();
                suggestions = allItems.stream().filter(s -> s.toLowerCase().contains(lower)).collect(Collectors.toList());
            }

            Font font = Minecraft.getInstance().font;
            int currentMax = this.width;
            for (String s : suggestions) {
                int w = font.width(s) + 10;
                if (w > currentMax) currentMax = w;
            }
            this.maxSuggestionWidth = currentMax;
            suggestionScroll.reset();
            suggestionScroll.update(
                    suggestions.size(),
                    MAX_VISIBLE
            );
            selectedIndex = -1;
        }

        public boolean handleMouseScrolled(double delta) {
            if (!isFocused() || suggestions.isEmpty()) {
                return false;
            }

            suggestionScroll.update(
                    suggestions.size(),
                    MAX_VISIBLE
            );

            return suggestionScroll.scroll(delta);
        }

        public boolean handleKeyPressed(int keyCode) {
            if (!isFocused()) return false;
            if (!suggestions.isEmpty()) {
                if (keyCode == 265) {
                    selectedIndex = (selectedIndex <= 0) ? suggestions.size() - 1 : selectedIndex - 1;
                    ensureVisible(); return true;
                }
                if (keyCode == 264) {
                    selectedIndex = (selectedIndex >= suggestions.size() - 1) ? 0 : selectedIndex + 1;
                    ensureVisible(); return true;
                }
                if (keyCode == 257 || keyCode == 335) {
                    if (selectedIndex >= 0 && selectedIndex < suggestions.size()) {
                        selectItem(selectedIndex); return true;
                    }
                }
            }
            return super.keyPressed(keyCode, 0, 0);
        }

        private void ensureVisible() {
            suggestionScroll.update(
                    suggestions.size(),
                    MAX_VISIBLE
            );

            if (selectedIndex < suggestionScroll.offset()) {
                suggestionScroll.setOffset(selectedIndex);
            }

            if (selectedIndex
                    >= suggestionScroll.offset() + MAX_VISIBLE) {
                suggestionScroll.setOffset(
                        selectedIndex - MAX_VISIBLE + 1
                );
            }
        }

        private void selectItem(int index) {
            String val = normalizeValue(suggestions.get(index));
            this.setValue(val);
            this.setCursorPosition(this.getValue().length());
            this.clearSuggestions();
            if (this.selectionResponder != null) {
                this.selectionResponder.accept(val);
            }
        }

        public void renderSuggestions(GuiGraphics gui, int mouseX, int mouseY) {
            if (!isFocused() || suggestions.isEmpty()) return;

            int x = this.getX() - 4;
            int y = this.getY() + this.getHeight() + 4;
            int itemH = 12;
            int w = this.maxSuggestionWidth;
            int visibleCount = Math.min(suggestions.size(), MAX_VISIBLE);
            int totalH = visibleCount * itemH;

            suggestionScroll.update(
                    suggestions.size(),
                    MAX_VISIBLE
            );

            int firstIndex = suggestionScroll.smoothIndexOffset();
            int visualShift = suggestionScroll.visualShift(itemH);
            int rowsToRender = Math.min(
                    visibleCount + (visualShift > 0 ? 1 : 0),
                    suggestions.size() - firstIndex
            );

            gui.pose().pushPose();
            gui.pose().translate(0, 0, 600);
            gui.fill(x, y, x + w, y + totalH, 0xFF0A0A0A);
            gui.renderOutline(x, y, w, totalH, 0xFF555555);
            gui.enableScissor(x, y, x + w, y + totalH);

            for (int i = 0; i < rowsToRender; i++) {
                int index = firstIndex + i;
                int top = y + (i * itemH) - visualShift;

                gui.fill(x + 1, top, x + w - 1, top + itemH, (index % 2 == 0) ? 0xFF1C1C1C : 0xFF0A0A0A);

                if ((mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + totalH
                        && mouseY >= top && mouseY < top + itemH) || index == selectedIndex) {
                    gui.fill(x + 1, top, x + w - 1, top + itemH, GuiTheme.current().accentHover());
                }

                String[] parts = suggestions.get(index).split(" - ", 2);
                MutableComponent line = Component.literal(parts[0]).withStyle(ChatFormatting.GOLD);
                if (parts.length > 1) line.append(Component.literal(" - " + parts[1]).withStyle(ChatFormatting.WHITE));
                gui.drawString(Minecraft.getInstance().font, line, x + 4, top + 2, 0xFFFFFF);
            }

            gui.disableScissor();
            suggestionScroll.render(
                    gui,
                    mouseX,
                    mouseY,
                    x + w + 2,
                    y,
                    4,
                    totalH,
                    10
            );
            gui.pose().popPose();
        }

        public boolean handleMouseClick(double mouseX, double mouseY) {
            if (!isFocused() || suggestions.isEmpty()) return false;
            int x = this.getX() - 4;
            int y = this.getY() + this.getHeight() + 4;
            int itemH = 12;
            int w = this.maxSuggestionWidth;
            int totalH = Math.min(suggestions.size(), MAX_VISIBLE) * itemH;

            suggestionScroll.update(
                    suggestions.size(),
                    MAX_VISIBLE
            );

            if (suggestionScroll.beginDrag(
                    mouseX,
                    mouseY,
                    x + w + 2,
                    y,
                    4,
                    totalH,
                    10,
                    2
            )) {
                return true;
            }
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + totalH) {
                int localY = (int) Math.floor(mouseY - y + suggestionScroll.visualShift(itemH));
                int clickedIdx = suggestionScroll.smoothIndexOffset() + localY / itemH;
                if (clickedIdx >= 0 && clickedIdx < suggestions.size()) {
                    selectItem(clickedIdx);
                    return true;
                }
            }
            return false;
        }

        public boolean handleMouseDragged(double mouseY) {
            if (!Double.isFinite(mouseY) || !isFocused()) {
                return false;
            }

            return suggestionScroll.drag(
                    mouseY,
                    this.getY() + this.getHeight() + 4,
                    Math.min(
                            suggestions.size(),
                            MAX_VISIBLE
                    ) * 12,
                    10
            );
        }

        public boolean handleMouseDragged(
                double mouseX,
                double mouseY
        ) {
            if (!Double.isFinite(mouseX)
                    || !Double.isFinite(mouseY)) {
                return false;
            }

            return handleMouseDragged(mouseY);
        }

        public boolean handleMouseReleased(int button) {
            return suggestionScroll.release(button);
        }

        public String normalizedValue() {
            return normalizeValue(getValue());
        }

        public static String normalizeValue(String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }

            int separator = value.indexOf(" - ");

            if (separator < 0) {
                return value;
            }

            return value.substring(0, separator).trim();
        }
    }

    public static final class AutoCompleteBoxGroup {
        private final List<AutoCompleteBox> boxes =
                new ArrayList<>();

        public void set(AutoCompleteBox... inputs) {
            boxes.clear();

            if (inputs == null) {
                return;
            }

            for (AutoCompleteBox input : inputs) {
                if (input != null) {
                    boxes.add(input);
                }
            }
        }

        public boolean handleMouseScrolled(double delta) {
            for (AutoCompleteBox box : boxes) {
                if (box.handleMouseScrolled(delta)) {
                    return true;
                }
            }

            return false;
        }

        public boolean handleSuggestionClick(
                double mouseX,
                double mouseY
        ) {
            for (AutoCompleteBox box : boxes) {
                if (box.handleMouseClick(
                        mouseX,
                        mouseY
                )) {
                    return true;
                }
            }

            return false;
        }

        public boolean handleMouseDragged(
                double mouseX,
                double mouseY
        ) {
            for (AutoCompleteBox box : boxes) {
                if (box.handleMouseDragged(
                        mouseX,
                        mouseY
                )) {
                    return true;
                }
            }

            return false;
        }

        public boolean handleMouseReleased(int button) {
            for (AutoCompleteBox box : boxes) {
                if (box.handleMouseReleased(button)) {
                    return true;
                }
            }

            return false;
        }

        public boolean handleKeyPressed(int keyCode) {
            for (AutoCompleteBox box : boxes) {
                if (box.handleKeyPressed(keyCode)) {
                    return true;
                }
            }

            return false;
        }

        public void clearFocusOutside(
                double mouseX,
                double mouseY
        ) {
            for (AutoCompleteBox box : boxes) {
                if (!box.isMouseOver(
                        mouseX,
                        mouseY
                )) {
                    box.setFocused(false);
                }
            }
        }

        public void renderSuggestions(
                GuiGraphics graphics,
                int mouseX,
                int mouseY
        ) {
            for (AutoCompleteBox box : boxes) {
                box.renderSuggestions(
                        graphics,
                        mouseX,
                        mouseY
                );
            }
        }
    }

    public static class NumericAutoCompleteBox extends AutoCompleteBox {
        public enum Type {
            INTEGER,
            DECIMAL
        }

        private final Type type;
        private final boolean allowNegative;
        private final Double minValue;
        private final Double maxValue;

        public NumericAutoCompleteBox(
                Font font,
                int x,
                int y,
                int width,
                int height,
                Component message,
                Supplier<List<String>> dictionarySupplier,
                Type type,
                boolean allowNegative,
                Double minValue,
                Double maxValue
        ) {
            super(
                    font,
                    x,
                    y,
                    width,
                    height,
                    message,
                    dictionarySupplier
            );

            this.type = type;
            this.allowNegative = allowNegative;
            this.minValue = minValue;
            this.maxValue = maxValue;
            setFilter(this::isAllowedText);
        }

        public static NumericAutoCompleteBox integer(
                Font font,
                int x,
                int y,
                int width,
                int height,
                Component message,
                Supplier<List<String>> dictionarySupplier,
                boolean allowNegative,
                Integer minValue,
                Integer maxValue
        ) {
            return new NumericAutoCompleteBox(
                    font,
                    x,
                    y,
                    width,
                    height,
                    message,
                    dictionarySupplier,
                    Type.INTEGER,
                    allowNegative,
                    minValue == null
                            ? null
                            : minValue.doubleValue(),
                    maxValue == null
                            ? null
                            : maxValue.doubleValue()
            );
        }

        public static NumericAutoCompleteBox decimal(
                Font font,
                int x,
                int y,
                int width,
                int height,
                Component message,
                Supplier<List<String>> dictionarySupplier,
                boolean allowNegative,
                Double minValue,
                Double maxValue
        ) {
            return new NumericAutoCompleteBox(
                    font,
                    x,
                    y,
                    width,
                    height,
                    message,
                    dictionarySupplier,
                    Type.DECIMAL,
                    allowNegative,
                    minValue,
                    maxValue
            );
        }

        public Integer getIntValue() {
            String raw = getValue().trim();

            if (raw.isEmpty()
                    || "-".equals(raw)) {
                return null;
            }

            try {
                int value = Integer.parseInt(raw);

                return isInRange(value)
                        ? value
                        : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        public Double getDoubleValue() {
            String raw = getValue().trim();

            if (raw.isEmpty()
                    || "-".equals(raw)
                    || ".".equals(raw)
                    || "-.".equals(raw)) {
                return null;
            }

            try {
                double value =
                        Double.parseDouble(raw);

                if (!Double.isFinite(value)
                        || !isInRange(value)) {
                    return null;
                }

                return value;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        public void setIntValue(int value) {
            setValue(Integer.toString(value));
        }

        public void setDoubleValue(double value) {
            setValue(format(value));
        }

        public static String format(double value) {
            if (!Double.isFinite(value)) {
                return Double.toString(value);
            }

            return BigDecimal.valueOf(value)
                    .stripTrailingZeros()
                    .toPlainString();
        }

        private boolean isAllowedText(String value) {
            if (value == null
                    || value.isEmpty()) {
                return true;
            }

            if ("-".equals(value)) {
                return allowNegative;
            }

            int start =
                    value.charAt(0) == '-'
                            ? 1
                            : 0;

            if (start == 1 && !allowNegative) {
                return false;
            }

            if (type == Type.INTEGER) {
                for (int i = start;
                     i < value.length();
                     i++) {
                    if (!Character.isDigit(
                            value.charAt(i)
                    )) {
                        return false;
                    }
                }

                return true;
            }

            boolean dotSeen = false;

            for (int i = start;
                 i < value.length();
                 i++) {
                char c = value.charAt(i);

                if (c == '.') {
                    if (dotSeen) {
                        return false;
                    }

                    dotSeen = true;
                    continue;
                }

                if (!Character.isDigit(c)) {
                    return false;
                }
            }

            return true;
        }

        private boolean isInRange(double value) {
            if (minValue != null
                    && value < minValue) {
                return false;
            }

            return maxValue == null
                    || value <= maxValue;
        }
    }

    public static final class ColorPreviewButton extends Button {
        private int rgb;

        public ColorPreviewButton(int x, int y, int width, int height, int rgb, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.rgb = rgb & 0xFFFFFF;
        }

        public void setRgb(int rgb) {
            this.rgb = rgb & 0xFFFFFF;
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            int size = Math.min(12, Math.max(8, getHeight() - 8));
            int x = getX() + 6;
            int y = getY() + (getHeight() - size) / 2;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFFFFFFFF);
            graphics.fill(x, y, x + size, y + size, 0xFF000000 | rgb);
        }
    }

    public static class HighZButton extends Button {
        private final int zLevel;

        public HighZButton(int x, int y, int w, int h, Component msg, OnPress onPress, Tooltip tooltip) {
            this(x, y, w, h, msg, onPress, tooltip, 200);
        }

        public HighZButton(int x, int y, int w, int h, Component msg, OnPress onPress, Tooltip tooltip, int zLevel) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
            if (tooltip != null) this.setTooltip(tooltip);
            this.zLevel = zLevel;
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics g, int mx, int my, float pt) {
            g.pose().pushPose();
            g.pose().translate(0, 0, zLevel);
            super.renderWidget(g, mx, my, pt);
            g.pose().popPose();
        }
    }

    public static final class LayerState<L> {
        private L activeLayer;

        public void open(L layer) {
            activeLayer = Objects.requireNonNull(layer);
        }

        public void close(L layer) {
            if (Objects.equals(activeLayer, layer)) activeLayer = null;
        }

        public void closeAll() {
            activeLayer = null;
        }

        public boolean isOpen(L layer) {
            return Objects.equals(activeLayer, layer);
        }

        public boolean isAnyOpen() {
            return activeLayer != null;
        }

        public L activeLayer() {
            return activeLayer;
        }
    }

    public static final class EntityPreviewRenderer {
        public static final float DEFAULT_ROTATION = 20f;
        public static final float DEFAULT_BASE_ROTATION_SPEED = 90f;
        public static final int DEFAULT_ZOOM_PERCENT = 100;
        public static final int MIN_ZOOM_PERCENT = 10;
        public static final int MAX_ZOOM_PERCENT = 500;
        public static final int ZOOM_STEP_PERCENT = 10;
        public static final float DEFAULT_FILL_RATIO = 0.56f;
        public static final float DEFAULT_MAX_AUTO_SCALE_FACTOR = 1.10f;
        public static final long DEFAULT_FRAME_LIMIT_NANOS = 100_000_000L;
        public static final int DEFAULT_CACHE_SIZE = 48;

        private static final int CHECKER_SIZE = 6;
        private static final int CHECKER_LIGHT = 0xFFF0F0F0;
        private static final int CHECKER_DARK = 0xFFD2D2D2;

        private final int maxCacheSize;
        private final float fillRatio;
        private final float maxAutoScaleFactor;
        private final Map<String, Entity> renderCache;
        private final Set<String> renderFailed = new HashSet<>();
        private final Map<String, EntityPreviewState> states = new LinkedHashMap<>();
        private int rotationSpeedPercent = 100;
        private boolean clockwise = true;

        public EntityPreviewRenderer() {
            this(DEFAULT_CACHE_SIZE, DEFAULT_FILL_RATIO, DEFAULT_MAX_AUTO_SCALE_FACTOR);
        }

        public EntityPreviewRenderer(int maxCacheSize, float fillRatio, float maxAutoScaleFactor) {
            this.maxCacheSize = Math.max(1, maxCacheSize);
            this.fillRatio = Math.max(0.05f, fillRatio);
            this.maxAutoScaleFactor = Math.max(0.05f, maxAutoScaleFactor);
            this.renderCache = new LinkedHashMap<>(this.maxCacheSize, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entity> eldest) {
                    return size() > EntityPreviewRenderer.this.maxCacheSize;
                }
            };
        }

        public void setRotationSpeedPercent(int rotationSpeedPercent) {
            this.rotationSpeedPercent = Math.max(0, Math.min(500, rotationSpeedPercent));
        }

        public void setClockwise(boolean clockwise) {
            this.clockwise = clockwise;
        }

        public int getZoomPercent(String stateKey) {
            return getState(stateKey).getZoomPercent();
        }

        public void setZoomPercent(String stateKey, int zoomPercent) {
            getState(stateKey).setZoomPercent(Math.max(MIN_ZOOM_PERCENT, Math.min(MAX_ZOOM_PERCENT, zoomPercent)));
        }

        public void adjustZoom(String stateKey, double delta) {
            if (stateKey == null || delta == 0D) return;
            int current = getZoomPercent(stateKey);
            setZoomPercent(stateKey, current + (delta > 0D ? ZOOM_STEP_PERCENT : -ZOOM_STEP_PERCENT));
        }

        public boolean render(
                GuiGraphics graphics,
                String entityId,
                String stateKey,
                int boxX,
                int boxY,
                int boxW,
                int boxH,
                float guiScale,
                int offsetX,
                int offsetY,
                boolean hovered
        ) {
            ResourceLocation id = ResourceLocation.tryParse(entityId);
            if (id == null) return false;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            return render(graphics, type, id, stateKey, boxX, boxY, boxW, boxH, guiScale, offsetX, offsetY, hovered);
        }

        public boolean render(
                GuiGraphics graphics,
                EntityType<?> type,
                ResourceLocation id,
                String stateKey,
                int boxX,
                int boxY,
                int boxW,
                int boxH,
                float guiScale,
                int offsetX,
                int offsetY,
                boolean hovered
        ) {
            if (type == null || id == null || stateKey == null) return false;
            Entity entity = getOrCreateEntity(type, id);
            if (entity == null) return false;

            float entityWidth = Math.max(entity.getBbWidth(), 0.1f);
            float entityHeight = Math.max(entity.getBbHeight(), 0.1f);
            float safeWidth = boxW * fillRatio;
            float safeHeight = boxH * fillRatio;
            float autoScale = Math.min(safeWidth / entityWidth, safeHeight / entityHeight);
            autoScale = Math.min(autoScale, boxH * maxAutoScaleFactor);
            EntityPreviewState state = getState(stateKey);
            float scale = autoScale * state.getZoomPercent() / 100f;
            int renderX = Math.round(boxX + boxW / 2f);
            int renderY = Math.round(boxY + boxH / 2f + entityHeight * scale / 2f);
            float angle = state.updateRotation(hovered, DEFAULT_BASE_ROTATION_SPEED, rotationSpeedPercent, clockwise, DEFAULT_FRAME_LIMIT_NANOS);

            int scissorX1 = offsetX + (int) Math.ceil((boxX + 0.5f) * guiScale);
            int scissorY1 = offsetY + (int) Math.ceil((boxY + 0.5f) * guiScale);
            int scissorX2 = offsetX + (int) Math.floor((boxX + boxW - 0.5f) * guiScale);
            int scissorY2 = offsetY + (int) Math.floor((boxY + boxH - 0.5f) * guiScale);
            if (scissorX2 <= scissorX1 || scissorY2 <= scissorY1) return false;
            graphics.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
            graphics.pose().pushPose();

            float oldYRot = entity.getYRot();
            float oldYRotO = entity.yRotO;
            float oldXRot = entity.getXRot();
            float oldXRotO = entity.xRotO;
            float oldBody = 0f;
            float oldBodyO = 0f;
            float oldHead = 0f;
            float oldHeadO = 0f;

            try {
                graphics.pose().translate(renderX, renderY, 50D);
                graphics.pose().scale(scale, scale, -scale);
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180f));
                graphics.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(-10f));
                graphics.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f - angle));

                if (entity instanceof LivingEntity living) {
                    oldBody = living.yBodyRot;
                    oldBodyO = living.yBodyRotO;
                    oldHead = living.yHeadRot;
                    oldHeadO = living.yHeadRotO;
                    living.yBodyRot = 0f;
                    living.yBodyRotO = 0f;
                    living.yHeadRot = 0f;
                    living.yHeadRotO = 0f;
                }

                entity.setYRot(0f);
                entity.yRotO = 0f;
                entity.setXRot(0f);
                entity.xRotO = 0f;

                var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                dispatcher.setRenderShadow(false);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                Lighting.setupFor3DItems();
                RenderSystem.runAsFancy(() -> dispatcher.render(
                        entity, 0D, 0D, 0D, 0f, 1f, graphics.pose(), graphics.bufferSource(), 15728880
                ));
                graphics.bufferSource().endBatch();
                return true;
            } catch (Throwable ignored) {
                renderCache.remove(id.toString());
                renderFailed.add(id.toString());
                return false;
            } finally {
                Minecraft.getInstance().getEntityRenderDispatcher().setRenderShadow(true);
                Lighting.setupFor3DItems();
                entity.setYRot(oldYRot);
                entity.yRotO = oldYRotO;
                entity.setXRot(oldXRot);
                entity.xRotO = oldXRotO;
                if (entity instanceof LivingEntity living) {
                    living.yBodyRot = oldBody;
                    living.yBodyRotO = oldBodyO;
                    living.yHeadRot = oldHead;
                    living.yHeadRotO = oldHeadO;
                }
                graphics.pose().popPose();
                graphics.disableScissor();
            }
        }

        public void clear() {
            renderCache.clear();
            renderFailed.clear();
            states.clear();
        }

        public static void drawCheckerboard(GuiGraphics graphics, int x, int y, int width, int height) {
            graphics.fill(x, y, x + width, y + height, CHECKER_LIGHT);
            int rows = (height + CHECKER_SIZE - 1) / CHECKER_SIZE;
            int cols = (width + CHECKER_SIZE - 1) / CHECKER_SIZE;
            for (int row = 0; row < rows; row++) {
                for (int col = row & 1; col < cols; col += 2) {
                    int x1 = x + col * CHECKER_SIZE;
                    int y1 = y + row * CHECKER_SIZE;
                    graphics.fill(x1, y1, Math.min(x1 + CHECKER_SIZE, x + width), Math.min(y1 + CHECKER_SIZE, y + height), CHECKER_DARK);
                }
            }
        }

        private EntityPreviewState getState(String stateKey) {
            return states.computeIfAbsent(stateKey, ignored -> new EntityPreviewState(DEFAULT_ROTATION, DEFAULT_ZOOM_PERCENT));
        }

        private Entity getOrCreateEntity(EntityType<?> type, ResourceLocation id) {
            String key = id.toString();
            if (renderFailed.contains(key)) return null;
            Entity cached = renderCache.get(key);
            if (cached != null) return cached;
            if (Minecraft.getInstance().level == null) return null;
            try {
                Entity entity = type.create(Minecraft.getInstance().level);
                if (entity == null) {
                    renderFailed.add(key);
                    return null;
                }
                renderCache.put(key, entity);
                return entity;
            } catch (Throwable ignored) {
                renderFailed.add(key);
                return null;
            }
        }
    }

    private static final class EntityPreviewState {
        private float angle;
        private int zoomPercent;
        private long lastNanos;
        private boolean hovered;

        private EntityPreviewState(float defaultAngle, int defaultZoomPercent) {
            angle = defaultAngle;
            zoomPercent = defaultZoomPercent;
            lastNanos = System.nanoTime();
        }

        private float updateRotation(boolean hoveredNow, float baseSpeed, int speedPercent, boolean clockwise, long frameLimitNanos) {
            long now = System.nanoTime();
            if (hoveredNow && hovered) {
                long elapsedNanos = now - lastNanos;
                if (elapsedNanos > 0L && elapsedNanos <= frameLimitNanos) {
                    float elapsedSeconds = elapsedNanos / 1_000_000_000f;
                    float direction = clockwise ? 1f : -1f;
                    angle = (angle + elapsedSeconds * baseSpeed * speedPercent * direction / 100f) % 360f;
                }
            }
            hovered = hoveredNow;
            lastNanos = now;
            return angle;
        }

        private int getZoomPercent() {
            return zoomPercent;
        }

        private void setZoomPercent(int zoomPercent) {
            this.zoomPercent = zoomPercent;
        }
    }

    public static final class DragStateController<T> {
        private T type;
        private Object payload;

        public void start(T type, Object payload) {
            this.type = type;
            this.payload = payload;
        }

        public boolean isActive() {
            return type != null;
        }

        public T type() {
            return type;
        }

        public Object payload() {
            return payload;
        }

        public void clear() {
            type = null;
            payload = null;
        }
    }

    public static final class EditedEntryTracker<T> {
        private final Map<T, Long> editedOrder = new HashMap<>();
        private long sequence;

        public void refresh(
                Collection<T> entries,
                Predicate<T> editedPredicate
        ) {
            editedOrder.clear();

            for (T entry : entries) {
                if (editedPredicate.test(entry)) {
                    editedOrder.put(entry, 0L);
                }
            }
        }

        public boolean update(
                T entry,
                boolean edited
        ) {
            boolean wasEdited = editedOrder.containsKey(entry);

            if (edited) {
                if (!wasEdited) {
                    editedOrder.put(entry, ++sequence);
                    return true;
                }

                return false;
            }

            return editedOrder.remove(entry) != null;
        }

        public boolean isEdited(T entry) {
            return editedOrder.containsKey(entry);
        }

        public Comparator<T> comparator(Comparator<T> fallback) {
            return (left, right) -> {
                boolean leftEdited = isEdited(left);
                boolean rightEdited = isEdited(right);

                if (leftEdited != rightEdited) {
                    return leftEdited ? -1 : 1;
                }

                if (leftEdited) {
                    long leftOrder = editedOrder.getOrDefault(left, 0L);
                    long rightOrder = editedOrder.getOrDefault(right, 0L);
                    int recentCompare = Long.compare(rightOrder, leftOrder);

                    if (recentCompare != 0) {
                        return recentCompare;
                    }
                }

                return fallback.compare(left, right);
            };
        }

        public void clear() {
            editedOrder.clear();
        }
    }

    public static final class ToggleButton extends Button {
        private boolean value;
        private final Component onText;
        private final Component offText;
        private final Consumer<Boolean> responder;

        public ToggleButton(
                int x,
                int y,
                int width,
                int height,
                boolean value,
                Component onText,
                Component offText,
                Consumer<Boolean> responder
        ) {
            super(x, y, width, height, value ? onText : offText, ignored -> { }, DEFAULT_NARRATION);
            this.value = value;
            this.onText = Objects.requireNonNullElse(onText, Component.empty());
            this.offText = Objects.requireNonNullElse(offText, Component.empty());
            this.responder = responder == null ? ignored -> { } : responder;
        }

        public boolean value() {
            return value;
        }

        public void setValue(boolean value) {
            this.value = value;
            setMessage(value ? onText : offText);
        }

        @Override
        public void onPress() {
            setValue(!value);
            responder.accept(value);
        }
    }

    public static final class Dropdown extends Button {
        private final List<Component> options;
        private final Consumer<Integer> responder;
        private final Consumer<Dropdown> opener;
        private int selectedIndex;

        public Dropdown(
                int x,
                int y,
                int width,
                int height,
                List<Component> options,
                int selectedIndex,
                Consumer<Integer> responder
        ) {
            this(x, y, width, height, options, selectedIndex, responder, null);
        }

        public Dropdown(
                int x,
                int y,
                int width,
                int height,
                List<Component> options,
                int selectedIndex,
                Consumer<Integer> responder,
                Consumer<Dropdown> opener
        ) {
            super(x, y, width, height, messageAt(options, selectedIndex), ignored -> { }, DEFAULT_NARRATION);
            this.options = options == null ? List.of() : List.copyOf(options);
            this.responder = responder == null ? ignored -> { } : responder;
            this.opener = opener;
            this.selectedIndex = normalizeIndex(selectedIndex, this.options.size());
            setMessage(messageAt(this.options, this.selectedIndex));
        }

        public List<Component> options() {
            return options;
        }

        public int selectedIndex() {
            return selectedIndex;
        }

        public Component selected() {
            return messageAt(options, selectedIndex);
        }

        public void setSelectedIndex(int index) {
            selectedIndex = normalizeIndex(index, options.size());
            setMessage(messageAt(options, selectedIndex));
        }

        public void choose(int index) {
            if (options.isEmpty()) return;
            setSelectedIndex(index);
            responder.accept(selectedIndex);
        }

        @Override
        public void onPress() {
            if (options.isEmpty()) return;
            if (opener != null) {
                opener.accept(this);
            } else {
                choose(selectedIndex + 1);
            }
        }

        private static int normalizeIndex(int index, int size) {
            if (size <= 0) return -1;
            int normalized = index % size;
            return normalized < 0 ? normalized + size : normalized;
        }

        private static Component messageAt(List<Component> options, int index) {
            if (options == null || options.isEmpty()) return Component.empty();
            int normalized = normalizeIndex(index, options.size());
            return options.get(normalized);
        }
    }

    public static final class TabBar {
        private final List<Button> buttons = new ArrayList<>();
        private int selectedIndex;

        public List<Button> buttons() {
            return List.copyOf(buttons);
        }

        public int selectedIndex() {
            return selectedIndex;
        }

        public void build(
                int x,
                int y,
                int totalWidth,
                int height,
                List<Component> labels,
                int selected,
                Consumer<Integer> responder
        ) {
            buttons.clear();
            if (labels == null || labels.isEmpty()) {
                selectedIndex = -1;
                return;
            }
            selectedIndex = Math.max(0, Math.min(labels.size() - 1, selected));
            int baseWidth = Math.max(1, totalWidth / labels.size());
            int used = 0;
            for (int index = 0; index < labels.size(); index++) {
                int width = index == labels.size() - 1 ? totalWidth - used : baseWidth;
                int buttonIndex = index;
                Button button = Button.builder(labels.get(index), ignored -> {
                    selectedIndex = buttonIndex;
                    if (responder != null) responder.accept(buttonIndex);
                }).bounds(x + used, y, width, height).build();
                buttons.add(button);
                used += width;
            }
        }
    }
}
