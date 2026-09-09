package dev.xyat.kineticcore.config.client;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModContainer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public final class KTConfigApi {
    private static final String REQUIRES_WORLD_KEY = "gui.kineticcore.config.requires_world";
    private static final Map<String, KTConfigPage> PAGES = new LinkedHashMap<>();

    private KTConfigApi() {
    }

    public static synchronized void register(KTConfigPage page) {
        Objects.requireNonNull(page, "page");
        KTConfigPage old = PAGES.putIfAbsent(page.id(), page);
        if (old != null && old != page) {
            throw new IllegalStateException("A config page is already registered for " + page.id());
        }
    }

    /**
     * Registers a Forge {@code ModConfig.Type.CLIENT} spec as a standard KT
     * configuration page. Spec comments only decide whether a localized
     * tooltip/description exists; visible text comes from language keys.
     */
    public static void registerClientConfig(
            String pageId,
            Component title,
            ForgeConfigSpec spec
    ) {
        register(KTClientConfigAdapter.pageBuilder(pageId, title, spec).build());
    }

    public static synchronized void unregister(String pageId) {
        PAGES.remove(pageId);
    }

    public static synchronized Optional<KTConfigPage> find(String pageId) {
        return Optional.ofNullable(PAGES.get(pageId));
    }

    public static synchronized List<KTConfigPage> pages() {
        return List.copyOf(PAGES.values());
    }

    public static void notifySaved(String pageId) {
        if (pageId == null || pageId.isBlank()) return;
        find(pageId).ifPresent(KTConfigApi::notifySaved);
    }

    public static void notifySaved(KTConfigPage page) {
        Objects.requireNonNull(page, "page");
        Component message = page.applyNotice() == null
                ? Component.translatable(
                        page.applyTiming().savedTranslationKey(),
                        page.title().copy()
                )
                : Component.translatable(
                        "gui.kineticcore.config.saved.notice",
                        page.title().copy(),
                        page.applyNotice()
                );
        GuiOverlay.toast("kineticcore_config_saved:" + page.id(), message);
    }

    public static void notifyModuleSaved(Component moduleTitle) {
        if (moduleTitle == null) return;
        GuiOverlay.toast(
                "kineticcore_config_module_saved",
                Component.translatable("gui.kineticcore.config.module_saved", moduleTitle.copy())
        );
    }

    public static boolean canEdit(KTConfigPage page) {
        Objects.requireNonNull(page, "page");
        if (page.scope() != KTConfigScope.SERVER_AUTHORITATIVE) return true;
        if (!page.serverManaged()) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || minecraft.player == null || minecraft.level == null) return false;
        return KTServerConfigClient.canEdit(page.id());
    }

    public static Component unavailableReason(KTConfigPage page) {
        Objects.requireNonNull(page, "page");
        if (page.scope() != KTConfigScope.SERVER_AUTHORITATIVE) {
            return Component.empty();
        }
        if (!page.serverManaged()) {
            return Component.translatable("gui.kineticcore.config.server.unmanaged");
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || minecraft.player == null || minecraft.level == null) {
            return Component.translatable(REQUIRES_WORLD_KEY);
        }
        if (!KTServerConfigClient.isLoaded(page.id())) {
            String failureKey = KTServerConfigClient.loadFailureKey(page.id());
            if (!failureKey.isBlank()) {
                return Component.translatable(failureKey);
            }
            return Component.translatable("gui.kineticcore.config.server.loading");
        }
        if (!KTServerConfigClient.canEdit(page.id())) {
            return Component.translatable("gui.kineticcore.config.server.op_required");
        }
        return Component.empty();
    }

    public static Screen createScreen(Screen parent) {
        return new KTConfigIndexScreen(parent);
    }

    public static Screen createScreen(Screen parent, String pageId) {
        KTConfigPage page = find(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown config page: " + pageId));
        if (page.scope() == KTConfigScope.SERVER_AUTHORITATIVE && !page.serverManaged()) {
            showUnavailable(page);
            return new KTConfigIndexScreen(parent);
        }
        return new KTConfigScreen(parent, page);
    }

    public static Screen createScreenForOwner(Screen parent, String ownerModId) {
        String ownerId = Objects.requireNonNull(ownerModId, "ownerModId");
        String ownerPrefix = ownerId + ":";
        List<KTConfigPage> ownedPages = pages().stream()
                .filter(page -> page.id().startsWith(ownerPrefix))
                .toList();
        if (ownedPages.isEmpty()) return new KTConfigIndexScreen(parent);
        return new KTModuleConfigScreen(
                parent,
                KTConfigIndexScreen.moduleTitle(ownerId),
                ownedPages
        );
    }

    public static void installForgeConfigHub(ModContainer owner) {
        Objects.requireNonNull(owner, "owner").registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> createScreen(parent)
                )
        );
    }

    public static void installForgeConfigScreen(ModContainer owner) {
        Objects.requireNonNull(owner, "owner").registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> createScreenForOwner(parent, owner.getModId())
                )
        );
    }

    /**
     * Compatibility helper for old pages. New editors should use a dedicated
     * action or an authenticated client/server protocol instead of commands.
     */
    @Deprecated(forRemoval = false)
    public static Runnable serverCommandAction(String command) {
        String normalized = normalizeCommand(command);
        return () -> sendServerCommand(normalized);
    }

    /** Builds an action that opens a specialized editor with the current page as its parent. */
    public static Runnable screenAction(Function<Screen, ? extends Screen> screenFactory) {
        Objects.requireNonNull(screenFactory, "screenFactory");
        return () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(Objects.requireNonNull(
                    screenFactory.apply(minecraft.screen),
                    "screenFactory returned null"
            ));
        };
    }

    public static boolean sendServerCommand(String command) {
        String normalized = normalizeCommand(command);
        LocalPlayer player = Minecraft.getInstance().player;
        ClientPacketListener connection = player == null ? null : player.connection;
        if (connection == null) {
            GuiOverlay.toast(
                    "kineticcore_config_requires_world",
                    Component.translatable(REQUIRES_WORLD_KEY)
            );
            return false;
        }
        connection.sendCommand(normalized);
        return true;
    }

    private static void showUnavailable(KTConfigPage page) {
        GuiOverlay.toast(
                "kineticcore_config_unavailable",
                unavailableReason(page)
        );
    }

    private static String normalizeCommand(String command) {
        String normalized = Objects.requireNonNull(command, "command").trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("command cannot be blank");
        }
        return normalized;
    }
}
