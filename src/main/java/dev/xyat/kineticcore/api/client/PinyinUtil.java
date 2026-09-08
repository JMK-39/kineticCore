package dev.xyat.kineticcore.api.client;

import com.github.promeg.pinyinhelper.Pinyin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PinyinUtil {
    private static final SearchData EMPTY = new SearchData("", "", "", new String[0], "");
    private static final ThreadLocal<MatchBuffer> MATCH_BUFFER = ThreadLocal.withInitial(MatchBuffer::new);

    private PinyinUtil() {
    }

    public static String getSearchData(String str) {
        return prepare(str).searchData();
    }

    public static SearchData prepare(String str) {
        if (str == null || str.isEmpty()) {
            return EMPTY;
        }

        String rawLower = str.toLowerCase(Locale.ROOT);
        StringBuilder full = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        StringBuilder spaced = new StringBuilder();
        List<String> syllables = new ArrayList<>(Math.min(str.length(), 32));

        for (char c : str.toCharArray()) {
            if (!Pinyin.isChinese(c)) {
                continue;
            }

            String pinyin = Pinyin.toPinyin(c);
            if (pinyin == null || pinyin.isEmpty()) {
                continue;
            }

            String lower = pinyin.toLowerCase(Locale.ROOT);
            full.append(lower);
            initials.append(lower.charAt(0));
            if (spaced.length() > 0) {
                spaced.append(' ');
            }
            spaced.append(lower);
            syllables.add(lower);
        }

        if (syllables.isEmpty()) {
            return new SearchData(rawLower, "", "", new String[0], "");
        }

        String fullValue = full.toString();
        String initialsValue = initials.toString();
        String searchData = fullValue + " " + initialsValue + " " + spaced;
        return new SearchData(rawLower, fullValue, initialsValue, syllables.toArray(String[]::new), searchData);
    }

    public static boolean match(String text, String query) {
        return prepare(text).matches(query);
    }

    public static boolean match(SearchData data, String query) {
        if (data == null || query == null || query.isBlank()) {
            return false;
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
        if (lowerQuery.isEmpty()) {
            return false;
        }

        if (!data.rawLower.isEmpty() && data.rawLower.contains(lowerQuery)) {
            return true;
        }

        String compact = compactLatin(lowerQuery);
        if (compact.isEmpty() || data.syllables.length == 0) {
            return false;
        }

        if (data.full.contains(compact) || data.initials.contains(compact)) {
            return true;
        }

        return matchHybrid(data.syllables, compact);
    }

    private static String compactLatin(String input) {
        StringBuilder out = null;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            if (out == null) {
                out = new StringBuilder(input.length());
                out.append(input, 0, i);
            }
        }
        return out == null ? input : out.toString();
    }

    private static boolean matchHybrid(String[] syllables, String query) {
        int queryLength = query.length();
        MatchBuffer buffer = MATCH_BUFFER.get();
        buffer.ensureCapacity(queryLength + 1);
        boolean[] current = buffer.current;
        boolean[] next = buffer.next;
        Arrays.fill(current, 0, queryLength + 1, false);
        current[0] = true;

        for (String syllable : syllables) {
            Arrays.fill(next, 0, queryLength + 1, false);
            current[0] = true;

            for (int position = 0; position < queryLength; position++) {
                if (!current[position]) {
                    continue;
                }

                int max = Math.min(syllable.length(), queryLength - position);
                for (int length = 1; length <= max; length++) {
                    if (query.charAt(position + length - 1) != syllable.charAt(length - 1)) {
                        break;
                    }
                    int end = position + length;
                    if (end == queryLength) {
                        return true;
                    }
                    next[end] = true;
                }
            }

            boolean[] swap = current;
            current = next;
            next = swap;
        }

        return false;
    }

    public static final class SearchData {
        private final String rawLower;
        private final String full;
        private final String initials;
        private final String[] syllables;
        private final String searchData;

        private SearchData(String rawLower, String full, String initials, String[] syllables, String searchData) {
            this.rawLower = rawLower;
            this.full = full;
            this.initials = initials;
            this.syllables = syllables;
            this.searchData = searchData;
        }

        public String full() {
            return full;
        }

        public String initials() {
            return initials;
        }

        public String searchData() {
            return searchData;
        }

        public boolean matches(String query) {
            return PinyinUtil.match(this, query);
        }
    }

    private static final class MatchBuffer {
        private boolean[] current = new boolean[32];
        private boolean[] next = new boolean[32];

        private void ensureCapacity(int required) {
            if (current.length >= required) {
                return;
            }
            int size = current.length;
            while (size < required) {
                size <<= 1;
            }
            current = new boolean[size];
            next = new boolean[size];
        }
    }
}
