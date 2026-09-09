package dev.xyat.kineticcore.api.client.selector;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reusable visual multi-selector for exact entity IDs. It edits a local draft
 * only: applying invokes the callback, while persistence remains the caller's
 * responsibility.
 */
public final class EntitySelectorScreen extends KineticScreen {
    private static final int COLS = 8;
    private static final int CELL_W = 68;
    private static final int CELL_H = 68;
    private static final int GRID_X = 42;
    private static final int GRID_Y = 67;
    private static final int VISIBLE_ROWS = 3;
    private static final int GRID_W = COLS * CELL_W;
    private static final int GRID_H = VISIBLE_ROWS * CELL_H;
    private static final int SCROLL_X = GRID_X + GRID_W + 6;
    private static final int SCROLL_W = 4;

    private final Screen parent;
    private final Consumer<List<String>> onApply;
    private final List<String> allEntityIds = new ArrayList<>();
    private final List<String> filteredEntityIds = new ArrayList<>();
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final Set<String> originalIds = new LinkedHashSet<>();
    private final Map<String, String> searchData = new HashMap<>();
    private final GridScrollController scroll = new GridScrollController();
    private final EntityPreviewRenderer previewRenderer = new EntityPreviewRenderer();

    private EditBox searchBox;
    private String searchQuery = "";
    private List<Component> deferredTooltip;

    public EntitySelectorScreen(
            Screen parent,
            Component title,
            Collection<String> initialEntityIds,
            Consumer<List<String>> onApply
    ) {
        super(title);
        this.parent = parent;
        this.onApply = onApply;
        if (initialEntityIds != null) {
            for (String value : initialEntityIds) {
                if (value != null && !value.isBlank()) selectedIds.add(value.trim());
            }
        }
        originalIds.addAll(selectedIds);

        ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .map(ResourceLocation::toString)
                .sorted(String::compareToIgnoreCase)
                .forEach(allEntityIds::add);
        for (String selected : selectedIds) {
            if (!allEntityIds.contains(selected)) allEntityIds.add(selected);
        }
        allEntityIds.sort(String::compareToIgnoreCase);
        buildSearchData();
        useCanvas(640, 360, 6);
    }

    @Override
    protected void buildUi() {
        searchBox = addRenderableWidget(new EditBox(
                font, GRID_X, 38, Math.min(GRID_W, 430), 20,
                Component.translatable("gui.kineticcore.entity_selector.search_hint")
        ));
        searchBox.setMaxLength(256);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(query -> {
            searchQuery = query == null ? "" : query;
            updateSearch(searchQuery);
        });

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.entity_selector.clear"),
                        ignored -> selectedIds.clear())
                .bounds(166, 325, 92, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.config.back"),
                        ignored -> onClose())
                .bounds(274, 325, 92, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.entity_selector.apply"),
                        ignored -> applyAndReturn())
                .bounds(382, 325, 92, 20).build());

        updateSearch(searchQuery);
    }

    private void buildSearchData() {
        searchData.clear();
        for (String id : allEntityIds) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            EntityType<?> type = location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
            String name = type == null ? id : type.getDescription().getString();
            String raw = id + " " + name;
            searchData.put(id, (raw + " " + KineticSearch.pinyin(raw)).toLowerCase(Locale.ROOT));
        }
    }

    private void updateSearch(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredEntityIds.clear();
        for (String id : allEntityIds) {
            if (normalized.isEmpty() || KineticSearch.match(
                    searchData.getOrDefault(id, id.toLowerCase(Locale.ROOT)), normalized)) {
                filteredEntityIds.add(id);
            }
        }
        filteredEntityIds.sort((left, right) -> {
            int selectedCompare = Boolean.compare(selectedIds.contains(right), selectedIds.contains(left));
            return selectedCompare != 0 ? selectedCompare : left.compareToIgnoreCase(right);
        });
        scroll.reset();
        updateScrollRange();
    }

    private void updateScrollRange() {
        int totalRows = (filteredEntityIds.size() + COLS - 1) / COLS;
        scroll.updateRange(Math.max(0, totalRows - VISIBLE_ROWS), totalRows, VISIBLE_ROWS);
    }

    @Override
    protected void renderCanvasBackground(
            @NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        deferredTooltip = null;
        GuiTheme.panel(graphics, 20, 12, 600, 342);
        graphics.drawCenteredString(font, title, canvasWidth / 2, 22, 0xFFFFAA00);
        GuiTheme.panelAlt(graphics, GRID_X - 3, GRID_Y - 3, GRID_W + 6, GRID_H + 6);
        renderGrid(graphics, mouseX, mouseY);
        GuiTheme.scrollbar(
                scroll, graphics, mouseX, mouseY,
                SCROLL_X, GRID_Y, SCROLL_W, GRID_H, 18
        );
        graphics.drawCenteredString(
                font,
                Component.translatable("gui.kineticcore.entity_selector.selected", Component.literal(String.valueOf(selectedIds.size())).withStyle(ChatFormatting.GREEN)),
                canvasWidth / 2,
                306,
                0xFFAAAAAA
        );
        if (filteredEntityIds.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.kineticcore.entity_selector.empty"),
                    GRID_X + GRID_W / 2,
                    GRID_Y + GRID_H / 2,
                    0xFFAAAAAA
            );
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
                Component.translatable("gui.kineticcore.entity_selector.search_hint").getString(),
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

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int smoothRow = scroll.smoothIndexOffset();
        int scrollShift = scroll.visualShift(CELL_H);
        int first = smoothRow * COLS;
        int last = Math.min(
                first + (VISIBLE_ROWS + 1) * COLS,
                filteredEntityIds.size()
        );
        enableCanvasScissor(
                graphics,
                GRID_X,
                GRID_Y,
                GRID_X + GRID_W,
                GRID_Y + GRID_H
        );
        for (int index = first; index < last; index++) {
            int local = index - first;
            int x = GRID_X + local % COLS * CELL_W;
            int y = GRID_Y + local / COLS * CELL_H - scrollShift;
            String id = filteredEntityIds.get(index);
            boolean selected = selectedIds.contains(id);
            boolean hovered = mouseX >= x && mouseX < x + CELL_W
                    && mouseY >= y && mouseY < y + CELL_H;

            EntityPreviewRenderer.drawCheckerboard(graphics, x + 2, y + 2, CELL_W - 4, CELL_H - 16);
            graphics.renderOutline(x, y, CELL_W, CELL_H,
                    selected ? 0xFF55FF55 : hovered ? 0xFFFFAA00 : 0xFF555555);
            if (selected) graphics.renderOutline(x + 1, y + 1, CELL_W - 2, CELL_H - 2, 0xFF55FF55);

            boolean rendered = previewRenderer.render(
                    graphics, id, "selector:" + id,
                    x + 3, y + 3, CELL_W - 6, CELL_H - 19,
                    canvasScale, canvasX, canvasY, hovered
            );
            if (!rendered) {
                graphics.drawCenteredString(font, "?", x + CELL_W / 2, y + 23, 0xFF777777);
            }
            graphics.drawCenteredString(
                    font,
                    GuiTheme.trim(font, entityName(id), CELL_W - 6),
                    x + CELL_W / 2,
                    y + CELL_H - 12,
                    selected ? 0xFF55FF55 : 0xFFE0E0E0
            );
            if (hovered) {
                deferredTooltip = List.of(
                        Component.literal(entityName(id)),
                        Component.literal(id),
                        Component.translatable(selected
                                ? "gui.kineticcore.entity_selector.remove_hint"
                                : "gui.kineticcore.entity_selector.add_hint")
                );
            }
        }
        graphics.disableScissor();
    }

    private String entityName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        EntityType<?> type = location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
        return type == null ? id : type.getDescription().getString();
    }

    private boolean inGrid(double mouseX, double mouseY) {
        return mouseX >= GRID_X && mouseX < GRID_X + GRID_W
                && mouseY >= GRID_Y && mouseY < GRID_Y + GRID_H;
    }

    private int entityIndex(double mouseX, double mouseY) {
        int column = (int) ((mouseX - GRID_X) / CELL_W);
        int row = (int) Math.floor(
                (mouseY - GRID_Y + scroll.visualShift(CELL_H)) / CELL_H
        );
        return scroll.smoothIndexOffset() * COLS + row * COLS + column;
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        boolean widgetHandled = super.canvasMouseClicked(mouseX, mouseY, button);
        if (button == 0 && scroll.beginDrag(
                mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_W, GRID_H, 18, 2)) return true;
        if (button == 0 && inGrid(mouseX, mouseY)) {
            int index = entityIndex(mouseX, mouseY);
            if (index >= 0 && index < filteredEntityIds.size()) {
                String id = filteredEntityIds.get(index);
                if (!selectedIds.add(id)) selectedIds.remove(id);
                return true;
            }
        }
        return widgetHandled;
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scroll.drag(mouseY, GRID_Y, GRID_H, 18)
                || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button)
                || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (Screen.hasControlDown() && inGrid(mouseX, mouseY)) {
            int index = entityIndex(mouseX, mouseY);
            if (index >= 0 && index < filteredEntityIds.size()) {
                String id = filteredEntityIds.get(index);
                previewRenderer.adjustZoom("selector:" + id, delta);
                return true;
            }
        }
        if (inGrid(mouseX, mouseY) && scroll.scroll(delta)) return true;
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        if (deferredTooltip != null) {
            showTooltip(deferredTooltip);
        }
    }

    private void applyAndReturn() {
        onApply.accept(new ArrayList<>(selectedIds));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        Minecraft client = Minecraft.getInstance();
        if (selectedIds.equals(originalIds)) {
            client.setScreen(parent);
            return;
        }
        client.setScreen(new ConfirmScreen(
                shouldApply -> {
                    if (shouldApply) {
                        applyAndReturn();
                    } else {
                        client.setScreen(parent);
                    }
                },
                Component.translatable("gui.kineticcore.config.unsaved_action.title"),
                Component.translatable("gui.kineticcore.entity_selector.unsaved")
        ));
    }

    @Override
    public void removed() {
        previewRenderer.clear();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class EntityPreviewRenderer {
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
            this(
                    DEFAULT_CACHE_SIZE,
                    DEFAULT_FILL_RATIO,
                    DEFAULT_MAX_AUTO_SCALE_FACTOR
            );
        }

        public EntityPreviewRenderer(
                int maxCacheSize,
                float fillRatio,
                float maxAutoScaleFactor
        ) {
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
            this.rotationSpeedPercent = Math.max(
                    0,
                    Math.min(500, rotationSpeedPercent)
            );
        }

        public void setClockwise(boolean clockwise) {
            this.clockwise = clockwise;
        }

        public int getZoomPercent(String stateKey) {
            return getState(stateKey).getZoomPercent();
        }

        public void setZoomPercent(String stateKey, int zoomPercent) {
            getState(stateKey).setZoomPercent(
                    Math.max(
                            MIN_ZOOM_PERCENT,
                            Math.min(MAX_ZOOM_PERCENT, zoomPercent)
                    )
            );
        }

        public void adjustZoom(String stateKey, double delta) {
            if (stateKey == null || delta == 0D) {
                return;
            }

            int current = getZoomPercent(stateKey);
            int next = current + (
                    delta > 0D
                            ? ZOOM_STEP_PERCENT
                            : -ZOOM_STEP_PERCENT
            );

            setZoomPercent(stateKey, next);
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

            if (id == null) {
                return false;
            }

            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);

            return render(
                    graphics,
                    type,
                    id,
                    stateKey,
                    boxX,
                    boxY,
                    boxW,
                    boxH,
                    guiScale,
                    offsetX,
                    offsetY,
                    hovered
            );
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
            if (type == null || id == null || stateKey == null) {
                return false;
            }

            Entity entity = getOrCreateEntity(type, id);

            if (entity == null) {
                return false;
            }

            float entityWidth = Math.max(entity.getBbWidth(), 0.1f);
            float entityHeight = Math.max(entity.getBbHeight(), 0.1f);
            float safeWidth = boxW * fillRatio;
            float safeHeight = boxH * fillRatio;

            float autoScale = Math.min(
                    safeWidth / entityWidth,
                    safeHeight / entityHeight
            );

            autoScale = Math.min(
                    autoScale,
                    boxH * maxAutoScaleFactor
            );

            EntityPreviewState state = getState(stateKey);

            float scale = autoScale
                    * state.getZoomPercent()
                    / 100f;

            int renderX = Math.round(
                    boxX + boxW / 2f
            );

            int renderY = Math.round(
                    boxY
                            + boxH / 2f
                            + entityHeight * scale / 2f
            );

            float angle = state.updateRotation(
                    hovered,
                    DEFAULT_BASE_ROTATION_SPEED,
                    rotationSpeedPercent,
                    clockwise,
                    DEFAULT_FRAME_LIMIT_NANOS
            );

            int scissorX1 = (int) (boxX * guiScale) + offsetX;
            int scissorY1 = (int) (boxY * guiScale) + offsetY;
            int scissorX2 = (int) ((boxX + boxW) * guiScale) + offsetX;
            int scissorY2 = (int) ((boxY + boxH) * guiScale) + offsetY;

            graphics.enableScissor(
                    scissorX1,
                    scissorY1,
                    scissorX2,
                    scissorY2
            );

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
                graphics.pose().translate(
                        renderX,
                        renderY,
                        50D
                );

                graphics.pose().scale(
                        scale,
                        scale,
                        -scale
                );

                graphics.pose().mulPose(
                        com.mojang.math.Axis.ZP.rotationDegrees(180f)
                );

                graphics.pose().mulPose(
                        com.mojang.math.Axis.XP.rotationDegrees(-10f)
                );

                graphics.pose().mulPose(
                        com.mojang.math.Axis.YP.rotationDegrees(
                                180f - angle
                        )
                );

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

                var dispatcher = Minecraft.getInstance()
                        .getEntityRenderDispatcher();

                dispatcher.setRenderShadow(false);

                RenderSystem.setShaderColor(
                        1f,
                        1f,
                        1f,
                        1f
                );

                Lighting.setupFor3DItems();

                RenderSystem.runAsFancy(() ->
                        dispatcher.render(
                                entity,
                                0D,
                                0D,
                                0D,
                                0f,
                                1f,
                                graphics.pose(),
                                graphics.bufferSource(),
                                15728880
                        )
                );

                graphics.bufferSource().endBatch();
                return true;
            } catch (Throwable ignored) {
                renderCache.remove(id.toString());
                renderFailed.add(id.toString());
                return false;
            } finally {
                Minecraft.getInstance()
                        .getEntityRenderDispatcher()
                        .setRenderShadow(true);

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

        public static void drawCheckerboard(
                GuiGraphics graphics,
                int x,
                int y,
                int width,
                int height
        ) {
            graphics.fill(
                    x,
                    y,
                    x + width,
                    y + height,
                    CHECKER_LIGHT
            );

            int rows = (height + CHECKER_SIZE - 1) / CHECKER_SIZE;
            int cols = (width + CHECKER_SIZE - 1) / CHECKER_SIZE;

            for (int row = 0; row < rows; row++) {
                for (int col = row & 1; col < cols; col += 2) {
                    int x1 = x + col * CHECKER_SIZE;
                    int y1 = y + row * CHECKER_SIZE;

                    graphics.fill(
                            x1,
                            y1,
                            Math.min(x1 + CHECKER_SIZE, x + width),
                            Math.min(y1 + CHECKER_SIZE, y + height),
                            CHECKER_DARK
                    );
                }
            }
        }

        private EntityPreviewState getState(String stateKey) {
            return states.computeIfAbsent(
                    stateKey,
                    ignored -> new EntityPreviewState(
                            DEFAULT_ROTATION,
                            DEFAULT_ZOOM_PERCENT
                    )
            );
        }

        private Entity getOrCreateEntity(
                EntityType<?> type,
                ResourceLocation id
        ) {
            String key = id.toString();

            if (renderFailed.contains(key)) {
                return null;
            }

            Entity cached = renderCache.get(key);

            if (cached != null) {
                return cached;
            }

            if (Minecraft.getInstance().level == null) {
                return null;
            }

            try {
                Entity entity = type.create(
                        Minecraft.getInstance().level
                );

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

        public EntityPreviewState(float defaultAngle, int defaultZoomPercent) {
            this.angle = defaultAngle;
            this.zoomPercent = defaultZoomPercent;
            this.lastNanos = System.nanoTime();
        }

        public float updateRotation(
                boolean hoveredNow,
                float baseSpeed,
                int speedPercent,
                boolean clockwise,
                long frameLimitNanos
        ) {
            long now = System.nanoTime();

            if (hoveredNow && hovered) {
                long elapsedNanos = now - lastNanos;

                if (elapsedNanos > 0L && elapsedNanos <= frameLimitNanos) {
                    float elapsedSeconds = elapsedNanos / 1_000_000_000f;
                    float direction = clockwise ? 1f : -1f;

                    angle = (
                            angle
                                    + elapsedSeconds
                                    * baseSpeed
                                    * speedPercent
                                    * direction
                                    / 100f
                    ) % 360f;
                }
            }

            hovered = hoveredNow;
            lastNanos = now;
            return angle;
        }

        public int getZoomPercent() {
            return zoomPercent;
        }

        public void setZoomPercent(int zoomPercent) {
            this.zoomPercent = zoomPercent;
        }
    }
}
