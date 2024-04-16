package org.vatplanner.commons.utils;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;

/**
 * Common helper methods to work with {@link Properties}.
 */
public class PropertiesHelper {
    private PropertiesHelper() {
        // utility class; hide constructor
    }

    /**
     * Returns an {@link Optional} for the requested {@link Properties} entry that is empty if the value is either not
     * set or an empty string.
     *
     * @param properties {@link Properties} to retrieve the value from
     * @param key        key to look up on given {@link Properties}
     * @return value if set and not an empty string, otherwise an empty {@link Optional}
     */
    public static Optional<String> getNonEmpty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * Extracts all entries whose keys match the given {@link Predicate} to a new set of {@link Properties}.
     *
     * @param properties {@link Properties} to filter on
     * @param predicate  condition to filter keys on
     * @return new {@link Properties} holding all matched entries
     */
    public static Properties filterByKeys(Properties properties, Predicate<String> predicate) {
        Properties out = new Properties();

        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String) entry.getKey();
            if (predicate.test(key)) {
                out.put(key, entry.getValue());
            }
        }

        return out;
    }

    /**
     * Extracts all entries whose keys start with the given prefix to a new set of {@link Properties}, removing the
     * prefix.
     * <p>
     * Extracted sub-keys need to be collision-free and must not be empty.
     * </p>
     *
     * @param properties {@link Properties} to extract entries from
     * @param keyPrefix  key prefix to match and remove
     * @return new {@link Properties} holding all entries that started with the wanted prefix; key prefix is removed
     */
    public static Properties extract(Properties properties, String keyPrefix) {
        Properties out = new Properties();
        int prefixLength = keyPrefix.length();

        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String) entry.getKey();
            if (!key.startsWith(keyPrefix)) {
                continue;
            }

            String subKey = key.substring(prefixLength);
            if (subKey.isEmpty()) {
                throw new IllegalArgumentException("Sub-key is empty after prefix removal: key=\"" + key + "\", prefix=\"" + keyPrefix + "\"");
            }

            Object previous = out.put(subKey, entry.getValue());
            if (previous != null) {
                throw new IllegalArgumentException(
                    "Duplicate entry after prefix removal: key=\"" + key
                        + "\", prefix=\"" + keyPrefix
                        + "\", values=[" + previous
                        + ", " + entry.getValue()
                        + "]"
                );
            }
        }

        return out;
    }
}
