package org.vatplanner.commons.utils;

import java.util.function.Predicate;

/**
 * Common helper methods to improve handling Functional API.
 */
public class Functions {
    private Functions() {
        // utility class; hide constructor
    }

    /**
     * Negates the given {@link Predicate}.
     *
     * @param predicate {@link Predicate} to negate
     * @param <T>       type of {@link Predicate}
     * @return negated {@link Predicate}
     */
    public static <T> Predicate<T> not(Predicate<T> predicate) {
        return predicate.negate();
    }
}
