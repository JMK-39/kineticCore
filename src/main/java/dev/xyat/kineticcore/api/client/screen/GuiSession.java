package dev.xyat.kineticcore.api.client.screen;

import dev.xyat.kineticcore.KineticCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Kinetic GUI 会话管理器。
 * 统一负责父界面导航、未保存草稿回滚和保存边界。
 */
@Mod.EventBusSubscriber(modid = KineticCore.MODID, value = Dist.CLIENT)
public final class GuiSession {
    private static final Map<Screen, Screen> PARENTS = new WeakHashMap<>();

    public static boolean routeSelectionListWheel(
            java.util.List<? extends GuiEventListener> children,
            double mouseX,
            double mouseY,
            double delta
    ) {
        if (children == null || delta == 0D) return false;
        for (GuiEventListener child : children) {
            if (child instanceof ObjectSelectionList<?> list
                    && list.isMouseOver(mouseX, mouseY)
                    && list.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return false;
    }

    private GuiSession() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        Screen current = event.getCurrentScreen();
        if (current == next) return;

        if (current != null && isKineticScreen(next)) {
            if (!isReturningToParent(current, next)) {
                synchronized (PARENTS) {
                    PARENTS.putIfAbsent(next, current);
                }
            }
            shareDraftSessionWithChild(current, next);
        }

        if (current != null && !sameDraftSession(current, next)) {
            if (current instanceof KineticScreen kineticScreen) {
                kineticScreen.discardPendingEditsForNavigation();
            } else if (current instanceof KineticContainerScreen<?> kineticContainerScreen) {
                kineticContainerScreen.discardPendingEditsForNavigation();
            } else if (current instanceof KineticNativeScreen kineticNativeScreen) {
                kineticNativeScreen.discardPendingEditsForNavigation();
            }
        }
    }

    private static boolean isReturningToParent(Screen current, Screen next) {
        return current != null && next != null && parentOf(current) == next;
    }


    private static void shareDraftSessionWithChild(Screen current, Screen next) {
        DraftSession draft = draftSessionOf(current);
        if (draft == null || !draft.enabled()) return;
        DraftSession nextDraft = draftSessionOf(next);
        if (nextDraft != null && nextDraft.isStandaloneOwner(next)) return;
        if (next instanceof KineticScreen kineticScreen) {
            kineticScreen.adoptDraftSession(draft);
        } else if (next instanceof KineticContainerScreen<?> kineticContainerScreen) {
            kineticContainerScreen.adoptDraftSession(draft);
        } else if (next instanceof KineticNativeScreen kineticNativeScreen) {
            kineticNativeScreen.adoptDraftSession(draft);
        }
    }

    private static boolean sameDraftSession(Screen first, Screen second) {
        if (first == null || second == null) return false;
        DraftSession a = draftSessionOf(first);
        DraftSession b = draftSessionOf(second);
        return a != null && a.enabled() && a == b;
    }

    private static DraftSession draftSessionOf(Screen screen) {
        if (screen instanceof KineticScreen kineticScreen) {
            return kineticScreen.draftSession();
        }
        if (screen instanceof KineticContainerScreen<?> kineticContainerScreen) {
            return kineticContainerScreen.draftSession();
        }
        if (screen instanceof KineticNativeScreen kineticNativeScreen) {
            return kineticNativeScreen.draftSession();
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlainScreenEscape(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (event.getKeyCode() != GLFW.GLFW_KEY_ESCAPE) return;
        if (!isKineticScreen(screen)) return;
        if (screen instanceof KineticScreen || screen instanceof KineticContainerScreen<?> || screen instanceof KineticNativeScreen) return;
        event.setCanceled(true);
        back(screen);
    }

    public static void setParent(Screen screen, Screen parent) {
        if (screen == null) return;
        synchronized (PARENTS) {
            if (parent == null) PARENTS.remove(screen);
            else PARENTS.put(screen, parent);
        }
    }

    public static Screen parentOf(Screen screen) {
        if (screen == null) return null;
        synchronized (PARENTS) {
            return PARENTS.get(screen);
        }
    }

    public static void back(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = parentOf(screen);
        if (screen instanceof AbstractContainerScreen<?> && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        minecraft.setScreen(parent);
    }

    public static boolean isKineticScreen(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getName();
        return name.startsWith("dev.xyat.kinetic");
    }

    public static final class DraftSession {
        private Supplier<Object> capture;
        private Consumer<Object> restore;
        private Object baseline;
        private Screen owner;
        private boolean standaloneOwner;
        private boolean enabled;
        private boolean restoring;

        public void reserveStandaloneOwner(Screen owner) {
            this.owner = owner;
            this.standaloneOwner = true;
        }

        public <T> void configureDraft(Screen owner, Supplier<T> capture, Consumer<T> restore, boolean standaloneOwner) {
            Objects.requireNonNull(capture, "capture");
            Objects.requireNonNull(restore, "restore");
            this.owner = owner;
            this.standaloneOwner = standaloneOwner;
            this.capture = capture::get;
            this.restore = value -> {
                @SuppressWarnings("unchecked") T typed = (T) value;
                restore.accept(typed);
            };
            this.baseline = this.capture.get();
            this.enabled = true;
        }

        public boolean isStandaloneOwner(Screen screen) {
            return standaloneOwner && owner == screen;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean isDirty() {
            return enabled && capture != null && !Objects.equals(baseline, capture.get());
        }

        public void commitBaseline(Screen caller) {
            if (owner != null && owner != caller) return;
            if (owner == caller && enabled && capture != null) baseline = capture.get();
        }

        public void discardToBaseline() {
            if (!enabled || restore == null || capture == null) return;
            if (!Objects.equals(baseline, capture.get())) restoreSnapshot(baseline);
        }

        private void restoreSnapshot(Object value) {
            if (restore == null || restoring) return;
            restoring = true;
            try {
                restore.accept(value);
            } finally {
                restoring = false;
            }
        }
    }
}
