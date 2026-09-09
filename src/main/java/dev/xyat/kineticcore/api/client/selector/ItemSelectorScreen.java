package dev.xyat.kineticcore.api.client.selector;

import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ItemSelectorScreen extends KineticScreen {
    public int getCachedVisibleCols() {
        return cachedVisibleCols;
    }

    public int getCachedVisibleRows() {
        return cachedVisibleRows;
    }

    public enum SelectionType {
        ITEM,
        TAG,
        MOD
    }

    public record Selection(SelectionType type, ItemStack stack, String value) {
        public static Selection item(ItemStack stack) {
            return new Selection(SelectionType.ITEM, stack == null ? ItemStack.EMPTY : stack.copy(), "");
        }

        public static Selection tag(String value) {
            return new Selection(SelectionType.TAG, ItemStack.EMPTY, value == null ? "" : value);
        }

        public static Selection mod(String value) {
            return new Selection(SelectionType.MOD, ItemStack.EMPTY, value == null ? "" : value);
        }

        public boolean isItem() {
            return type == SelectionType.ITEM && stack != null && !stack.isEmpty();
        }

        public boolean isTag() {
            return type == SelectionType.TAG && value != null && !value.isBlank();
        }

        public boolean isMod() {
            return type == SelectionType.MOD && value != null && !value.isBlank();
        }
    }

    private record DisplayCacheKey(int mode, String query, int filterType, String filterValue, String categoryKey) {
    }

    private enum CategoryType {
        MODE,
        VANILLA,
        MOD,
        HEADER
    }

    private record CategoryEntry(CategoryType type, int mode, String key, Component label) {
        boolean selectable() {
            return type != CategoryType.HEADER;
        }
    }

    private record VisibleSlot(int displayIndex, ItemStack stack, int x, int y) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + SLOT_SIZE
                    && mouseY >= y && mouseY < y + SLOT_SIZE;
        }
    }

    private final Screen parent;
    private final Consumer<Selection> onSelect;
    private final List<ItemSearchIndex.CachedItem> invItems = new ArrayList<>();
    private List<ItemSearchIndex.CachedItem> displayList = new ArrayList<>();

    private static String rememberedSearch = "";
    private static int rememberedMode = 0;
    private static String rememberedFilterValue = null;
    private static int rememberedFilterType = 0;
    private static String rememberedCategoryKey = null;

    private EditBox searchBox;
    private int mode = rememberedMode;
    private final GridScrollController mainScroll =
            new GridScrollController();

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 1;
    private static final int CELL_SIZE = SLOT_SIZE + SLOT_GAP;
    private static final int FIXED_GRID_COLS = 25;
    private static final int FIXED_GRID_ROWS = 16;
    private static final int CATEGORY_WIDTH = 132;
    private static final int CATEGORY_BUTTON_SHIFT_X = -4;
    private static final int CATEGORY_BUTTON_WIDTH = 140;
    private static final int CATEGORY_SCROLL_GAP = 2;
    private static final int CATEGORY_GAP = 5;
    private static final int GRID_SHIFT_LEFT = -5;
    private static final int CATEGORY_SCROLLBAR_SHIFT_X = 1;
    private static final int CATEGORY_SCROLLBAR_WIDTH = 4;
    private static final int MAIN_SCROLL_GAP = 2;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SEARCH_CACHE_LIMIT = 64;
    private int gridX;
    private int gridY;
    private int gridCols;
    private int gridRowsVisible;
    private int categoryX;
    private int categoryY;
    private int topInfoY;

    private List<ItemSearchIndex.CachedItem> cachedAllSource = List.of();
    private List<ItemSearchIndex.CachedItem> cachedInventorySource = List.of();
    private List<ItemSearchIndex.CachedItem> cachedItemSearchIndexIdentity = List.of();
    private Map<String, List<ItemSearchIndex.CachedItem>> cachedVanillaCategorySources = Map.of();
    private Map<String, List<ItemSearchIndex.CachedItem>> cachedModSources = Map.of();
    private final Map<DisplayCacheKey, List<ItemSearchIndex.CachedItem>> displayCache =
            new LinkedHashMap<>(SEARCH_CACHE_LIMIT, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<DisplayCacheKey, List<ItemSearchIndex.CachedItem>> eldest) {
                    return size() > SEARCH_CACHE_LIMIT;
                }
            };
    private final List<VisibleSlot> visibleSlotCache = new ArrayList<>();
    private int displayVersion = 0;
    private int cachedVisibleDisplayVersion = -1;
    private int cachedVisibleScroll = -1;
    private int cachedVisibleCols = -1;
    private int cachedVisibleRows = -1;

    private final List<String> allMods = new ArrayList<>();
    private final List<String> allTags = new ArrayList<>();
    private final List<CategoryEntry> categoryEntries = new ArrayList<>();
    private final List<Button> categoryButtons = new ArrayList<>();
    private final GridScrollController categoryScroll = new GridScrollController();
    private String categoryKey = rememberedCategoryKey;
    private List<String> autoCompleteList = new ArrayList<>();
    private final GridScrollController autoCompleteScroll =
            new GridScrollController();
    private final int autoCompleteMaxVisible = 10;
    private int autoCompleteSelected = -1;
    private boolean showAutoComplete = false;
    private int autoCompleteMode = 0;

    private String activeFilterValue = rememberedFilterValue;
    private int activeFilterType = rememberedFilterType;
    private Button applyFilterBtn = null;
    private int btnAreaStartX;

    public ItemSelectorScreen(Screen parent, Consumer<Selection> onSelect) {
        super(Component.translatable("gui.kineticcore.items.item_selector.title"));
        this.parent = parent;
        this.onSelect = onSelect;
        useCanvas(
                640f,
                360f,
                6
        );
        maxScale = 1.0f;
        loadPlayerStacks();
        loadFilters();
        rebuildCategoryEntries();
        if (mode != 0 && mode != 1) {
            mode = 0;
        }
        if (mode != 0) {
            categoryKey = null;
        }
    }

    private void loadPlayerStacks() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        player.getInventory().items.forEach(this::addInventoryStack);
        player.getArmorSlots().forEach(this::addInventoryStack);
        addInventoryStack(player.getOffhandItem());

        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.getCurios().values().forEach(stackHandler -> {
                var stacks = stackHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    addInventoryStack(stacks.getStackInSlot(i));
                }
            }));
        } catch (NoClassDefFoundError ignored) {
        }
    }

    private void addInventoryStack(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            invItems.add(new ItemSearchIndex.CachedItem(stack));
        }
    }

    private void loadFilters() {
        Set<String> modSet = new TreeSet<>();
        ForgeRegistries.ITEMS.getEntries().forEach(entry -> modSet.add(entry.getKey().location().getNamespace()));
        allMods.addAll(modSet);

        Set<String> tagSet = new TreeSet<>();
        Objects.requireNonNull(ForgeRegistries.ITEMS.tags()).getTagNames().forEach(tagKey -> tagSet.add(tagKey.location().toString()));
        allTags.addAll(tagSet);
    }

    private void rebuildCategoryEntries() {
        categoryEntries.clear();
        categoryEntries.add(new CategoryEntry(CategoryType.MODE, 0, null, Component.translatable("gui.kineticcore.items.mode.all")));
        categoryEntries.add(new CategoryEntry(CategoryType.MODE, 1, null, Component.translatable("gui.kineticcore.items.mode.inv")));
        categoryEntries.add(new CategoryEntry(CategoryType.VANILLA, 0, "blocks", Component.translatable("gui.kineticcore.items.category.blocks")));
        categoryEntries.add(new CategoryEntry(CategoryType.VANILLA, 0, "redstone", Component.translatable("gui.kineticcore.items.category.redstone")));
        categoryEntries.add(new CategoryEntry(CategoryType.VANILLA, 0, "tools", Component.translatable("gui.kineticcore.items.category.tools")));
        categoryEntries.add(new CategoryEntry(CategoryType.VANILLA, 0, "combat", Component.translatable("gui.kineticcore.items.category.combat")));
        categoryEntries.add(new CategoryEntry(CategoryType.VANILLA, 0, "food", Component.translatable("gui.kineticcore.items.category.food")));
        categoryEntries.add(new CategoryEntry(CategoryType.VANILLA, 0, "ingredients", Component.translatable("gui.kineticcore.items.category.ingredients")));
        categoryEntries.add(new CategoryEntry(CategoryType.VANILLA, 0, "spawn_eggs", Component.translatable("gui.kineticcore.items.category.spawn_eggs")));
        categoryEntries.add(new CategoryEntry(CategoryType.HEADER, 0, null, Component.translatable("gui.kineticcore.items.category.mods")));
        for (String modId : allMods) {
            categoryEntries.add(new CategoryEntry(CategoryType.MOD, 0, modId, Component.literal("@" + modId)));
        }
        categoryScroll.update(categoryEntries.size(), FIXED_GRID_ROWS);
    }

    @Override
    protected void buildUi() {
        resetScrollableWidgets();
        if (!ItemSearchIndex.isReady()) {
            ItemSearchIndex.prepareCache(() -> {
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> this.init(this.minecraft, this.width, this.height));
                }
            });
            return;
        }

        gridCols = FIXED_GRID_COLS;
        gridRowsVisible = FIXED_GRID_ROWS;
        gridY = 34;
        categoryY = gridY;

        int contentW = gridContentWidth();
        int totalWidth = CATEGORY_WIDTH
                + CATEGORY_SCROLLBAR_WIDTH
                + CATEGORY_GAP
                + contentW
                + SCROLLBAR_WIDTH
                + 4;

        categoryX = Math.max(8, (canvasWidth - totalWidth) / 2);
        gridX = categoryX + CATEGORY_WIDTH + CATEGORY_SCROLLBAR_WIDTH + CATEGORY_GAP - GRID_SHIFT_LEFT;

        int gap = 4;
        int topY = 5;
        int backBtnW = 40;
        int applyBtnW = 45;
        int rightEdge = gridX + contentW + SCROLLBAR_WIDTH + 4;

        int backBtnX = rightEdge - backBtnW - 2;
        int applyBtnX = backBtnX - gap - applyBtnW;
        btnAreaStartX = applyBtnX;
        topInfoY = topY + 6;

        addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.kineticcore.config.back"),
                                button -> {
                                    if (minecraft != null) {
                                        minecraft.setScreen(parent);
                                    }
                                }
                        )
                        .bounds(backBtnX, topY, backBtnW, 20)
                        .build()
        );

        applyFilterBtn = Button.builder(
                        Component.translatable("gui.kineticcore.items.filter.apply"),
                        button -> applyFilterAsResult()
                )
                .bounds(applyBtnX, topY, applyBtnW, 20)
                .build();

        applyFilterBtn.visible = false;
        applyFilterBtn.active = false;
        addRenderableWidget(applyFilterBtn);

        int searchX = gridX;
        int maxSearchWidth = Math.max(100, applyBtnX - gap - searchX);
        int searchWidth = Math.min(220, maxSearchWidth);

        searchBox = new EditBox(
                font,
                searchX,
                topY,
                searchWidth,
                20,
                Component.empty()
        );

        searchBox.setMaxLength(1024);
        searchBox.setResponder(this::onSearchInput);
        searchBox.setValue(rememberedSearch);
        addRenderableWidget(searchBox);

        categoryScroll.update(categoryEntries.size(), FIXED_GRID_ROWS);
        createCategoryButtons();
        refreshDisplay();
    }



    private void onSearchInput(String text) {
        rememberedSearch = text == null ? "" : text;
        String trimmed = rememberedSearch.trim();
        if (trimmed.startsWith("@")) {
            autoCompleteMode = 1;
            String query = trimmed.substring(1).toLowerCase(Locale.ROOT);
            autoCompleteList = allMods.stream().filter(mod -> query.isEmpty() || mod.contains(query)).collect(Collectors.toList());
            showAutoComplete = !autoCompleteList.isEmpty();
            autoCompleteScroll.reset();
            autoCompleteScroll.update(
                    autoCompleteList.size(),
                    autoCompleteMaxVisible
            );
            autoCompleteSelected = -1;
        } else if (trimmed.startsWith("#")) {
            autoCompleteMode = 2;
            String query = trimmed.substring(1).toLowerCase(Locale.ROOT);
            autoCompleteList = allTags.stream().filter(tag -> query.isEmpty() || tag.contains(query)).collect(Collectors.toList());
            showAutoComplete = !autoCompleteList.isEmpty();
            autoCompleteScroll.reset();
            autoCompleteScroll.update(
                    autoCompleteList.size(),
                    autoCompleteMaxVisible
            );
            autoCompleteSelected = -1;
        } else {
            showAutoComplete = false;
            autoCompleteMode = 0;
            autoCompleteList.clear();
        }
        refreshDisplay();
    }

    private void refreshDisplay() {
        ensureSourceCache();

        String text = searchBox != null ? searchBox.getValue() : "";
        String query = text.toLowerCase(Locale.ROOT).trim();
        mainScroll.reset();

        DisplayCacheKey cacheKey = new DisplayCacheKey(
                mode,
                query,
                activeFilterType,
                activeFilterValue == null ? "" : activeFilterValue,
                categoryKey == null ? "" : categoryKey
        );
        List<ItemSearchIndex.CachedItem> cached = displayCache.get(cacheKey);
        if (cached != null) {
            displayList = cached;
        } else {
            List<ItemSearchIndex.CachedItem> source = sourceForMode();
            if (activeFilterType == 0 && query.isEmpty()) {
                displayList = source;
            } else {
                List<ItemSearchIndex.CachedItem> scanSource = findCachedSearchBase(cacheKey, source);
                List<ItemSearchIndex.CachedItem> filtered = new ArrayList<>();

                String extraQuery = query;
                if (activeFilterType != 0 && (extraQuery.startsWith("@") || extraQuery.startsWith("#"))) {
                    extraQuery = "";
                }

                for (ItemSearchIndex.CachedItem item : scanSource) {
                    if (!matchesActiveFilter(item)) {
                        continue;
                    }
                    if (!matchesSearch(item, query, extraQuery)) {
                        continue;
                    }
                    filtered.add(item);
                }

                displayList = List.copyOf(filtered);
            }
            displayCache.put(cacheKey, displayList);
        }

        mainScroll.update(
                totalDisplayRows(),
                gridRowsVisible
        );
        markDisplayChanged();
        updateApplyButton();
    }

    private void ensureSourceCache() {
        List<ItemSearchIndex.CachedItem> currentItems = ItemSearchIndex.getItems();
        if (currentItems == cachedItemSearchIndexIdentity) {
            return;
        }

        cachedItemSearchIndexIdentity = currentItems;
        cachedAllSource = buildSelectableSource(currentItems);
        cachedInventorySource = buildSelectableSource(invItems);

        Map<String, List<ItemSearchIndex.CachedItem>> groupedMods = new LinkedHashMap<>();
        Map<String, List<ItemSearchIndex.CachedItem>> groupedVanilla = new LinkedHashMap<>();
        groupedVanilla.put("blocks", new ArrayList<>());
        groupedVanilla.put("redstone", new ArrayList<>());
        groupedVanilla.put("tools", new ArrayList<>());
        groupedVanilla.put("combat", new ArrayList<>());
        groupedVanilla.put("food", new ArrayList<>());
        groupedVanilla.put("ingredients", new ArrayList<>());
        groupedVanilla.put("spawn_eggs", new ArrayList<>());

        for (ItemSearchIndex.CachedItem item : cachedAllSource) {
            String namespace = getNamespace(item);
            if (!namespace.isEmpty()) {
                groupedMods.computeIfAbsent(namespace, key -> new ArrayList<>()).add(item);
            }
            for (String category : groupedVanilla.keySet()) {
                if (belongsToVanillaCategory(item, category)) {
                    groupedVanilla.get(category).add(item);
                }
            }
        }

        cachedModSources = freezeGroupedSources(groupedMods);
        cachedVanillaCategorySources = freezeGroupedSources(groupedVanilla);
        displayCache.clear();
    }

    private List<ItemSearchIndex.CachedItem> buildSelectableSource(List<ItemSearchIndex.CachedItem> source) {
        List<ItemSearchIndex.CachedItem> result = new ArrayList<>();
        for (ItemSearchIndex.CachedItem item : source) {
            if (isSelectable(item)) {
                result.add(item);
            }
        }
        return List.copyOf(result);
    }

    private boolean isSelectable(ItemSearchIndex.CachedItem item) {
        return item != null && item.stack != null && !item.stack.isEmpty();
    }

    private Map<String, List<ItemSearchIndex.CachedItem>> freezeGroupedSources(Map<String, List<ItemSearchIndex.CachedItem>> grouped) {
        Map<String, List<ItemSearchIndex.CachedItem>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, List<ItemSearchIndex.CachedItem>> entry : grouped.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    private boolean belongsToVanillaCategory(ItemSearchIndex.CachedItem cachedItem, String category) {
        ItemStack stack = cachedItem.stack;
        Item item = stack.getItem();
        String path = registryPath(cachedItem);

        return switch (category) {
            case "blocks" -> item instanceof BlockItem;
            case "redstone" -> isRedstoneItem(item, path);
            case "tools" -> isToolItem(item, path);
            case "combat" -> isCombatItem(item, path);
            case "food" -> stack.isEdible() || item instanceof PotionItem;
            case "ingredients" -> isIngredientItem(cachedItem, path);
            case "spawn_eggs" -> item instanceof SpawnEggItem;
            default -> false;
        };
    }

    private boolean isRedstoneItem(Item item, String path) {
        if (!(item instanceof BlockItem)) {
            return path.equals("redstone") || path.equals("repeater") || path.equals("comparator");
        }
        return containsAny(path,
                "redstone", "repeater", "comparator", "piston", "observer", "lever", "button",
                "pressure_plate", "tripwire", "dispenser", "dropper", "hopper", "target",
                "daylight_detector", "note_block", "jukebox", "tnt", "rail", "sculk_sensor",
                "lightning_rod");
    }

    private boolean isToolItem(Item item, String path) {
        return item instanceof DiggerItem
                || item instanceof ShearsItem
                || item instanceof FishingRodItem
                || item instanceof FlintAndSteelItem
                || item instanceof BrushItem
                || item instanceof BucketItem
                || item instanceof CompassItem
                || containsAny(path, "clock", "spyglass", "lead", "name_tag", "saddle");
    }

    private boolean isCombatItem(Item item, String path) {
        return item instanceof SwordItem
                || item instanceof ArmorItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem
                || item instanceof ShieldItem
                || item instanceof ArrowItem
                || containsAny(path, "totem_of_undying");
    }

    private boolean isIngredientItem(ItemSearchIndex.CachedItem item, String path) {
        if (containsAny(path,
                "ingot", "nugget", "diamond", "emerald", "lapis", "quartz", "amethyst", "coal",
                "charcoal", "scrap", "shard", "crystal", "dust", "powder", "rod", "stick",
                "string", "feather", "leather", "paper", "book", "brick", "flint", "bone",
                "gunpowder", "slime_ball", "magma_cream", "ghast_tear", "ender_pearl", "ender_eye",
                "membrane", "shell", "heart_of_the_sea", "echo_shard", "raw_")) {
            return true;
        }
        String searchData = item.searchData == null ? "" : item.searchData;
        return containsAny(searchData,
                "#forge:ingots/", "#forge:nuggets/", "#forge:gems/", "#forge:dusts/",
                "#forge:raw_materials/", "#forge:rods/", "#forge:plates/", "#forge:shards/",
                "#forge:crystals/");
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String registryPath(ItemSearchIndex.CachedItem item) {
        if (item == null || item.idStr == null) {
            return "";
        }
        int colon = item.idStr.indexOf(':');
        String path = colon >= 0 && colon + 1 < item.idStr.length()
                ? item.idStr.substring(colon + 1)
                : item.idStr;
        return path.toLowerCase(Locale.ROOT);
    }

    private List<ItemSearchIndex.CachedItem> sourceForMode() {
        if (mode == 1) {
            return cachedInventorySource;
        }
        if (categoryKey == null || categoryKey.isBlank()) {
            return cachedAllSource;
        }
        if (categoryKey.startsWith("vanilla:")) {
            return cachedVanillaCategorySources.getOrDefault(categoryKey.substring("vanilla:".length()), List.of());
        }
        if (categoryKey.startsWith("mod:")) {
            return cachedModSources.getOrDefault(categoryKey.substring("mod:".length()), List.of());
        }
        return cachedAllSource;
    }

    private List<ItemSearchIndex.CachedItem> rawSourceForMode() {
        return sourceForMode();
    }

    private String getNamespace(ItemSearchIndex.CachedItem item) {
        if (item == null || item.idStr == null || item.idStr.isBlank()) {
            return "";
        }
        int colon = item.idStr.indexOf(':');
        if (colon <= 0) {
            return "";
        }
        return item.idStr.substring(0, colon).toLowerCase(Locale.ROOT);
    }

    private boolean matchesActiveFilter(ItemSearchIndex.CachedItem item) {
        if (activeFilterType == 1 && activeFilterValue != null) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.stack.getItem());
            return id != null && id.getNamespace().equals(activeFilterValue);
        }
        if (activeFilterType == 2 && activeFilterValue != null) {
            return ItemSearchIndex.getRegistryTagIds(item.stack).contains(activeFilterValue);
        }
        return true;
    }

    private boolean matchesSearch(ItemSearchIndex.CachedItem item, String query, String extraQuery) {
        if (activeFilterType != 0) {
            return extraQuery.isEmpty() || KineticSearch.match(item.searchData, extraQuery);
        }
        if (query.startsWith("@") && query.length() > 1) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.stack.getItem());
            return id != null && id.getNamespace().contains(query.substring(1));
        }
        if (query.startsWith("#") && query.length() > 1) {
            String tagQuery = query.substring(1);
            return ItemSearchIndex.getRegistryTagIds(item.stack).stream().anyMatch(tag -> tag.contains(tagQuery));
        }
        return query.isEmpty() || KineticSearch.match(item.searchData, query);
    }

    private List<ItemSearchIndex.CachedItem> findCachedSearchBase(
            DisplayCacheKey requested,
            List<ItemSearchIndex.CachedItem> fallback
    ) {
        List<ItemSearchIndex.CachedItem> best = fallback;
        int bestLength = -1;

        for (Map.Entry<DisplayCacheKey, List<ItemSearchIndex.CachedItem>> entry : displayCache.entrySet()) {
            DisplayCacheKey candidate = entry.getKey();
            if (candidate.mode() != requested.mode()
                    || candidate.filterType() != requested.filterType()
                    || !candidate.filterValue().equals(requested.filterValue())
                    || !candidate.categoryKey().equals(requested.categoryKey())
                    || candidate.query().length() >= requested.query().length()
                    || !requested.query().startsWith(candidate.query())) {
                continue;
            }

            if (candidate.query().length() > bestLength) {
                bestLength = candidate.query().length();
                best = entry.getValue();
            }
        }

        return best;
    }

    private void updateApplyButton() {
        if (applyFilterBtn == null) return;
        boolean canApply = activeFilterType != 0 && activeFilterValue != null && !activeFilterValue.isBlank();
        applyFilterBtn.visible = canApply;
        applyFilterBtn.active = canApply;
    }

    private void selectAutoComplete(int index) {
        if (index < 0 || index >= autoCompleteList.size()) return;
        String selected = autoCompleteList.get(index);
        if (autoCompleteMode == 1) {
            activeFilterType = 1;
            activeFilterValue = selected;
            rememberedFilterType = activeFilterType;
            rememberedFilterValue = activeFilterValue;
            searchBox.setValue("@" + selected);
        } else if (autoCompleteMode == 2) {
            activeFilterType = 2;
            activeFilterValue = selected;
            rememberedFilterType = activeFilterType;
            rememberedFilterValue = activeFilterValue;
            searchBox.setValue("#" + selected);
        }
        showAutoComplete = false;
        autoCompleteMode = 0;
        autoCompleteList.clear();
        autoCompleteScroll.reset();
        refreshDisplay();
    }

    private void clearActiveFilter() {
        activeFilterType = 0;
        activeFilterValue = null;
        rememberedFilterType = 0;
        rememberedFilterValue = null;
        rememberedSearch = "";
        searchBox.setValue("");
        refreshDisplay();
    }

    private void applyFilterAsResult() {
        if (onSelect != null) {
            if (activeFilterType == 2 && activeFilterValue != null) {
                onSelect.accept(Selection.tag(activeFilterValue));
            } else if (activeFilterType == 1 && activeFilterValue != null) {
                onSelect.accept(Selection.mod(activeFilterValue));
            }
        }
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (ItemSearchIndex.isReady()) {
            syncCategoryButtons();
        }
        graphics.fillGradient(0, 0, this.canvasWidth, this.canvasHeight, 0xFF222222, 0xFF111111);
        graphics.fill(0, this.gridY - 4, this.canvasWidth, this.gridY - 3, 0x40FFFFFF);

    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!ItemSearchIndex.isReady()) return;
        renderTopInfo(graphics, mouseX, mouseY);
        renderCategories(graphics, mouseX, mouseY);
        renderItems(graphics, mouseX, mouseY);
        renderMainScrollbar(
                graphics,
                mouseX,
                mouseY
        );
        renderSearchHint(graphics);
        if (showAutoComplete && !autoCompleteList.isEmpty() && searchBox != null) renderAutoComplete(graphics, mouseX, mouseY);
    }

    private void renderTopInfo(GuiGraphics graphics, int mouseX, int mouseY) {
        if (searchBox == null) return;
        int infoX = searchBox.getX() + searchBox.getWidth() + 6;

        int infoY =
                topInfoY;

        int maxInfoX =
                btnAreaStartX - 6;
        List<ItemSearchIndex.CachedItem> src = rawSourceForMode();
        MutableComponent countText = Component.literal(String.format("%,d", displayList.size()))
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" / ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format("%,d", src.size())).withStyle(ChatFormatting.YELLOW));
        if (infoX + font.width(countText) >= maxInfoX) {
            return;
        }

        graphics.drawString(
                font,
                countText,
                infoX,
                infoY,
                0xFFFFFF,
                true
        );

        int nextX =
                infoX
                        + font.width(countText)
                        + 8;

        if (activeFilterType == 0 || activeFilterValue == null || nextX >= maxInfoX) return;
        String prefix = activeFilterType == 1 ? "@" : "#";
        String fullLabel = prefix + activeFilterValue;
        int availW = maxInfoX - nextX - 14;
        String displayLabel = fullLabel;
        if (this.font.width(displayLabel) > availW) displayLabel = this.font.plainSubstrByWidth(fullLabel, availW - 6) + "..";
        Component filterComp = Component.translatable("gui.kineticcore.items.filter.label", Component.literal(displayLabel).withStyle(ChatFormatting.GOLD));
        int filterW = this.font.width(filterComp) + 14;
        int badgeY = searchBox.getY() + 2;

        graphics.fill(nextX, badgeY, nextX + filterW, badgeY + 16, 0xCC2A2A2A);
        graphics.renderOutline(nextX, badgeY, filterW, 16, 0xFFAAAAAA);
        graphics.drawString(this.font, filterComp, nextX + 3, badgeY + 4, 0xFFFFFF, false);

        int closeX = nextX + filterW - 11;
        int closeY = badgeY + 4;
        boolean closeHovered = mouseX >= closeX - 1 && mouseX < closeX + 7 && mouseY >= closeY - 1 && mouseY < closeY + 9;
        graphics.drawString(this.font, "✕", closeX, closeY, closeHovered ? 0xFFFFFF : 0xFF5555, false);
    }

    private void renderCategories(GuiGraphics graphics, int mouseX, int mouseY) {
        syncCategoryButtons();
        categoryScroll.render(
                graphics,
                mouseX,
                mouseY,
                categoryScrollbarX(),
                categoryY,
                CATEGORY_SCROLLBAR_WIDTH,
                gridContentHeight(),
                20,
                GuiTheme.current().scrollTrack(),
                GuiTheme.current().scrollThumb(),
                GuiTheme.current().scrollThumbHover()
        );
    }

    private void createCategoryButtons() {
        categoryButtons.clear();
        for (int index = 0; index < categoryEntries.size(); index++) {
            int categoryIndex = index;
            Button button = Button.builder(
                            Component.empty(),
                            ignored -> selectCategoryIndex(categoryIndex)
                    )
                    .bounds(
                            categoryButtonX(),
                            categoryY + index * CELL_SIZE,
                            CATEGORY_BUTTON_WIDTH,
                            SLOT_SIZE
                    )
                    .build();
            categoryButtons.add(button);
            addScrollableWidget(
                    button,
                    categoryButtonX(),
                    categoryY,
                    categoryButtonX() + CATEGORY_BUTTON_WIDTH,
                    categoryY + gridContentHeight(),
                    () -> categoryScroll.smoothOffset() * CELL_SIZE
            );
        }
        syncCategoryButtons();
    }

    private void syncCategoryButtons() {
        categoryScroll.update(categoryEntries.size(), FIXED_GRID_ROWS);
        for (int index = 0; index < categoryButtons.size(); index++) {
            Button button = categoryButtons.get(index);
            if (index >= categoryEntries.size()) {
                button.visible = false;
                button.active = false;
                button.setFocused(false);
                continue;
            }

            CategoryEntry entry = categoryEntries.get(index);
            button.visible = true;
            button.setMessage(entry.label());
            button.active = entry.selectable();
            button.setFocused(entry.selectable() && isCategoryActive(entry));
        }
    }

    private void selectCategoryIndex(int index) {
        if (index < 0 || index >= categoryEntries.size()) {
            return;
        }

        CategoryEntry entry = categoryEntries.get(index);
        if (!entry.selectable()) {
            return;
        }

        mode = entry.mode();
        categoryKey = switch (entry.type()) {
            case VANILLA -> "vanilla:" + entry.key();
            case MOD -> "mod:" + entry.key();
            default -> null;
        };
        rememberedMode = mode;
        rememberedCategoryKey = categoryKey;
        refreshDisplay();
        syncCategoryButtons();
        showAutoComplete = false;
    }

    private boolean isCategoryActive(CategoryEntry entry) {
        return switch (entry.type()) {
            case MODE -> categoryKey == null && mode == entry.mode();
            case VANILLA -> mode == 0 && ("vanilla:" + entry.key()).equals(categoryKey);
            case MOD -> mode == 0 && ("mod:" + entry.key()).equals(categoryKey);
            case HEADER -> false;
        };
    }

    private boolean handleCategoryClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        categoryScroll.update(categoryEntries.size(), FIXED_GRID_ROWS);
        if (categoryScroll.beginDrag(
                mouseX,
                mouseY,
                categoryScrollbarX(),
                categoryY,
                CATEGORY_SCROLLBAR_WIDTH,
                gridContentHeight(),
                20,
                0
        )) {
            return true;
        }
        if (mouseX >= categoryButtonX()
                && mouseX < categoryButtonX() + CATEGORY_BUTTON_WIDTH
                && mouseY >= categoryY
                && mouseY < categoryY + gridContentHeight()) {
            double contentY = mouseY - categoryY + categoryScroll.smoothOffset() * CELL_SIZE;
            int index = (int) Math.floor(contentY / CELL_SIZE);
            int within = (int) Math.floor(contentY - index * CELL_SIZE);
            if (within < SLOT_SIZE) {
                selectCategoryIndex(index);
                return true;
            }
        }
        return false;
    }

    private int categoryButtonX() {
        return categoryX + CATEGORY_BUTTON_SHIFT_X;
    }

    private int categoryScrollbarX() {
        return categoryButtonX() + CATEGORY_BUTTON_WIDTH + CATEGORY_SCROLL_GAP + CATEGORY_SCROLLBAR_SHIFT_X;
    }

    private int mainScrollbarX() {
        return gridX + gridContentWidth() + MAIN_SCROLL_GAP;
    }

    private void renderItems(GuiGraphics graphics, int mouseX, int mouseY) {
        ensureVisibleSlotCache();
        enableCanvasScissor(
                graphics,
                gridX,
                gridY,
                gridX + gridContentWidth(),
                gridY + gridContentHeight()
        );
        try {
            boolean mouseInGrid = mouseX >= gridX
                    && mouseX < gridX + gridContentWidth()
                    && mouseY >= gridY
                    && mouseY < gridY + gridContentHeight();
            for (VisibleSlot slot : visibleSlotCache) {
                boolean hovered = mouseInGrid && slot.contains(mouseX, mouseY);
                GuiTheme.itemSlot(graphics, slot.x(), slot.y(), SLOT_SIZE, hovered);
                graphics.renderItem(slot.stack(), slot.x() + 1, slot.y() + 1);
            }
        } finally {
            graphics.disableScissor();
        }
    }



    private void ensureVisibleSlotCache() {
        visibleSlotCache.clear();
        int firstRow = mainScroll.smoothIndexOffset();
        int shiftY = mainScroll.visualShift(CELL_SIZE);
        int startIndex = firstRow * gridCols;
        int endIndex = Math.min(
                startIndex + (gridRowsVisible + 1) * gridCols,
                displayList.size()
        );

        for (int index = startIndex; index < endIndex; index++) {
            int localIndex = index - startIndex;
            int column = localIndex % gridCols;
            int row = localIndex / gridCols;
            int x = gridX + column * CELL_SIZE;
            int y = gridY + row * CELL_SIZE - shiftY;
            if (y + SLOT_SIZE <= gridY || y >= gridY + gridContentHeight()) continue;
            ItemStack stack = displayList.get(index).stack;
            visibleSlotCache.add(new VisibleSlot(index, stack, x, y));
        }

        cachedVisibleDisplayVersion = displayVersion;
        cachedVisibleScroll = mainScroll.offset();
        cachedVisibleCols = gridCols;
        cachedVisibleRows = gridRowsVisible;
    }

    private void invalidateVisibleSlotCache() {
        cachedVisibleDisplayVersion = -1;
        cachedVisibleScroll = -1;
        visibleSlotCache.clear();
    }

    private void markDisplayChanged() {
        displayVersion++;
        invalidateVisibleSlotCache();
    }

    private int gridContentWidth() {
        return gridCols * CELL_SIZE;
    }

    private int gridContentHeight() {
        return gridRowsVisible * CELL_SIZE;
    }

    private int totalDisplayRows() {
        return (int) Math.ceil((double) displayList.size() / Math.max(1, gridCols));
    }

    private VisibleSlot findVisibleSlot(double mouseX, double mouseY) {
        if (mouseX < gridX
                || mouseX >= gridX + gridContentWidth()
                || mouseY < gridY
                || mouseY >= gridY + gridContentHeight()) {
            return null;
        }
        ensureVisibleSlotCache();
        for (VisibleSlot slot : visibleSlotCache) {
            if (slot.contains(mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    private void renderMainScrollbar(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        mainScroll.update(
                totalDisplayRows(),
                gridRowsVisible
        );

        mainScroll.render(
                graphics,
                mouseX,
                mouseY,
                mainScrollbarX(),
                gridY,
                SCROLLBAR_WIDTH,
                gridContentHeight(),
                20,
                GuiTheme.current().scrollTrack(),
                GuiTheme.current().scrollThumb(),
                GuiTheme.current().scrollThumbHover()
        );
    }

    private void renderSearchHint(GuiGraphics graphics) {
        if (searchBox == null || !searchBox.getValue().isEmpty() || searchBox.isFocused()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.drawString(this.font, Component.translatable("gui.kineticcore.items.search.hint"), searchBox.getX() + 6, searchBox.getY() + 6, 0xFFAAAAAA, false);
        graphics.pose().popPose();
    }

    private void renderAutoComplete(GuiGraphics graphics, int mouseX, int mouseY) {
        int acX = searchBox.getX();
        int acY = searchBox.getY() + searchBox.getHeight() + 2;
        int acW = Math.max(80, Math.min(250, canvasWidth - acX - 12));
        int itemH = 14;
        int visibleCount = Math.min(autoCompleteList.size(), autoCompleteMaxVisible);
        int totalH = visibleCount * itemH;
        String prefix = autoCompleteMode == 1 ? "@" : "#";

        autoCompleteScroll.update(autoCompleteList.size(), autoCompleteMaxVisible);
        double smoothOffset = autoCompleteScroll.smoothOffset();
        int first = Math.max(0, (int) Math.floor(smoothOffset));
        int last = Math.min(autoCompleteList.size(), first + autoCompleteMaxVisible + 2);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(acX, acY, acX + acW, acY + totalH, 0xFA0A0A0A);
        graphics.renderOutline(acX, acY, acW, totalH, 0xFF666666);
        enableCanvasScissor(graphics, acX, acY, acX + acW, acY + totalH);
        try {
            for (int index = first; index < last; index++) {
                int top = acY + (int) Math.round((index - smoothOffset) * itemH);
                int bgColor = index % 2 == 0 ? 0xFF1E1E1E : 0xFF2A2A2A;
                boolean hovered = mouseX >= acX
                        && mouseX < acX + acW
                        && mouseY >= acY
                        && mouseY < acY + totalH
                        && mouseY >= top
                        && mouseY < top + itemH;
                if (hovered || index == autoCompleteSelected) bgColor = 0xFF444444;
                graphics.fill(acX + 1, top, acX + acW - 1, top + itemH, bgColor);

                String entry = autoCompleteList.get(index);
                String rawText = prefix + entry;
                int textMaxW = acW - 8;
                if (this.font.width(rawText) > textMaxW) {
                    rawText = this.font.plainSubstrByWidth(rawText, textMaxW - 6) + "..";
                }
                Component lineText = Component.literal(rawText).withStyle(ChatFormatting.GOLD);
                graphics.drawString(this.font, lineText, acX + 4, top + 3, 0xFFFFFF, false);
            }
        } finally {
            graphics.disableScissor();
        }

        autoCompleteScroll.render(
                graphics,
                mouseX,
                mouseY,
                acX + acW + 1,
                acY,
                4,
                totalH,
                10,
                GuiTheme.current().scrollTrack(),
                GuiTheme.current().scrollThumb(),
                GuiTheme.current().scrollThumbHover()
        );
        graphics.pose().popPose();
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int rawMouseX, int rawMouseY) {
        if (!ItemSearchIndex.isReady() || showAutoComplete) {
            return;
        }
        VisibleSlot slot = findVisibleSlot(scaledMouseX, scaledMouseY);
        if (slot == null) {
            return;
        }
        showItemTooltip(slot.stack());
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        unfocusSearchIfNeeded(mouseX, mouseY);
        if (handleFilterCloseClick(mouseX, mouseY, button)) return true;
        if (handleCategoryClick(mouseX, mouseY, button)) {
            showAutoComplete = false;
            return true;
        }
        if (handleAutoCompleteClick(mouseX, mouseY, button)) return true;
        if (super.canvasMouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && ItemSearchIndex.isReady()) return handleListClick(mouseX, mouseY);
        return false;
    }

    private void unfocusSearchIfNeeded(double mouseX, double mouseY) {
        if (this.searchBox == null || this.searchBox.isMouseOver(mouseX, mouseY)) return;
        boolean inAutoComplete = false;
        if (showAutoComplete) {
            int acX = searchBox.getX();
            int acY = searchBox.getY() + searchBox.getHeight() + 2;
            int acW = Math.max(
                80,
                Math.min(
                        250,
                        canvasWidth - acX - 12
                )
        );
            int visibleCount = Math.min(autoCompleteList.size(), autoCompleteMaxVisible);
            int totalH = visibleCount * 14;
            inAutoComplete = mouseX >= acX && mouseX < acX + acW + 10 && mouseY >= acY && mouseY < acY + totalH;
        }
        if (!inAutoComplete) {
            this.searchBox.setFocused(false);
            if (this.getFocused() == this.searchBox) this.setFocused(null);
        }
    }

    private boolean handleFilterCloseClick(double mouseX, double mouseY, int button) {
        if (activeFilterType == 0 || activeFilterValue == null || searchBox == null || button != 0) return false;
        int infoX = searchBox.getX() + searchBox.getWidth() + 6;
        List<ItemSearchIndex.CachedItem> src = rawSourceForMode();
        MutableComponent countText = Component.literal(String.format("%,d", displayList.size()))
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" / ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format("%,d", src.size())).withStyle(ChatFormatting.YELLOW));
        int nextX = infoX + this.font.width(countText) + 8;
        int maxInfoX = btnAreaStartX - 6;
        if (nextX >= maxInfoX) return false;

        String prefix = activeFilterType == 1 ? "@" : "#";
        String fullLabel = prefix + activeFilterValue;
        int availW = maxInfoX - nextX - 14;
        String displayLabel = fullLabel;
        if (this.font.width(displayLabel) > availW) displayLabel = this.font.plainSubstrByWidth(fullLabel, availW - 6) + "..";
        Component filterComp = Component.translatable("gui.kineticcore.items.filter.label", Component.literal(displayLabel).withStyle(ChatFormatting.GOLD));
        int filterW = this.font.width(filterComp) + 14;
        int closeX = nextX + filterW - 11;
        int closeY = searchBox.getY() + 6;
        if (mouseX >= closeX - 2 && mouseX < closeX + 8 && mouseY >= closeY - 2 && mouseY < closeY + 10) {
            clearActiveFilter();
            return true;
        }
        return false;
    }

    private boolean handleAutoCompleteClick(double mouseX, double mouseY, int button) {
        if (!showAutoComplete || searchBox == null || button != 0) return false;
        int acX = searchBox.getX();
        int acY = searchBox.getY() + searchBox.getHeight() + 2;
        int acW = Math.max(
                80,
                Math.min(
                        250,
                        canvasWidth - acX - 12
                )
        );
        int itemH = 14;
        int visibleCount = Math.min(autoCompleteList.size(), autoCompleteMaxVisible);
        int totalH = visibleCount * itemH;
        autoCompleteScroll.update(
                autoCompleteList.size(),
                autoCompleteMaxVisible
        );

        if (autoCompleteScroll.beginDrag(
                mouseX,
                mouseY,
                acX + acW + 1,
                acY,
                4,
                totalH,
                10,
                2
        )) {
            return true;
        }
        if (mouseX >= acX && mouseX < acX + acW && mouseY >= acY && mouseY < acY + totalH) {
            double contentY = mouseY - acY + autoCompleteScroll.smoothOffset() * itemH;
            int clickedIdx = (int) Math.floor(contentY / itemH);
            if (clickedIdx >= 0 && clickedIdx < autoCompleteList.size()) {
                selectAutoComplete(clickedIdx);
                return true;
            }
        }
        showAutoComplete = false;
        return true;
    }

    private boolean handleListClick(double mouseX, double mouseY) {
        int contentWidth = gridContentWidth();
        int contentHeight = gridContentHeight();
        mainScroll.update(
                totalDisplayRows(),
                gridRowsVisible
        );

        if (mainScroll.beginDrag(
                mouseX,
                mouseY,
                gridX
                        + contentWidth
                        + MAIN_SCROLL_GAP,
                gridY,
                SCROLLBAR_WIDTH,
                contentHeight,
                20,
                0
        )) {
            invalidateVisibleSlotCache();
            return true;
        }

        VisibleSlot slot = findVisibleSlot(mouseX, mouseY);
        if (slot == null || slot.displayIndex() < 0 || slot.displayIndex() >= displayList.size()) {
            return false;
        }

        if (onSelect != null) {
            onSelect.accept(Selection.item(displayList.get(slot.displayIndex()).stack));
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
        return true;
    }


    @Override
    protected boolean canvasMouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        boolean handled =
                mainScroll.release(button);

        handled |=
                autoCompleteScroll.release(button);

        handled |=
                categoryScroll.release(button);

        return handled
                || super.canvasMouseReleased(
                        mouseX,
                        mouseY,
                        button
                );
    }

    @Override
    protected boolean canvasMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (showAutoComplete
                && searchBox != null) {
            int acY =
                    searchBox.getY()
                            + searchBox.getHeight()
                            + 2;

            int visibleCount =
                    Math.min(
                            autoCompleteList.size(),
                            autoCompleteMaxVisible
                    );

            int totalH =
                    visibleCount * 14;

            if (autoCompleteScroll.drag(
                    mouseY,
                    acY,
                    totalH,
                    10
            )) {
                return true;
            }
        }

        if (categoryScroll.drag(
                mouseY,
                categoryY,
                gridContentHeight(),
                20
        )) {
            return true;
        }

        if (mainScroll.drag(
                mouseY,
                gridY,
                gridContentHeight(),
                20
        )) {
            invalidateVisibleSlotCache();
            return true;
        }

        return super.canvasMouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    protected boolean canvasMouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (showAutoComplete
                && searchBox != null) {
            int acX =
                    searchBox.getX();

            int acY =
                    searchBox.getY()
                            + searchBox.getHeight()
                            + 2;

            int acW =
                    Math.max(
                            80,
                            Math.min(
                                    250,
                                    canvasWidth
                                            - acX
                                            - 12
                            )
                    );

            int visibleCount =
                    Math.min(
                            autoCompleteList.size(),
                            autoCompleteMaxVisible
                    );

            int totalH =
                    visibleCount * 14;

            if (mouseX >= acX
                    && mouseX < acX + acW + 10
                    && mouseY >= acY
                    && mouseY < acY + totalH) {
                autoCompleteScroll.update(
                        autoCompleteList.size(),
                        autoCompleteMaxVisible
                );

                return autoCompleteScroll.scroll(
                        delta
                );
            }
        }

        if (mouseX >= categoryButtonX()
                && mouseX < categoryScrollbarX() + CATEGORY_SCROLLBAR_WIDTH + 2
                && mouseY >= categoryY
                && mouseY < categoryY + gridContentHeight()) {
            categoryScroll.update(categoryEntries.size(), FIXED_GRID_ROWS);
            if (categoryScroll.scroll(delta)) {
                return true;
            }
        }

        mainScroll.update(
                totalDisplayRows(),
                gridRowsVisible
        );

        if (mainScroll.scroll(delta)) {
            invalidateVisibleSlotCache();
            return true;
        }

        return super.canvasMouseScrolled(
                mouseX,
                mouseY,
                delta
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showAutoComplete && !autoCompleteList.isEmpty()) {
            if (keyCode == 265) {
                autoCompleteSelected = autoCompleteSelected <= 0 ? autoCompleteList.size() - 1 : autoCompleteSelected - 1;
                ensureAutoCompleteVisible();
                return true;
            }
            if (keyCode == 264) {
                autoCompleteSelected = autoCompleteSelected >= autoCompleteList.size() - 1 ? 0 : autoCompleteSelected + 1;
                ensureAutoCompleteVisible();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (autoCompleteSelected >= 0 && autoCompleteSelected < autoCompleteList.size()) {
                    selectAutoComplete(autoCompleteSelected);
                    return true;
                }
            }
            if (keyCode == 256) {
                showAutoComplete = false;
                return true;
            }
            if (keyCode == 258) {
                if (autoCompleteSelected >= 0 && autoCompleteSelected < autoCompleteList.size()) selectAutoComplete(autoCompleteSelected);
                else selectAutoComplete(0);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void ensureAutoCompleteVisible() {
        autoCompleteScroll.update(
                autoCompleteList.size(),
                autoCompleteMaxVisible
        );

        if (autoCompleteSelected
                < autoCompleteScroll.smoothOffset()) {
            autoCompleteScroll.setOffset(
                    autoCompleteSelected
            );
        }

        if (autoCompleteSelected
                >= autoCompleteScroll.smoothOffset()
                + autoCompleteMaxVisible) {
            autoCompleteScroll.setOffset(
                    autoCompleteSelected
                            - autoCompleteMaxVisible
                            + 1
            );
        }
    }
}
