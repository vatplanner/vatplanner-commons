package org.vatplanner.commons.utils;

import java.util.Optional;
import java.util.Properties;

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
}
