package org.vatplanner.commons.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;

/**
 * Common helper methods to work with {@link Locale}s.
 */
public class Locales {
    private Locales() {
        // utility class; hide constructor
    }

    /**
     * Returns the first item of {@code availableLocales} that matches the {@code wantedLocales} either exactly or
     * just by language, in order.
     *
     * @param wantedLocales    wanted {@link Locale}s to search for, exactly or just by language
     * @param availableLocales possible {@link Locale}s to be returned; {@code null} will be ignored
     * @return closest matching {@link Locale}
     */
    public static Optional<Locale> findClosestMatch(Collection<Locale> wantedLocales, Collection<Locale> availableLocales) {
        HashSet<Locale> indexedAvailableLocales = new HashSet<>(availableLocales);

        for (Locale wantedLocale : wantedLocales) {
            // try for exact match
            if (indexedAvailableLocales.contains(wantedLocale)) {
                return Optional.of(wantedLocale);
            }

            // try without country, if set
            if (!wantedLocale.getCountry().isEmpty()) {
                Locale onlyLanguage = new Locale.Builder().setLanguage(wantedLocale.getLanguage()).build();
                if (indexedAvailableLocales.contains(onlyLanguage)) {
                    return Optional.of(onlyLanguage);
                }
            }
        }

        return Optional.empty();
    }
}
