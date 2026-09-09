package dev.xyat.kineticcore.api.client.search;

import com.github.promeg.pinyinhelper.Pinyin;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class KineticSearch {
    private static final PinyinData EMPTY_PINYIN = new PinyinData("", "", "", new String[0], "");
    private static final ThreadLocal<MatchBuffer> MATCH_BUFFER = ThreadLocal.withInitial(MatchBuffer::new);

    private static List<String> attributeDictionary;
    private static List<String> potionDictionary;
    private static List<String> damageDictionary;
    private static List<String> specificDamageDictionary;

    private KineticSearch() {
    }

    public static boolean match(String text, String query) {
        if (query == null || query.isBlank()) return true;
        if (text == null || text.isBlank()) return false;

        String preparedText = normalize(text);
        String[] tokens = queryTokens(query);
        if (tokens.length == 0) return true;

        PinyinData pinyin = null;
        for (String token : tokens) {
            if (preparedText.contains(token)) continue;
            if (pinyin == null) pinyin = preparePinyin(text);
            if (!pinyin.matches(token)) return false;
        }
        return true;
    }

    public static String normalize(String input) {
        if (input == null || input.isBlank()) return "";
        String lower = input.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        boolean lastSpace = true;

        for (int index = 0; index < lower.length(); index++) {
            char c = lower.charAt(index);
            if (isSearchChar(c)) {
                builder.append(c);
                lastSpace = false;
            } else if (!lastSpace) {
                builder.append(' ');
                lastSpace = true;
            }
        }

        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) == ' ') {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    public static String[] queryTokens(String query) {
        String normalized = normalize(query);
        return normalized.isBlank() ? new String[0] : normalized.split(" +");
    }

    public static boolean matchPrepared(String preparedText, String[] preparedQueryTokens) {
        if (preparedQueryTokens == null || preparedQueryTokens.length == 0) return true;
        if (preparedText == null || preparedText.isBlank()) return false;
        for (String token : preparedQueryTokens) {
            if (token != null && !token.isBlank() && !preparedText.contains(token)) return false;
        }
        return true;
    }

    public static String pinyin(String text) {
        return preparePinyin(text).searchData();
    }

    public static PinyinData preparePinyin(String text) {
        if (text == null || text.isEmpty()) return EMPTY_PINYIN;
        String rawLower = text.toLowerCase(Locale.ROOT);
        StringBuilder full = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        StringBuilder spaced = new StringBuilder();
        List<String> syllables = new ArrayList<>(Math.min(text.length(), 32));

        for (char c : text.toCharArray()) {
            if (!Pinyin.isChinese(c)) continue;
            String pinyin = Pinyin.toPinyin(c);
            if (pinyin == null || pinyin.isEmpty()) continue;
            String lower = pinyin.toLowerCase(Locale.ROOT);
            full.append(lower);
            initials.append(lower.charAt(0));
            if (!spaced.isEmpty()) spaced.append(' ');
            spaced.append(lower);
            syllables.add(lower);
        }

        if (syllables.isEmpty()) return new PinyinData(rawLower, "", "", new String[0], "");
        String fullValue = full.toString();
        String initialsValue = initials.toString();
        return new PinyinData(
                rawLower,
                fullValue,
                initialsValue,
                syllables.toArray(String[]::new),
                fullValue + " " + initialsValue + " " + spaced
        );
    }

    public static String cleanTranslatedName(String... keys) {
        for (String key : keys) {
            String translated = Component.translatable(key).getString();
            if (!translated.equals(key)
                    && !translated.contains("%")
                    && translated.matches(".*[\\u4e00-\\u9fa5].*")) {
                return translated;
            }
        }
        return null;
    }

    public static String dictionaryName(String id, List<String> dictionary) {
        if (id == null || dictionary == null) return id;
        for (String entry : dictionary) {
            if (entry.startsWith(id + " - ")) return entry.substring(entry.indexOf(" - ") + 3);
        }
        return id;
    }

    public static List<String> attributeDictionary() {
        if (attributeDictionary == null) {
            attributeDictionary = ForgeRegistries.ATTRIBUTES.getEntries().stream().map(entry -> {
                String id = entry.getKey().location().toString();
                String translated = cleanTranslatedName(entry.getValue().getDescriptionId());
                return translated != null ? id + " - " + translated : id;
            }).collect(Collectors.toList());
        }
        return attributeDictionary;
    }

    public static List<String> potionDictionary() {
        if (potionDictionary == null) {
            potionDictionary = ForgeRegistries.MOB_EFFECTS.getEntries().stream().map(entry -> {
                String id = entry.getKey().location().toString();
                String translated = cleanTranslatedName(entry.getValue().getDescriptionId());
                return translated != null ? id + " - " + translated : id;
            }).collect(Collectors.toList());
        }
        return potionDictionary;
    }

    public static List<String> damageDictionary() {
        if (damageDictionary == null) damageDictionary = buildDamageDictionary(true);
        return damageDictionary;
    }

    public static List<String> specificDamageDictionary() {
        if (specificDamageDictionary == null) specificDamageDictionary = buildDamageDictionary(false);
        return specificDamageDictionary;
    }

    public static List<String> getAttributeDict() {
        return attributeDictionary();
    }

    public static List<String> getPotionDict() {
        return potionDictionary();
    }

    public static List<String> getDamageDict() {
        return damageDictionary();
    }

    public static List<String> getSpecificDamageDict() {
        return specificDamageDictionary();
    }

    public static void clearDictionaries() {
        attributeDictionary = null;
        potionDictionary = null;
        damageDictionary = null;
        specificDamageDictionary = null;
    }

    private static List<String> buildDamageDictionary(boolean includeAllAndTags) {
        List<String> result = new ArrayList<>();
        if (includeAllAndTags) {
            String translated = cleanTranslatedName("gui.kineticcore.damage.all");
            result.add(translated != null ? "all - " + translated : "all");
        }

        if (Minecraft.getInstance().level == null) return result;
        var registry = Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Set<String> addedMessageIds = new HashSet<>();
        registry.entrySet().forEach(entry -> {
            ResourceLocation location = entry.getKey().location();
            String messageId = entry.getValue().msgId();
            if (!addedMessageIds.add(messageId)) return;
            String translated = cleanTranslatedName(
                    "damage_type." + messageId.replace(":", "."),
                    "dmg." + messageId,
                    "damage_type." + location.getNamespace() + "." + location.getPath()
            );
            result.add(translated != null ? messageId + " - " + translated : messageId);
        });

        if (includeAllAndTags) {
            registry.getTagNames().forEach(tag -> {
                ResourceLocation location = tag.location();
                String translated = cleanTranslatedName(
                        "tag.damage_type." + location.getNamespace() + "." + location.getPath(),
                        "tag." + location.getNamespace() + "." + location.getPath(),
                        "tag." + location.getPath()
                );
                result.add(translated != null ? "#" + location + " - " + translated : "#" + location);
            });
        }
        return result;
    }

    private static boolean isSearchChar(char c) {
        return Character.isLetterOrDigit(c)
                || c == '_'
                || c == '-'
                || c == '.'
                || c == ':'
                || c == '@'
                || c == '#'
                || (c >= '一' && c <= '鿿');
    }

    private static boolean matchPinyin(PinyinData data, String query) {
        if (data == null || query == null || query.isBlank()) return false;
        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
        if (lowerQuery.isEmpty()) return false;
        if (!data.rawLower().isEmpty() && data.rawLower().contains(lowerQuery)) return true;

        String compact = compactLatin(lowerQuery);
        if (compact.isEmpty() || data.syllables().length == 0) return false;
        if (data.full().contains(compact) || data.initials().contains(compact)) return true;
        return matchHybrid(data.syllables(), compact);
    }

    private static String compactLatin(String input) {
        StringBuilder out = null;
        for (int index = 0; index < input.length(); index++) {
            char c = input.charAt(index);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (out != null) out.append(c);
                continue;
            }
            if (out == null) {
                out = new StringBuilder(input.length());
                out.append(input, 0, index);
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
                if (!current[position]) continue;
                int max = Math.min(syllable.length(), queryLength - position);
                for (int length = 1; length <= max; length++) {
                    if (query.charAt(position + length - 1) != syllable.charAt(length - 1)) break;
                    int end = position + length;
                    if (end == queryLength) return true;
                    next[end] = true;
                }
            }
            boolean[] swap = current;
            current = next;
            next = swap;
        }
        return false;
    }

    public record PinyinData(
            String rawLower,
            String full,
            String initials,
            String[] syllables,
            String searchData
    ) {
        public boolean matches(String query) {
            return KineticSearch.matchPinyin(this, query);
        }
    }

    public static final class Model<T> {
        private final List<T> source = new ArrayList<>();
        private final List<T> visible = new ArrayList<>();
        private final BiPredicate<T, String> matcher;
        private Comparator<T> comparator;

        public Model(Collection<T> source, Function<T, String> searchData) {
            this(source, (entry, query) -> KineticSearch.match(searchData.apply(entry), query));
        }

        public Model(Collection<T> source, BiPredicate<T, String> matcher) {
            this.matcher = matcher;
            setSource(source);
        }

        public void setSource(Collection<T> entries) {
            source.clear();
            if (entries != null) source.addAll(entries);
        }

        public void setComparator(Comparator<T> comparator) {
            this.comparator = comparator;
        }

        public void refresh(String query) {
            String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
            visible.clear();
            for (T entry : source) {
                if (normalized.isEmpty() || matcher.test(entry, normalized)) visible.add(entry);
            }
            if (comparator != null) visible.sort(comparator);
        }

        public List<T> items() {
            return visible;
        }
    }

    public static final class EditedTracker<T> {
        private final java.util.Map<T, Long> editedOrder = new java.util.HashMap<>();
        private long sequence;

        public void refresh(Collection<T> entries, java.util.function.Predicate<T> editedPredicate) {
            editedOrder.clear();
            for (T entry : entries) if (editedPredicate.test(entry)) editedOrder.put(entry, 0L);
        }

        public boolean update(T entry, boolean edited) {
            boolean wasEdited = editedOrder.containsKey(entry);
            if (edited) {
                if (!wasEdited) {
                    editedOrder.put(entry, ++sequence);
                    return true;
                }
                return false;
            }
            return editedOrder.remove(entry) != null;
        }

        public boolean isEdited(T entry) {
            return editedOrder.containsKey(entry);
        }

        public Comparator<T> comparator(Comparator<T> fallback) {
            return (left, right) -> {
                boolean leftEdited = isEdited(left);
                boolean rightEdited = isEdited(right);
                if (leftEdited != rightEdited) return leftEdited ? -1 : 1;
                if (leftEdited) {
                    int recent = Long.compare(
                            editedOrder.getOrDefault(right, 0L),
                            editedOrder.getOrDefault(left, 0L)
                    );
                    if (recent != 0) return recent;
                }
                return fallback.compare(left, right);
            };
        }

        public void clear() {
            editedOrder.clear();
        }
    }

    private static final class MatchBuffer {
        private boolean[] current = new boolean[32];
        private boolean[] next = new boolean[32];

        private void ensureCapacity(int required) {
            if (current.length >= required) return;
            int size = current.length;
            while (size < required) size <<= 1;
            current = new boolean[size];
            next = new boolean[size];
        }
    }
}
