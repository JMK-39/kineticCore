package dev.xyat.kineticcore.config.client;

import dev.xyat.kineticcore.KineticCore;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.config.server.KTServerConfigNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = KineticCore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KTServerConfigClient {
    private record State(
            boolean loaded,
            boolean editable,
            Map<String, Object> values,
            long revision,
            String loadFailureKey
    ) {
    }

    private static final Map<String, State> STATES = new HashMap<>();
    private static long revisionCounter;

    private KTServerConfigClient() {
    }

    public static boolean isLoaded(String pageId) {
        State state = STATES.get(pageId);
        return state != null && state.loaded;
    }

    public static boolean canEdit(String pageId) {
        State state = STATES.get(pageId);
        return state != null && state.loaded && state.editable;
    }

    public static long revision(String pageId) {
        State state = STATES.get(pageId);
        return state == null ? 0L : state.revision;
    }

    public static String loadFailureKey(String pageId) {
        State state = STATES.get(pageId);
        return state == null || state.loadFailureKey == null ? "" : state.loadFailureKey;
    }

    public static void request(KTConfigPage page) {
        Objects.requireNonNull(page, "page");
        if (page.scope() != KTConfigScope.SERVER_AUTHORITATIVE || !page.serverManaged()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || minecraft.player == null) return;
        markLoading(page.id());
        try {
            KTServerConfigNetwork.CHANNEL.sendToServer(new KTServerConfigNetwork.RequestPacket(page.id()));
        } catch (Throwable throwable) {
            KineticCore.LOGGER.error("Failed to request server config page {}", page.id(), throwable);
            markLoadFailure(page.id(), "gui.kineticcore.config.server.load_failed");
            refreshOpenScreen(page.id());
        }
    }

    public static void request(String pageId) {
        KTConfigApi.find(pageId).ifPresent(KTServerConfigClient::request);
    }

    public static boolean save(KTConfigPage page, Map<String, Object> changedValues) {
        Objects.requireNonNull(page, "page");
        if (!page.serverManaged() || !canEdit(page.id())) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || minecraft.player == null) return false;
        try {
            byte[] payload = KTServerConfigNetwork.encodeValues(changedValues);
            KTServerConfigNetwork.CHANNEL.sendToServer(new KTServerConfigNetwork.SavePacket(page.id(), payload));
            return true;
        } catch (Throwable throwable) {
            KineticCore.LOGGER.error("Failed to send server config page {}", page.id(), throwable);
            GuiOverlay.toast(
                    "kineticcore_server_config_save_failed",
                    Component.translatable("gui.kineticcore.config.server.save_failed")
            );
            return false;
        }
    }

    public static boolean savePartial(String pageId, Map<String, Object> changedValues) {
        KTConfigPage page = KTConfigApi.find(pageId).orElse(null);
        return page != null && save(page, changedValues);
    }

    static void applyCached(KTConfigPage page) {
        State state = STATES.get(page.id());
        if (state != null && state.loaded) {
            applyToClientMirror(page, state.values);
        }
    }

    public static String getString(String pageId, String entryId, String fallback) {
        Object value = value(pageId, entryId);
        return value instanceof String string ? string : fallback;
    }

    public static List<String> getStringList(String pageId, String entryId, List<String> fallback) {
        Object raw = value(pageId, entryId);
        if (!(raw instanceof List<?> list)) return new ArrayList<>(fallback);
        List<String> result = new ArrayList<>(list.size());
        for (Object value : list) {
            if (!(value instanceof String string)) return new ArrayList<>(fallback);
            result.add(string);
        }
        return result;
    }

    public static void handleSync(
            String pageId,
            boolean editable,
            boolean saveResponse,
            boolean success,
            String messageKey,
            byte[] payload
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        boolean snapshotLoaded = false;
        String loadFailureKey = "";
        if (payload != null && payload.length > 0) {
            try {
                values.putAll(KTServerConfigNetwork.decodeValues(payload));
                snapshotLoaded = true;
            } catch (Throwable throwable) {
                KineticCore.LOGGER.error("Failed to decode server config snapshot {}", pageId, throwable);
                success = false;
                messageKey = "gui.kineticcore.config.server.load_failed";
                loadFailureKey = messageKey;
            }
        } else if (!success) {
            loadFailureKey = messageKey == null || messageKey.isBlank()
                    ? "gui.kineticcore.config.server.load_failed"
                    : messageKey;
        } else {
            success = false;
            messageKey = "gui.kineticcore.config.server.load_failed";
            loadFailureKey = messageKey;
        }

        long revision = ++revisionCounter;
        STATES.put(pageId, new State(
                snapshotLoaded,
                snapshotLoaded && editable,
                Map.copyOf(values),
                revision,
                snapshotLoaded ? "" : loadFailureKey
        ));
        if (snapshotLoaded) {
            KTConfigApi.find(pageId).ifPresent(page -> applyToClientMirror(page, values));
        }
        refreshOpenScreen(pageId);

        if (saveResponse) {
            if (success) {
                KTConfigPage page = KTConfigApi.find(pageId).orElse(null);
                if (page != null) {
                    KTConfigApi.notifySaved(page);
                } else {
                    GuiOverlay.toast(
                            "kineticcore_server_config_saved:" + pageId,
                            Component.translatable("gui.kineticcore.config.server.saved")
                    );
                }
            } else {
                Component message = Component.translatable(
                        messageKey == null || messageKey.isBlank()
                                ? "gui.kineticcore.config.server.save_failed"
                                : messageKey
                );
                GuiOverlay.toast("kineticcore_server_config_save_failed:" + pageId, message);
            }
        }
    }

    private static void markLoading(String pageId) {
        long revision = ++revisionCounter;
        STATES.put(pageId, new State(false, false, Map.of(), revision, ""));
    }

    private static void markLoadFailure(String pageId, String messageKey) {
        long revision = ++revisionCounter;
        STATES.put(pageId, new State(false, false, Map.of(), revision, messageKey == null ? "" : messageKey));
    }

    private static Object value(String pageId, String entryId) {
        State state = STATES.get(pageId);
        return state == null ? null : state.values.get(entryId);
    }

    private static void applyToClientMirror(KTConfigPage page, Map<String, Object> values) {
        for (KTConfigEntry<?> entry : page.entries()) {
            if (!entry.isValue() || !values.containsKey(entry.id())) continue;
            Object normalized = normalizeForEntry(entry, values.get(entry.id()));
            if (normalized == null || !entry.accepts(normalized)) continue;
            try {
                entry.writeSnapshot(normalized);
            } catch (Throwable throwable) {
                KineticCore.LOGGER.error(
                        "Failed to apply server config mirror {}/{}",
                        page.id(),
                        entry.id(),
                        throwable
                );
            }
        }
    }

    private static Object normalizeForEntry(KTConfigEntry<?> entry, Object raw) {
        if (raw == null) return null;
        return switch (entry.type()) {
            case INTEGER, COLOR -> raw instanceof Number number ? safeInt(number) : raw;
            case LONG -> raw instanceof Number number ? safeLong(number) : raw;
            case DOUBLE -> raw instanceof Number number ? number.doubleValue() : raw;
            case INTEGER_LIST -> normalizeIntegerList(raw);
            default -> raw;
        };
    }

    private static Integer safeInt(Number number) {
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE || value != Math.rint(value)) {
            return null;
        }
        return (int) value;
    }

    private static Long safeLong(Number number) {
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < Long.MIN_VALUE || value > Long.MAX_VALUE || value != Math.rint(value)) {
            return null;
        }
        return number.longValue();
    }

    private static List<Integer> normalizeIntegerList(Object raw) {
        if (!(raw instanceof List<?> list)) return null;
        List<Integer> result = new ArrayList<>(list.size());
        for (Object value : list) {
            if (!(value instanceof Number number)) return null;
            Integer decoded = safeInt(number);
            if (decoded == null) return null;
            result.add(decoded);
        }
        return result;
    }

    private static void refreshOpenScreen(String pageId) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof KTConfigScreen configScreen) {
            configScreen.serverSnapshotUpdated(pageId);
        } else if (screen instanceof KTModuleConfigScreen moduleScreen) {
            moduleScreen.serverSnapshotUpdated(pageId);
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        STATES.clear();
        revisionCounter++;
    }
}
