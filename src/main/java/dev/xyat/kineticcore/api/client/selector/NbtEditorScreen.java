package dev.xyat.kineticcore.api.client.selector;

import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.Scroll;
import dev.xyat.kineticcore.feature.nbt.network.NbtNetwork;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class NbtEditorScreen extends KineticScreen {
    private final String initialNbt;
    private final Consumer<String> onSave;
    private final Screen parentScreen;

    private NbtEditorWidget nbtEditor;
    private EditBox searchBox;

    public NbtEditorScreen(String initialNbt, byte ignoredTargetType, String ignoredTargetId) {
        super(Component.translatable("screen.kineticcore.nbt_editor"));
        this.initialNbt = initialNbt;
        this.parentScreen = null;
        this.onSave = value -> NbtNetwork.sendToServer(new NbtNetwork.SaveNbtPacket(value));
        useCanvas(
                640f,
                360f,
                6
        );
    }

    public NbtEditorScreen(String initialNbt, Consumer<String> onSave, Screen parentScreen) {
        super(Component.translatable("screen.kineticcore.nbt_editor"));
        this.initialNbt = initialNbt;
        this.onSave = onSave;
        this.parentScreen = parentScreen;
        useCanvas(
                640f,
                360f,
                6
        );
    }

    @Override
    protected void buildUi() {
        searchBox = new EditBox(this.font, 20, 10, 120, 20, Component.translatable("gui.kineticcore.search"));
        searchBox.setResponder(query -> {
            if (nbtEditor != null) nbtEditor.setSearchQuery(query);
        });
        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(Button.builder(Component.literal("↑"), b -> nbtEditor.navigateSearch(-1))
                .bounds(145, 10, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("↓"), b -> nbtEditor.navigateSearch(1))
                .bounds(170, 10, 20, 20).build());

        int btnW = 80;
        int gap = 10;
        int closeX = this.canvasWidth - 20 - btnW;
        int saveX = closeX - gap - btnW;
        int clearX = saveX - gap - btnW;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.nbt.save"), b -> {
            String val = nbtEditor.getValue().trim();
            if (val.isEmpty() || val.equals("{}")) {
                onSave.accept("");
            } else {
                try {
                    TagParser.parseTag(val);
                    onSave.accept(val);
                } catch (Exception e) {
                    nbtEditor.setError(Component.translatable("gui.kineticcore.nbt.editor.invalid").getString());
                    return;
                }
            }
            if (this.minecraft != null) this.minecraft.setScreen(parentScreen);
        }).bounds(saveX, 10, btnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.nbt.clear"), b -> nbtEditor.setValue("")).bounds(clearX, 10, btnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.kineticcore.nbt.close"), b -> {
            if (this.minecraft != null) this.minecraft.setScreen(parentScreen);
        }).bounds(closeX, 10, btnW, 20).build());

        int editorX = 20;
        int editorY = 40;
        int editorW = this.canvasWidth - 40;
        int editorH = this.canvasHeight - 60;

        nbtEditor = new NbtEditorWidget(this.font, editorX, editorY, editorW, editorH);
        nbtEditor.setValue(initialNbt);
        nbtEditor.setResponder(val -> {
            try {
                if (val.trim().isEmpty() || val.trim().equals("{}")) { nbtEditor.setError(""); return; }
                TagParser.parseTag(val);
                nbtEditor.setError("");
            } catch (Exception e) {
                nbtEditor.setError(e.getMessage());
            }
        });
    }

    @Override
    public void renderCanvasBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.canvasWidth, this.canvasHeight, 0xCC000000);
        nbtEditor.render(g, mx, my);

        if (nbtEditor != null) {
            String countText = nbtEditor.getSearchMatchCount();
            if (!countText.isEmpty()) {
                g.drawString(this.font, countText, 195, 16, 0xFFFFFFFF, false);
            }
        }
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        if (searchBox != null && !searchBox.isFocused() && searchBox.getValue().isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.kineticcore.search_hint"),
                    searchBox.getX() + 4, searchBox.getY() + 6, 0xFFAAAAAA, false);
        }
    }

    @Override
    public boolean canvasMouseClicked(double mx, double my, int btn) {
        if (searchBox != null && !searchBox.isMouseOver(mx, my)) {
            if (this.getFocused() == searchBox) {
                this.setFocused(null);
            }
            searchBox.setFocused(false);
        }

        if (nbtEditor.mouseClicked(mx, my, btn)) return true;
        return super.canvasMouseClicked(mx, my, btn);
    }

    @Override
    public boolean canvasMouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (nbtEditor.mouseDragged(mx, my)) return true;
        return super.canvasMouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean canvasMouseReleased(double mx, double my, int btn) {
        if (nbtEditor.mouseReleased()) return true;
        return super.canvasMouseReleased(mx, my, btn);
    }

    @Override
    public boolean canvasMouseScrolled(double mx, double my, double d) {
        if (nbtEditor.mouseScrolled(d)) return true;
        return super.canvasMouseScrolled(mx, my, d);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                nbtEditor.navigateSearch(1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.setFocused(null);
                searchBox.setFocused(false);
                return true;
            }
        }

        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_F) {
            this.setFocused(searchBox);
            searchBox.setFocused(true);
            return true;
        }

        if (nbtEditor.keyPressed(keyCode)) return true;

        assert this.minecraft != null;
        if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(parentScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nbtEditor.charTyped(codePoint)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    private static final class NbtEditorWidget {
        public int x, y, width, height;
        private String value = "";
        private int cursorPos = 0;
        private int selectPos = 0;
        public boolean isFocused = false;
        private double scrollOffset = 0D;
        private final Scroll.State smoothScroll = new Scroll.State();
        private Consumer<String> responder;
        private final Font font;

        private boolean isDraggingScroll = false;
        private boolean isDraggingText = false;
        private String errorMsg = "";
        private String hint = "";

        private final List<LineInfo> lines = new ArrayList<>();

        private long lastClickTime = 0;
        private int lastClickCursor = -1;
        private String searchQuery = "";
        private final List<Integer> matchIndices = new ArrayList<>();
        private int currentMatchIdx = -1;
        private long flashStartTime = 0;


        private static class LineInfo {
            String coloredString;
            int rawStartIndex;
            int rawEndIndex;
            int[] pixelOffsets;
        }

        public NbtEditorWidget(Font font, int x, int y, int width, int height) {
            this.font = font;
            this.x = x; this.y = y; this.width = width; this.height = height;
            wrapText();
        }

        public String getValue() { return value; }
        public void setResponder(Consumer<String> r) { this.responder = r; }
        public void setError(String errorMsg) { this.errorMsg = errorMsg; }
        public void setHint(String hint) { this.hint = hint == null ? "" : hint; }

        public String getSearchMatchCount() {
            if (searchQuery.isEmpty()) return "";
            return matchIndices.isEmpty() ? "0/0" : (currentMatchIdx + 1) + "/" + matchIndices.size();
        }

        public void setValue(String v) {
            this.value = v;
            cursorPos = value.length();
            selectPos = cursorPos;
            onValueChange();
            scrollToCursor();
        }

        public void setSearchQuery(String query) {
            this.searchQuery = query.toLowerCase(Locale.ROOT);
            updateMatches();
            if (!matchIndices.isEmpty()) {
                currentMatchIdx = 0;
                flashStartTime = System.currentTimeMillis();
                scrollToCurrentMatch();
            } else {
                currentMatchIdx = -1;
            }
        }

        public void navigateSearch(int direction) {
            if (matchIndices.isEmpty()) return;
            currentMatchIdx = (currentMatchIdx + direction + matchIndices.size()) % matchIndices.size();
            flashStartTime = System.currentTimeMillis();
            scrollToCurrentMatch();
        }

        private void updateMatches() {
            matchIndices.clear();
            if (searchQuery.isEmpty()) return;
            String lowerValue = value.toLowerCase(Locale.ROOT);
            int index = lowerValue.indexOf(searchQuery);
            while (index >= 0) {
                matchIndices.add(index);
                index = lowerValue.indexOf(searchQuery, index + 1);
            }
        }

        private void scrollToCurrentMatch() {
            if (currentMatchIdx < 0 || currentMatchIdx >= matchIndices.size()) return;
            int mStart = matchIndices.get(currentMatchIdx);
            int targetLine = 0;
            for (int i = 0; i < lines.size(); i++) {
                if (mStart >= lines.get(i).rawStartIndex && mStart <= lines.get(i).rawEndIndex) {
                    targetLine = i;
                    break;
                }
            }
            int maxVisible = getMaxVisibleLines();
            if (targetLine < scrollOffset || targetLine >= scrollOffset + maxVisible) {
                int desiredScroll = Math.max(0, targetLine - maxVisible / 2);
                scrollOffset = Math.min(desiredScroll, maxScroll());
            }
        }

        private void wrapText() {
            lines.clear();
            if (value.isEmpty()) {
                LineInfo info = new LineInfo();
                info.rawStartIndex = 0; info.rawEndIndex = 0;
                info.coloredString = "";
                info.pixelOffsets = new int[]{0};
                lines.add(info);
                return;
            }

            int maxW = this.width - 16;
            boolean inString = false;
            char stringChar = 0;

            int[] charWidths = new int[value.length()];
            String[] charColors = new String[value.length()];

            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                String targetColor;

                if (inString) {
                    targetColor = "";
                    if (c == stringChar && value.charAt(i - 1) != '\\') inString = false;
                } else {
                    if (c == '"' || c == '\'') {
                        targetColor = ""; inString = true; stringChar = c;
                    } else if (c == '{' || c == '}' || c == '[' || c == ']') {
                        targetColor = "";
                    } else if (c == ':' || c == ',') {
                        targetColor = "";
                    } else if (Character.isDigit(c) || c == '-' || c == '.' || (i > 0 && Character.isDigit(value.charAt(i-1)) && (c == 'b' || c == 's' || c == 'L' || c == 'f' || c == 'd'))) {
                        targetColor = "";
                    } else {
                        targetColor = "";
                    }
                }
                charColors[i] = targetColor;
                charWidths[i] = font.width(String.valueOf(c));
            }

            int currentLineStart = 0;
            int currentLineWidth = 0;

            for (int i = 0; i < value.length(); i++) {
                if (currentLineWidth + charWidths[i] > maxW && currentLineWidth > 0) {
                    lines.add(buildLine(value, charColors, charWidths, currentLineStart, i));
                    currentLineStart = i;
                    currentLineWidth = 0;
                }
                currentLineWidth += charWidths[i];
            }
            if (currentLineStart < value.length()) {
                lines.add(buildLine(value, charColors, charWidths, currentLineStart, value.length()));
            }
        }

        private LineInfo buildLine(String raw, String[] colors, int[] widths, int start, int end) {
            LineInfo info = new LineInfo();
            info.rawStartIndex = start;
            info.rawEndIndex = end;
            info.pixelOffsets = new int[end - start + 1];

            StringBuilder colored = new StringBuilder();
            String lastColor = "";
            int currentX = 0;

            for (int i = start; i < end; i++) {
                info.pixelOffsets[i - start] = currentX;
                if (!colors[i].equals(lastColor)) {
                    colored.append(colors[i]);
                    lastColor = colors[i];
                }
                colored.append(raw.charAt(i));
                currentX += widths[i];
            }
            info.pixelOffsets[end - start] = currentX;
            info.coloredString = colored.toString();
            return info;
        }

        private void onValueChange() {
            wrapText();
            updateMatches();
            if (responder != null) responder.accept(value);
        }

        private int getMaxVisibleLines() { return (height - 25) / font.lineHeight; }
        private int maxScroll() { return Math.max(0, lines.size() - getMaxVisibleLines()); }

        private void scrollToCursor() {
            int cLine = 0;
            for (int i = 0; i < lines.size(); i++) {
                if (cursorPos >= lines.get(i).rawStartIndex && cursorPos <= lines.get(i).rawEndIndex) { cLine = i; break; }
            }
            int maxVisible = getMaxVisibleLines();
            if (cLine < scrollOffset) scrollOffset = cLine;
            else if (cLine >= scrollOffset + maxVisible) scrollOffset = cLine - maxVisible + 1;
            scrollOffset = Math.min(scrollOffset, maxScroll());
        }

        private int getPixelX(LineInfo line, int rawIndex) {
            int localIdx = rawIndex - line.rawStartIndex;
            if (localIdx < 0) return 0;
            if (localIdx >= line.pixelOffsets.length) return line.pixelOffsets[line.pixelOffsets.length - 1];
            return line.pixelOffsets[localIdx];
        }

        private int getRawIndex(LineInfo line, int pixelX) {
            if (pixelX <= 0) return line.rawStartIndex;
            for (int i = 0; i < line.pixelOffsets.length - 1; i++) {
                int left = line.pixelOffsets[i];
                int right = line.pixelOffsets[i+1];
                if (pixelX >= left && pixelX < right) {
                    if (pixelX - left < right - pixelX) return line.rawStartIndex + i;
                    else return line.rawStartIndex + i + 1;
                }
            }
            return line.rawEndIndex;
        }

        public void render(GuiGraphics g, double mouseX, double mouseY) {
            g.fill(x, y, x + width, y + height, 0xFF181818);

            if (value.isEmpty() && !isFocused && hint != null && !hint.isEmpty()) {
                String line = font.plainSubstrByWidth(hint, width - 18);
                g.drawString(font, line, x + 6, y + 6, 0xFF777777, false);
            }

            int maxVisible = getMaxVisibleLines();
            int contentH = maxVisible * font.lineHeight;

            int minPos = Math.min(selectPos, cursorPos);
            int maxPos = Math.max(selectPos, cursorPos);

            double visualScroll = smoothScroll.follow(scrollOffset, maxScroll());
            int visualStart = Math.max(0, Math.min((int) Math.floor(visualScroll), maxScroll()));
            int visualShift = (int) Math.round((visualScroll - visualStart) * font.lineHeight);
            int visualEnd = Math.min(lines.size(), visualStart + maxVisible + 2);

            for (int i = visualStart; i < visualEnd; i++) {
                int py = y + 4 + (i - visualStart) * font.lineHeight - visualShift;
                if (py + font.lineHeight <= y + 2 || py >= y + contentH + 4) continue;
                LineInfo line = lines.get(i);

                for (int m = 0; m < matchIndices.size(); m++) {
                    int mStart = matchIndices.get(m);
                    int mEnd = mStart + searchQuery.length();
                    if (mStart < line.rawEndIndex && mEnd > line.rawStartIndex) {
                        int selS = Math.max(mStart, line.rawStartIndex);
                        int selE = Math.min(mEnd, line.rawEndIndex);
                        int pxS = x + 4 + getPixelX(line, selS);
                        int pxE = x + 4 + getPixelX(line, selE);

                        boolean isCurrent = (m == currentMatchIdx);
                        int boxColor = 0x88FFFF00;
                        if (isCurrent) {
                            boxColor = 0xAAFF8800;
                            long elapsed = System.currentTimeMillis() - flashStartTime;
                            if (elapsed < 400 && (elapsed / 100) % 2 == 0) {
                                boxColor = 0xFFFFFFFF;
                            }
                        }
                        g.fill(pxS, py - 1, pxE, py + font.lineHeight, boxColor);
                    }
                }

                if (minPos < line.rawEndIndex && maxPos > line.rawStartIndex) {
                    int selS = Math.max(minPos, line.rawStartIndex);
                    int selE = Math.min(maxPos, line.rawEndIndex);
                    int pxS = x + 4 + getPixelX(line, selS);
                    int pxE = x + 4 + getPixelX(line, selE);
                    g.fill(pxS, py - 1, pxE, py + font.lineHeight, 0x66777777);
                }

                g.drawString(font, line.coloredString, x + 4, py, 0xFFFFFF, false);

                long elapsed = System.currentTimeMillis() - flashStartTime;
                for (int m = 0; m < matchIndices.size(); m++) {
                    int mStart = matchIndices.get(m);
                    int mEnd = mStart + searchQuery.length();
                    if (m == currentMatchIdx && mStart < line.rawEndIndex && mEnd > line.rawStartIndex) {
                        if (elapsed < 400 && (elapsed / 100) % 2 == 0) {
                            int selS = Math.max(mStart, line.rawStartIndex);
                            int selE = Math.min(mEnd, line.rawEndIndex);
                            int pxS = x + 4 + getPixelX(line, selS);
                            String snippet = value.substring(selS, selE);
                            g.drawString(font, snippet, pxS, py, 0xFF000000, false);
                        }
                    }
                }
            }

            if (isFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                for (int i = visualStart; i < visualEnd; i++) {
                    LineInfo line = lines.get(i);
                    if (cursorPos >= line.rawStartIndex && cursorPos <= line.rawEndIndex) {
                        if (cursorPos == line.rawEndIndex && i < lines.size() - 1) continue;
                        int cx = x + 4 + getPixelX(line, cursorPos);
                        int cy = y + 4 + (i - visualStart) * font.lineHeight - visualShift;
                        if (cy + font.lineHeight > y + 2 && cy < y + contentH + 4) {
                            g.fill(cx, cy - 1, cx + 1, cy + font.lineHeight, 0xFFFFFFFF);
                        }
                        break;
                    }
                }
            }

            if (maxScroll() > 0) {
                int sbX = x + width - 6;
                float ratio = (float) maxVisible / lines.size();
                int hH = Math.max(10, (int) ((contentH + 2) * ratio));
                int maxHandleYDist = (contentH + 2) - hH;
                int hY = y + 2 + (int) Math.round(maxHandleYDist * (visualScroll / maxScroll()));
                boolean hovered = mouseX >= sbX && mouseX < sbX + 4
                        && mouseY >= hY && mouseY < hY + hH;
                g.fill(sbX, y + 2, sbX + 4, y + contentH + 4, GuiTheme.current().scrollTrack());
                g.fill(
                        sbX,
                        hY,
                        sbX + 4,
                        hY + hH,
                        isDraggingScroll || hovered
                                ? GuiTheme.current().scrollThumbHover()
                                : GuiTheme.current().scrollThumb()
                );
            }

            int footerY = y + height - 14;
            g.fill(x, footerY - 4, x + width, y + height, 0xFF222222);

            if (errorMsg != null && !errorMsg.isEmpty()) {
                String displayError = font.width(errorMsg) > width - 100 ? font.plainSubstrByWidth(errorMsg, width - 110) + "..." : errorMsg;
                g.drawString(font, "❌ " + displayError, x + 4, footerY, 0xFF5555, false);
            } else if (!value.trim().isEmpty() && !value.trim().equals("{}")) {
                g.drawString(font, "✅", x + 4, footerY, 0x55FF55, false);
            }
            String lenStr = value.length() + "/32767";
            g.drawString(font, lenStr, x + width - 4 - font.width(lenStr), footerY, 0xAAAAAA, false);

            int outlineColor = isFocused ? 0xFFFFFFFF : 0xFFAAAAAA;
            if (!value.isEmpty() && !value.equals("{}")) {
                outlineColor = (errorMsg != null && !errorMsg.isEmpty()) ? 0xFFFF5555 : 0xFF55FF55;
            }
            g.renderOutline(x, y, width, height, outlineColor);
            g.renderOutline(x - 1, y - 1, width + 2, height + 2, outlineColor);
        }

        private int getCursorAt(double mx, double my) {
            double visualScroll = smoothScroll.follow(scrollOffset, maxScroll());
            int relY = (int) my - (y + 4);
            int cLine = (int) Math.floor(relY / (double) font.lineHeight + visualScroll);
            cLine = Math.max(0, Math.min(cLine, lines.size() - 1));
            LineInfo line = lines.get(cLine);
            return getRawIndex(line, (int) mx - (x + 4));
        }

        private boolean isWordChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_';
        }

        private int[] getWordBoundaries(String text, int col) {
            if (text == null || text.isEmpty()) return new int[]{0, 0};
            if (col >= text.length()) col = text.length() - 1;
            if (col < 0) col = 0;

            char c = text.charAt(col);
            boolean isAlphanumeric = isWordChar(c);
            boolean isWhitespace = Character.isWhitespace(c);

            int start = col;
            while (start > 0) {
                char prev = text.charAt(start - 1);
                if (isWhitespace && Character.isWhitespace(prev)) start--;
                else if (isAlphanumeric && isWordChar(prev)) start--;
                else if (!isWhitespace && !isAlphanumeric && !Character.isWhitespace(prev) && !isWordChar(prev)) start--;
                else break;
            }

            int end = col;
            while (end < text.length() - 1) {
                char next = text.charAt(end + 1);
                if (isWhitespace && Character.isWhitespace(next)) end++;
                else if (isAlphanumeric && isWordChar(next)) end++;
                else if (!isWhitespace && !isAlphanumeric && !Character.isWhitespace(next) && !isWordChar(next)) end++;
                else break;
            }
            return new int[]{start, end + 1};
        }

        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= x && mx <= x + width && my >= y && my <= y + height) {
                isFocused = true;
                if (mx >= x + width - 8 && maxScroll() > 0) {
                    isDraggingScroll = true; updateScroll(my);
                } else {
                    int c = getCursorAt(mx, my);
                    long currentTime = System.currentTimeMillis();

                    if (btn == 0 && (currentTime - lastClickTime < 300) && lastClickCursor == c) {
                        int[] bounds = getWordBoundaries(value, c);
                        selectPos = bounds[0];
                        cursorPos = bounds[1];
                        isDraggingText = false;
                    } else {
                        cursorPos = c;
                        if (!Screen.hasShiftDown()) selectPos = c;
                        isDraggingText = true;
                    }

                    lastClickTime = currentTime;
                    lastClickCursor = c;
                }
                return true;
            }
            isFocused = false; return false;
        }

        public boolean mouseDragged(double mx, double my) {
            if (isDraggingScroll) { updateScroll(my); return true; }
            if (isDraggingText) {
                cursorPos = getCursorAt(mx, my); scrollToCursor(); return true;
            }
            return false;
        }

        public boolean mouseReleased() {
            isDraggingScroll = isDraggingText = false;
            return isFocused;
        }

        public boolean mouseScrolled(double delta) {
            if (isFocused && maxScroll() > 0) {
                scrollOffset = smoothScroll.wheel(scrollOffset, delta, 1.0D / 3.0D, maxScroll());
                return true;
            }
            return false;
        }

        private void updateScroll(double my) {
            int maxVisible = getMaxVisibleLines();
            int contentH = maxVisible * font.lineHeight;
            float ratio = (float) maxVisible / lines.size();

            int hH = Math.max(10, (int) ((contentH + 2) * ratio));
            int trackTop = y + 2;
            int maxHandleYDist = (contentH + 2) - hH;

            if (maxHandleYDist > 0) {
                double relativeY = my - trackTop - (hH / 2.0);
                float progress = (float) (relativeY / maxHandleYDist);
                progress = Math.max(0.0f, Math.min(1.0f, progress));
                scrollOffset = progress * maxScroll();
                smoothScroll.snap(scrollOffset, maxScroll());
            }
        }

        private void insertText(String t) {
            deleteSelection();
            value = value.substring(0, cursorPos) + t + value.substring(cursorPos);
            cursorPos += t.length(); selectPos = cursorPos;
            onValueChange(); scrollToCursor();
        }

        private void deleteSelection() {
            if (cursorPos == selectPos) return;
            int min = Math.min(cursorPos, selectPos);
            int max = Math.max(cursorPos, selectPos);
            value = value.substring(0, min) + value.substring(max);
            cursorPos = min; selectPos = min;
            onValueChange();
            scrollToCursor();
        }

        private String getSelectedText() {
            return value.substring(Math.min(cursorPos, selectPos), Math.max(cursorPos, selectPos));
        }

        public boolean keyPressed(int keyCode) {
            if (!isFocused) return false;

            if (Screen.isSelectAll(keyCode)) { selectPos = 0; cursorPos = value.length(); scrollToCursor(); return true; }
            if (Screen.isCopy(keyCode)) { Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText()); return true; }
            if (Screen.isPaste(keyCode)) { insertText(Minecraft.getInstance().keyboardHandler.getClipboard()); return true; }
            if (Screen.isCut(keyCode)) { Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText()); deleteSelection(); return true; }

            if (keyCode == GLFW.GLFW_KEY_LEFT) { cursorPos = Math.max(0, cursorPos - 1); if (!Screen.hasShiftDown()) selectPos = cursorPos; scrollToCursor(); return true; }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { cursorPos = Math.min(value.length(), cursorPos + 1); if (!Screen.hasShiftDown()) selectPos = cursorPos; scrollToCursor(); return true; }
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                int cLine = 0;
                for (int i = 0; i < lines.size(); i++) { if (cursorPos >= lines.get(i).rawStartIndex && cursorPos <= lines.get(i).rawEndIndex) { cLine = i; break; } }
                int px = getPixelX(lines.get(cLine), cursorPos);
                if (keyCode == GLFW.GLFW_KEY_UP && cLine > 0) cursorPos = getRawIndex(lines.get(cLine - 1), px);
                if (keyCode == GLFW.GLFW_KEY_DOWN && cLine < lines.size() - 1) cursorPos = getRawIndex(lines.get(cLine + 1), px);
                if (!Screen.hasShiftDown()) selectPos = cursorPos; scrollToCursor(); return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (cursorPos != selectPos) deleteSelection();
                else if (cursorPos > 0) {
                    value = value.substring(0, cursorPos - 1) + value.substring(cursorPos); cursorPos--; selectPos = cursorPos; onValueChange(); scrollToCursor();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (cursorPos != selectPos) deleteSelection();
                else if (cursorPos < value.length()) {
                    value = value.substring(0, cursorPos) + value.substring(cursorPos + 1); onValueChange();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                for (LineInfo l : lines) if (cursorPos >= l.rawStartIndex && cursorPos <= l.rawEndIndex) { cursorPos = l.rawStartIndex; break; }
                if (!Screen.hasShiftDown()) selectPos = cursorPos; scrollToCursor(); return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                for (LineInfo l : lines) if (cursorPos >= l.rawStartIndex && cursorPos <= l.rawEndIndex) { cursorPos = l.rawEndIndex; break; }
                if (!Screen.hasShiftDown()) selectPos = cursorPos; scrollToCursor(); return true;
            }
            return false;
        }

        public boolean charTyped(char codePoint) {
            if (!isFocused) return false;
            if (SharedConstants.isAllowedChatCharacter(codePoint)) { insertText(String.valueOf(codePoint)); return true; }
            return false;
        }
    }
}
