package dev.xyat.kineticcore.feature.setspawn.client.gui;

import net.minecraft.ChatFormatting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.AutoCompleteBox;
import dev.xyat.kineticcore.feature.setspawn.network.SetSpawnNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SetSpawnScreen extends KineticScreen {
    private static final Map<String, String> ZH_CN_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> ZH_CN_LOADED_NAMESPACES = ConcurrentHashMap.newKeySet();

    private final String playerDim;
    private final String playerBiome;
    private final String playerStruct;

    private boolean globalEnable;
    private boolean dimEnable;
    private boolean biomeEnable;
    private boolean structEnable;

    private final List<String> dims;
    private final List<String> biomes;
    private final List<String> structs;

    private final List<String> serverDictDims;
    private final List<String> serverDictBiomes;
    private final List<String> serverDictStructs;

    private int currentTab = 0;

    private AutoCompleteBox activeInput;
    private StringListWidget activeListWidget;

    public SetSpawnScreen(SetSpawnNetwork.OpenSetSpawnGuiPacket packet) {
        super(Component.translatable("gui.kineticcore.setspawn.title"));
        useCanvas(
                640f,
                360f,
                6
        );

        this.globalEnable = packet.globalEnable();
        this.dimEnable = packet.dimEnable();
        this.biomeEnable = packet.biomeEnable();
        this.structEnable = packet.structEnable();

        this.dims = new ArrayList<>(packet.dims());
        this.biomes = new ArrayList<>(packet.biomes());
        this.structs = new ArrayList<>(packet.structs());

        this.playerDim = packet.playerDim();
        this.playerBiome = packet.playerBiome();
        this.playerStruct = packet.playerStruct();

        this.serverDictDims = packet.allDims() != null ? packet.allDims() : new ArrayList<>();
        this.serverDictBiomes = packet.allBiomes() != null ? packet.allBiomes() : new ArrayList<>();
        this.serverDictStructs = packet.allStructs() != null ? packet.allStructs() : new ArrayList<>();
    }

    @Override
    protected void buildUi() {
        int panelW = this.canvasWidth - 40;
        int startX = 20;
        int topY = 16;

        Button btnDim = Button.builder(Component.translatable("gui.kineticcore.setspawn.dim"), b -> switchTab(0))
                .bounds(startX, topY, 80, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.kineticcore.setspawn.tooltip.dim")))
                .build();
        btnDim.active = currentTab != 0;
        this.addRenderableWidget(btnDim);

        Button btnBiome = Button.builder(Component.translatable("gui.kineticcore.setspawn.biome"), b -> switchTab(1))
                .bounds(startX + 85, topY, 80, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.kineticcore.setspawn.tooltip.biome")))
                .build();
        btnBiome.active = currentTab != 1;
        this.addRenderableWidget(btnBiome);

        Button btnStruct = Button.builder(Component.translatable("gui.kineticcore.setspawn.struct"), b -> switchTab(2))
                .bounds(startX + 170, topY, 80, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.kineticcore.setspawn.tooltip.struct")))
                .build();
        btnStruct.active = currentTab != 2;
        this.addRenderableWidget(btnStruct);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.hud_editor.save"), b -> SetSpawnNetwork.CHANNEL.sendToServer(new SetSpawnNetwork.SaveSetSpawnPacket(globalEnable, dimEnable, dims, biomeEnable, biomes, structEnable, structs))).bounds(startX + panelW - 145, topY, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.config.back"), b -> this.onClose())
                .bounds(startX + panelW - 60, topY, 60, 20).build());

        int searchY = 46;
        int switchesW = 270;
        int inputW = panelW - switchesW - 5;

        String hintKey = currentTab == 0 ? "gui.kineticcore.setspawn.hint_dim" : (currentTab == 1 ? "gui.kineticcore.setspawn.hint_biome" : "gui.kineticcore.setspawn.hint_struct");
        activeInput = new AutoCompleteBox(this.font, startX, searchY, inputW, 20, Component.empty(), this::getActiveDict) {
            @Override
            public void renderWidget(@NotNull GuiGraphics g, int mx, int my, float pt) {
                super.renderWidget(g, mx, my, pt);
                if (!this.isFocused() && this.getValue().isEmpty()) {
                    g.drawString(Minecraft.getInstance().font, Component.translatable(hintKey), this.getX() + 4, this.getY() + (this.height - 9) / 2 + 1, 0xFFAAAAAA, false);
                }
            }
        };
        this.addRenderableWidget(activeInput);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.setspawn.global_btn", Component.translatable(globalEnable ? "gui.kineticcore.setspawn.enable" : "gui.kineticcore.setspawn.disable").withStyle(globalEnable ? ChatFormatting.GREEN : ChatFormatting.RED)), b -> {
            globalEnable = !globalEnable;
            b.setMessage(Component.translatable("gui.kineticcore.setspawn.global_btn", Component.translatable(globalEnable ? "gui.kineticcore.setspawn.enable" : "gui.kineticcore.setspawn.disable").withStyle(globalEnable ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }).bounds(startX + inputW + 5, searchY, 85, 20).tooltip(Tooltip.create(Component.translatable("gui.kineticcore.setspawn.tooltip.global"))).build());

        boolean currentEnable = currentTab == 0 ? dimEnable : (currentTab == 1 ? biomeEnable : structEnable);
        String tabPrefix = currentTab == 0 ? "gui.kineticcore.setspawn.dim_btn" : (currentTab == 1 ? "gui.kineticcore.setspawn.biome_btn" : "gui.kineticcore.setspawn.struct_btn");
        Component tabTooltip = Component.translatable(currentTab == 0 ? "gui.kineticcore.setspawn.tooltip.dim" : (currentTab == 1 ? "gui.kineticcore.setspawn.tooltip.biome" : "gui.kineticcore.setspawn.tooltip.struct"));

        this.addRenderableWidget(Button.builder(Component.translatable(tabPrefix, Component.translatable(currentEnable ? "gui.kineticcore.setspawn.enable" : "gui.kineticcore.setspawn.disable").withStyle(currentEnable ? ChatFormatting.GREEN : ChatFormatting.RED)), b -> {
            if (currentTab == 0) dimEnable = !dimEnable;
            else if (currentTab == 1) biomeEnable = !biomeEnable;
            else structEnable = !structEnable;
            boolean newState = currentTab == 0 ? dimEnable : (currentTab == 1 ? biomeEnable : structEnable);
            b.setMessage(Component.translatable(tabPrefix, Component.translatable(newState ? "gui.kineticcore.setspawn.enable" : "gui.kineticcore.setspawn.disable").withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }).bounds(startX + inputW + 95, searchY, 85, 20).tooltip(Tooltip.create(tabTooltip)).build());

        String currentEnv = currentTab == 0 ? playerDim : (currentTab == 1 ? playerBiome : playerStruct);
        boolean isOverworldDim = currentTab == 0 && "minecraft:overworld".equals(currentEnv);

        Button envBtn = Button.builder(Component.translatable("gui.kineticcore.setspawn.add_current_single"), b -> {
            if (currentEnv.equals("none") || currentEnv.isEmpty() || isOverworldDim) return;
            addToList(currentEnv);
        }).bounds(startX + panelW - 85, searchY, 85, 20).tooltip(Tooltip.create(Component.literal(toDisplayEntry(currentEnv, getCurrentPrefix())))).build();

        if (currentEnv.equals("none") || currentEnv.isEmpty() || isOverworldDim) envBtn.active = false;
        this.addRenderableWidget(envBtn);

        int listY = 76;
        int listH = this.canvasHeight - listY - 16;
        List<String> activeData = currentTab == 0 ? dims : (currentTab == 1 ? biomes : structs);

        activeListWidget = new StringListWidget(this.minecraft, panelW, listH, listY, listY + listH, 20, activeData);
        activeListWidget.setLeftPos(startX);
        this.addWidget(activeListWidget);
    }

    public void handleSaveResult(boolean success) {
        if (success) {
            GuiOverlay.toast(Component.translatable("gui.kineticcore.setspawn.saved_toast"));
            this.onClose();
        } else {
            GuiOverlay.toast(Component.translatable("gui.kineticcore.setspawn.save_invalid_toast"));
        }
    }

    private void switchTab(int tab) {
        this.currentTab = tab;
        this.rebuildWidgets();
    }

    private void addToList(String rawValue) {
        String val = stripDisplayText(rawValue);
        if (!val.isEmpty()) {
            if (currentTab == 0 && val.equals("minecraft:overworld")) return;

            List<String> targetList = currentTab == 0 ? dims : (currentTab == 1 ? biomes : structs);
            if (!targetList.contains(val)) {
                targetList.add(val);
                if (activeListWidget != null) activeListWidget.refresh();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (activeInput != null) {
            String val = activeInput.getValue();
            if (val.contains(" - ")) {
                addToList(val);
                activeInput.setValue("");
            }
        }
    }

    private String stripDisplayText(String rawValue) {
        if (rawValue == null) return "";
        String val = rawValue.trim();
        int split = val.indexOf(" - ");
        if (split >= 0) val = val.substring(0, split).trim();
        return val;
    }

    private String getCurrentPrefix() {
        if (currentTab == 0) return "dimension";
        if (currentTab == 1) return "biome";
        return "structure";
    }

    private String toDisplayEntry(String id, String prefix) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) return id;
        String translated = getChineseTranslation(prefix, loc);
        if (translated.isEmpty()) return id;
        return id + " - " + translated;
    }

    private String getChineseTranslation(String prefix, ResourceLocation loc) {
        String key = prefix + "." + loc.getNamespace() + "." + loc.getPath();
        String selectedLanguage = safeClientTranslate(key);
        if (isValidChineseTranslation(key, selectedLanguage)) {
            return selectedLanguage;
        }

        String zhCn = readZhCnTranslation(loc.getNamespace(), key);
        if (isValidChineseTranslation(key, zhCn)) {
            return zhCn;
        }

        if ("structure".equals(prefix)) {
            for (String fallbackKey : getStructureFallbackKeys(loc)) {
                selectedLanguage = safeClientTranslate(fallbackKey);
                if (isValidChineseTranslation(fallbackKey, selectedLanguage)) {
                    return selectedLanguage;
                }

                zhCn = readZhCnTranslation(loc.getNamespace(), fallbackKey);
                if (isValidChineseTranslation(fallbackKey, zhCn)) {
                    return zhCn;
                }
            }
        }

        return "";
    }

    private String safeClientTranslate(String key) {
        try {
            return I18n.get(key);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readZhCnTranslation(String namespace, String key) {
        loadZhCnNamespace(namespace);
        return ZH_CN_CACHE.getOrDefault(key, "");
    }

    private void loadZhCnNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) return;
        if (!ZH_CN_LOADED_NAMESPACES.add(namespace)) return;

        try {
            ResourceLocation langFile = new ResourceLocation(namespace, "lang/zh_cn.json");
            Minecraft.getInstance().getResourceManager().getResource(langFile).ifPresent(this::loadZhCnResource);
        } catch (Exception ignored) {
        }
    }

    private void loadZhCnResource(Resource resource) {
        try (InputStream input = resource.open(); InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                String value = entry.getValue().getAsString();
                if (containsChinese(value)) {
                    ZH_CN_CACHE.put(entry.getKey(), value);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isValidChineseTranslation(String key, String value) {
        if (value == null || value.isBlank()) return false;
        if (value.equals(key)) return false;
        if (value.contains("%")) return false;
        return containsChinese(value);
    }

    private boolean containsChinese(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= '一' && c <= '鿿') || (c >= '㐀' && c <= '䶿') || (c >= '豈' && c <= '﫿')) {
                return true;
            }
        }
        return false;
    }

    private List<String> getStructureFallbackKeys(ResourceLocation loc) {
        List<String> keys = new ArrayList<>();
        String namespace = loc.getNamespace();
        String path = loc.getPath();

        if (path.startsWith("village_")) {
            keys.add("structure." + namespace + ".village");
        }
        if (path.startsWith("ruined_portal_")) {
            keys.add("structure." + namespace + ".ruined_portal");
        }
        if (path.startsWith("ocean_ruin_")) {
            keys.add("structure." + namespace + ".ocean_ruin");
        }
        if (path.startsWith("mineshaft_")) {
            keys.add("structure." + namespace + ".mineshaft");
        }
        if (path.startsWith("shipwreck_")) {
            keys.add("structure." + namespace + ".shipwreck");
        }

        return keys;
    }

    private List<String> getActiveDict() {
        if (currentTab == 0) return getDimDict();
        if (currentTab == 1) return getBiomeDict();
        return getStructDict();
    }

    private List<String> getDimDict() {
        return this.serverDictDims.stream()
                .filter(id -> !id.equals("minecraft:overworld"))
                .map(id -> toDisplayEntry(id, "dimension"))
                .collect(Collectors.toList());
    }

    private List<String> getBiomeDict() {
        return this.serverDictBiomes.stream()
                .map(id -> toDisplayEntry(id, "biome"))
                .collect(Collectors.toList());
    }

    private List<String> getStructDict() {
        return this.serverDictStructs.stream()
                .map(id -> toDisplayEntry(id, "structure"))
                .collect(Collectors.toList());
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        GuiTheme.shadow(g, this.canvasWidth, this.canvasHeight);

        int panelW = this.canvasWidth - 40;
        int startX = 20;
        int listY = 76;
        int listH = this.canvasHeight - listY - 16;

        GuiTheme.panel(g, 10, 8, this.canvasWidth - 20, this.canvasHeight - 16);

        g.fill(16, 40, this.canvasWidth - 16, 41, 0xFF444444);
        g.fill(16, 71, this.canvasWidth - 16, 72, 0xFF444444);

        GuiTheme.panelAlt(g, startX - 2, listY - 2, panelW + 4, listH + 4);

        renderScaledList(activeListWidget, g, mx, my, pt);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        activeInput.renderSuggestions(g, mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (activeInput != null && activeInput.isFocused() && !activeInput.getValue().isEmpty()) {
                addToList(activeInput.getValue());
                activeInput.setValue("");
                return true;
            }
        }
        if (activeInput != null && activeInput.handleKeyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean canvasMouseClicked(double mx, double my, int btn) {
        if (activeInput != null) {
            if (!activeInput.isMouseOver(mx, my)) {
                activeInput.setFocused(false);
                if (this.getFocused() == activeInput) this.setFocused(null);
            } else {
                this.setFocused(activeInput);
            }
            if (activeInput.handleMouseClick(mx, my)) return true;
        }
        return super.canvasMouseClicked(mx, my, btn);
    }

    @Override
    protected boolean canvasMouseReleased(double mx, double my, int btn) {
        if (activeInput != null && activeInput.handleMouseReleased(btn)) return true;
        return super.canvasMouseReleased(mx, my, btn);
    }

    @Override
    protected boolean canvasMouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (activeInput != null && activeInput.handleMouseDragged(my)) return true;
        return super.canvasMouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    protected boolean canvasMouseScrolled(double mx, double my, double d) {
        if (activeInput != null && activeInput.handleMouseScrolled(d)) return true;
        return super.canvasMouseScrolled(mx, my, d);
    }

    class StringListWidget extends ObjectSelectionList<StringListWidget.Entry> {
        private final int listTop;
        private final int listBottom;
        private final List<String> backingList;

        public StringListWidget(Minecraft mc, int w, int h, int t, int b, int ih, List<String> backingList) {
            super(mc, w, h, t, b, ih);
            this.listTop = t;
            this.listBottom = b;
            this.backingList = backingList;
            setRenderBackground(false);
            setRenderTopAndBottom(false);
            refresh();
        }

        @Override
        public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
            super.render(g, mx, my, pt);
            if (this.getMaxScroll() > 0) {
                int barX = this.getScrollbarPosition();
                int height = listBottom - listTop;
                int thumbH = Math.max(20, (int) ((float) height * height / this.getMaxPosition()));
                GuiTheme.scrollbar(
                        g,
                        mx,
                        my,
                        barX,
                        listTop,
                        4,
                        height,
                        thumbH,
                        (int) (double) this.getMaxScroll(),
                        this.getScrollAmount(),
                        false
                );
            }
        }

        public void refresh() {
            clearEntries();
            for (int i = 0; i < backingList.size(); i++) {
                addEntry(new Entry(i, backingList.get(i), backingList));
            }
        }

        @Override
        public int getRowLeft() {
            return this.getLeft() + 2;
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getLeft() + this.width - 8;
        }

        class Entry extends ObjectSelectionList.Entry<Entry> {
            private final int index;
            private final String text;
            private final List<String> targetList;

            public Entry(int index, String text, List<String> targetList) {
                this.index = index;
                this.text = text;
                this.targetList = targetList;
            }

            @Override
            public void render(@NotNull GuiGraphics g, int index, int t, int l, int w, int h, int mx, int my, boolean hv, float pt) {
                int bgColor = hv ? 0x88777777 : ((index % 2 == 0) ? 0x88333333 : 0x881C1C1C);
                g.fill(l, t, l + w, t + h - 1, bgColor);

                int delW = 14;
                int delX = l + w - delW - 6;
                int delY = t + (h - 1 - delW) / 2;
                boolean delHover = mx >= delX && mx < delX + delW && my >= delY && my < delY + delW;

                g.fill(delX, delY, delX + delW, delY + delW, delHover ? 0xFFFF3333 : 0xFFCC0000);
                g.drawCenteredString(Minecraft.getInstance().font, "✕", delX + delW / 2 + 1, delY + 3, 0xFFFFFF);

                String disp = toDisplayEntry(this.text, getCurrentPrefix());
                int maxW = delX - (l + 4) - 8;
                if (Minecraft.getInstance().font.width(disp) > maxW) {
                    disp = Minecraft.getInstance().font.plainSubstrByWidth(disp, maxW - 10) + "...";
                }

                int textY = t + (h - 1 - Minecraft.getInstance().font.lineHeight) / 2 + 1;
                g.drawString(Minecraft.getInstance().font, disp, l + 6, textY, 0xFFFFFF, false);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int btn) {
                if (btn == 0) {
                    int delW = 14;
                    int delX = getLeft() + 2 + getRowWidth() - delW - 6;
                    if (mx >= delX && mx < delX + delW) {
                        targetList.remove(index);
                        refresh();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public @NotNull Component getNarration() {
                return Component.empty();
            }
        }
    }
}
