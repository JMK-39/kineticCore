package dev.xyat.kineticcore.feature.firstjoin.client;

import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public final class FirstJoinCommandEditScreen extends Screen {
    private final FirstJoinCommandListScreen parent;
    private final int editingIndex;
    private EditBox input;
    private CommandSuggestions commandSuggestions;

    public FirstJoinCommandEditScreen(FirstJoinCommandListScreen parent, int editingIndex) {
        super(parent.editorTitle(editingIndex));
        this.parent = parent;
        this.editingIndex = editingIndex;
    }

    @Override
    protected void init() {
        String initial = "";
        if (editingIndex >= 0 && editingIndex < parent.currentCommands().size()) {
            initial = parent.currentCommands().get(editingIndex);
        }

        int inputX = 4;
        int inputY = height - 14;
        int inputWidth = Math.max(40, width - 8);

        input = addRenderableWidget(new EditBox(
                font,
                inputX,
                inputY,
                inputWidth,
                12,
                Component.translatable("gui.kineticcore.firstjoin.command_edit.input")
        ));
        input.setBordered(false);
        input.setCanLoseFocus(false);
        input.setMaxLength(2048);
        input.setValue(toEditorText(initial));
        input.setFocused(true);
        setFocused(input);

        commandSuggestions = new CommandSuggestions(
                minecraft,
                this,
                input,
                font,
                false,
                false,
                1,
                10,
                true,
                0xD0000000
        );
        commandSuggestions.setAllowSuggestions(true);
        input.setResponder(value -> commandSuggestions.updateCommandInfo());
        commandSuggestions.updateCommandInfo();

        int buttonY = 88;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.hud_editor.save"),
                        button -> saveCommand())
                .bounds(width / 2 - 102, buttonY, 96, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticcore.firstjoin.command_edit.back"),
                        button -> closeToParent())
                .bounds(width / 2 + 6, buttonY, 96, 20)
                .build());
    }

    private String toEditorText(String stored) {
        if (stored == null || stored.isBlank()) return "/";
        String trimmed = stored.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private String normalizeForStorage(String text) {
        if (text == null) return "";
        String normalized = text.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private void saveCommand() {
        String command = normalizeForStorage(input.getValue());
        if (command.isBlank()) {
            GuiOverlay.toast(Component.translatable("msg.kineticcore.firstjoin.command_edit.empty"));
            return;
        }

        try {
            parent.saveEditedCommand(editingIndex, command);
            closeToParent();
        } catch (Throwable throwable) {
            GuiOverlay.toast(parent.saveFailedMessage());
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (commandSuggestions != null && commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (commandSuggestions != null && commandSuggestions.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (commandSuggestions != null && commandSuggestions.mouseScrolled(delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fillGradient(0, 0, width, height, 0xD0171717, 0xD00E0E0E);

        int panelWidth = Math.max(280, Math.min(width - 32, 720));
        int panelX = (width - panelWidth) / 2;
        GuiTheme.panel(graphics, panelX, 14, panelWidth, 112);
        graphics.drawCenteredString(font, title, width / 2, 30, 0xFFFFFF);
        graphics.drawString(
                font,
                Component.translatable("gui.kineticcore.firstjoin.command_edit.hint"),
                panelX + 16,
                52,
                0xFFFFFF,
                false
        );
        if (parent.showPlayerVariables()) {
            graphics.drawString(
                    font,
                    Component.translatable("gui.kineticcore.firstjoin.command_edit.variables"),
                    panelX + 16,
                    68,
                    0xFFFFFF,
                    false
            );
        }

        graphics.fill(2, height - 16, width - 2, height - 2, 0x80000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (commandSuggestions != null) {
            commandSuggestions.render(graphics, mouseX, mouseY);
        }
    }

    private void closeToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
