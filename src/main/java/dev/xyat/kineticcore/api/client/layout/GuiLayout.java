package dev.xyat.kineticcore.api.client.layout;

import java.util.ArrayList;
import java.util.List;

public final class GuiLayout {
    public enum Level {
        LARGE,
        NORMAL,
        SMALL,
        COMPACT
    }

    public enum Aspect {
        PORTRAIT,
        LANDSCAPE,
        ULTRAWIDE
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            width = Math.max(0, width);
            height = Math.max(0, height);
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }

        public Rect inset(int amount) {
            int safe = Math.max(0, amount);
            return new Rect(
                    x + safe,
                    y + safe,
                    Math.max(0, width - safe * 2),
                    Math.max(0, height - safe * 2)
            );
        }

        public Rect inset(int horizontal, int vertical) {
            int xInset = Math.max(0, horizontal);
            int yInset = Math.max(0, vertical);
            return new Rect(
                    x + xInset,
                    y + yInset,
                    Math.max(0, width - xInset * 2),
                    Math.max(0, height - yInset * 2)
            );
        }

        public Rect move(int deltaX, int deltaY) {
            return new Rect(x + deltaX, y + deltaY, width, height);
        }
    }

    public record SafeArea(int left, int top, int right, int bottom) {
        public static SafeArea of(int screenWidth, int screenHeight, int margin) {
            int safeWidth = Math.max(1, screenWidth);
            int safeHeight = Math.max(1, screenHeight);
            int maxHorizontalMargin = Math.max(0, (safeWidth - 1) / 2);
            int maxVerticalMargin = Math.max(0, (safeHeight - 1) / 2);
            int horizontalMargin = Math.min(Math.max(0, margin), maxHorizontalMargin);
            int verticalMargin = Math.min(Math.max(0, margin), maxVerticalMargin);
            return new SafeArea(
                    horizontalMargin,
                    verticalMargin,
                    safeWidth - horizontalMargin,
                    safeHeight - verticalMargin
            );
        }

        public int width() {
            return Math.max(1, right - left);
        }

        public int height() {
            return Math.max(1, bottom - top);
        }

        public Rect rect() {
            return new Rect(left, top, width(), height());
        }

        public boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    public record Metrics(
            Level level,
            Aspect aspect,
            int availableWidth,
            int availableHeight,
            float designWidth,
            float designHeight,
            float fitScale,
            float aspectRatio
    ) {
        public boolean isPortrait() {
            return aspect == Aspect.PORTRAIT;
        }

        public boolean isUltrawide() {
            return aspect == Aspect.ULTRAWIDE;
        }

        public boolean isCompact() {
            return level == Level.COMPACT;
        }
    }

    public record Split(Rect first, Rect second) {
    }

    private GuiLayout() {
    }

    public static Metrics measure(
            int availableWidth,
            int availableHeight,
            float designWidth,
            float designHeight
    ) {
        int safeWidth = Math.max(1, availableWidth);
        int safeHeight = Math.max(1, availableHeight);
        float safeDesignWidth = Math.max(1f, designWidth);
        float safeDesignHeight = Math.max(1f, designHeight);
        float fitScale = Math.min(safeWidth / safeDesignWidth, safeHeight / safeDesignHeight);

        Level level;
        if (fitScale >= 1.25f) {
            level = Level.LARGE;
        } else if (fitScale >= 0.95f) {
            level = Level.NORMAL;
        } else if (fitScale >= 0.72f) {
            level = Level.SMALL;
        } else {
            level = Level.COMPACT;
        }

        float aspectRatio = safeWidth / (float) safeHeight;
        Aspect aspect;
        if (aspectRatio < 0.9f) {
            aspect = Aspect.PORTRAIT;
        } else if (aspectRatio >= 2.0f) {
            aspect = Aspect.ULTRAWIDE;
        } else {
            aspect = Aspect.LANDSCAPE;
        }

        return new Metrics(
                level,
                aspect,
                safeWidth,
                safeHeight,
                safeDesignWidth,
                safeDesignHeight,
                fitScale,
                aspectRatio
        );
    }

    public static List<Rect> rows(Rect area, int count, int gap) {
        return divide(area, Math.max(0, count), Math.max(0, gap), false);
    }

    public static List<Rect> columns(Rect area, int count, int gap) {
        return divide(area, Math.max(0, count), Math.max(0, gap), true);
    }

    public static Split splitHorizontal(Rect area, int firstWidth, int gap) {
        int safeGap = Math.max(0, gap);
        int available = Math.max(0, area.width() - safeGap);
        int leftWidth = Math.max(0, Math.min(firstWidth, available));
        return new Split(
                new Rect(area.x(), area.y(), leftWidth, area.height()),
                new Rect(area.x() + leftWidth + safeGap, area.y(), available - leftWidth, area.height())
        );
    }

    public static Split splitVertical(Rect area, int firstHeight, int gap) {
        int safeGap = Math.max(0, gap);
        int available = Math.max(0, area.height() - safeGap);
        int topHeight = Math.max(0, Math.min(firstHeight, available));
        return new Split(
                new Rect(area.x(), area.y(), area.width(), topHeight),
                new Rect(area.x(), area.y() + topHeight + safeGap, area.width(), available - topHeight)
        );
    }

    public static List<Rect> weightedColumns(Rect area, int gap, int... weights) {
        return weighted(area, gap, true, weights);
    }

    public static List<Rect> weightedRows(Rect area, int gap, int... weights) {
        return weighted(area, gap, false, weights);
    }

    private static List<Rect> divide(Rect area, int count, int gap, boolean horizontal) {
        if (count <= 0) return List.of();
        int total = horizontal ? area.width() : area.height();
        int usable = Math.max(0, total - gap * (count - 1));
        int base = usable / count;
        int remainder = usable % count;
        List<Rect> result = new ArrayList<>(count);
        int cursor = horizontal ? area.x() : area.y();

        for (int index = 0; index < count; index++) {
            int size = base + (index < remainder ? 1 : 0);
            if (horizontal) {
                result.add(new Rect(cursor, area.y(), size, area.height()));
            } else {
                result.add(new Rect(area.x(), cursor, area.width(), size));
            }
            cursor += size + gap;
        }
        return List.copyOf(result);
    }

    private static List<Rect> weighted(Rect area, int gap, boolean horizontal, int... weights) {
        if (weights == null || weights.length == 0) return List.of();
        int safeGap = Math.max(0, gap);
        int totalWeight = 0;
        for (int weight : weights) totalWeight += Math.max(0, weight);
        if (totalWeight <= 0) return divide(area, weights.length, safeGap, horizontal);

        int total = horizontal ? area.width() : area.height();
        int usable = Math.max(0, total - safeGap * (weights.length - 1));
        List<Rect> result = new ArrayList<>(weights.length);
        int cursor = horizontal ? area.x() : area.y();
        int consumed = 0;

        for (int index = 0; index < weights.length; index++) {
            int size;
            if (index == weights.length - 1) {
                size = Math.max(0, usable - consumed);
            } else {
                size = Math.round(usable * (Math.max(0, weights[index]) / (float) totalWeight));
                size = Math.max(0, Math.min(size, usable - consumed));
            }
            if (horizontal) {
                result.add(new Rect(cursor, area.y(), size, area.height()));
            } else {
                result.add(new Rect(area.x(), cursor, area.width(), size));
            }
            cursor += size + safeGap;
            consumed += size;
        }
        return List.copyOf(result);
    }
}
