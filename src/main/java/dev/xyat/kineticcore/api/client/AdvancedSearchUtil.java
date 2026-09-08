package dev.xyat.kineticcore.api.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdvancedSearchUtil {
    public static boolean match(String text, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        if (text == null || text.isBlank()) {
            return false;
        }

        String preparedText = normalizeForSearch(text);
        String[] tokens = prepareQuery(query);
        if (tokens.length == 0) {
            return true;
        }

        PinyinUtil.SearchData pinyin = null;
        for (String token : tokens) {
            if (preparedText.contains(token)) {
                continue;
            }
            if (pinyin == null) {
                pinyin = PinyinUtil.prepare(text);
            }
            if (!pinyin.matches(token)) {
                return false;
            }
        }
        return true;
    }

    public static String normalizeForSearch(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String lower = input.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        boolean lastSpace = true;

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isSearchChar(c)) {
                builder.append(c);
                lastSpace = false;
            } else if (!lastSpace) {
                builder.append(' ');
                lastSpace = true;
            }
        }

        int length = builder.length();
        if (length > 0 && builder.charAt(length - 1) == ' ') {
            builder.setLength(length - 1);
        }
        return builder.toString();
    }

    public static String[] prepareQuery(String query) {
        String normalized = normalizeForSearch(query);
        if (normalized.isBlank()) {
            return new String[0];
        }

        List<String> tokens = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= normalized.length(); i++) {
            if (i == normalized.length() || normalized.charAt(i) == ' ') {
                if (i > start) {
                    tokens.add(normalized.substring(start, i));
                }
                start = i + 1;
            }
        }
        return tokens.toArray(String[]::new);
    }

    public static boolean matchPrepared(String preparedText, String[] preparedQueryTokens) {
        if (preparedQueryTokens == null || preparedQueryTokens.length == 0) {
            return true;
        }
        if (preparedText == null || preparedText.isBlank()) {
            return false;
        }

        for (String token : preparedQueryTokens) {
            if (token != null && !token.isBlank() && !preparedText.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSearchChar(char c) {
        return Character.isLetterOrDigit(c)
                || c == '_'
                || c == '-'
                || c == '.'
                || c == ':'
                || c == '@'
                || c == '#'
                || isChinese(c);
    }

    private static boolean isChinese(char c) {
        return c >= '一' && c <= '鿿';
    }
}
