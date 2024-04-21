package org.vatplanner.commons.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Helper methods to work with {@link Stream}s.
 */
public class Streams {
    /**
     * Generates a {@link Stream} of elements from both inputs zipped to {@link ImmutablePair}s.
     * Both inputs must have the same size.
     *
     * @param left  left-hand values
     * @param right right-hand values
     * @param <L>   left-hand type
     * @param <R>   right-hand type
     * @return stream of zipped elements
     */
    public static <L, R> Stream<ImmutablePair<L, R>> zipExactly(Collection<L> left, R[] right) {
        return zipExactly(left, Arrays.asList(right));
    }

    /**
     * Generates a {@link Stream} of elements from both {@link Collection}s zipped to {@link ImmutablePair}s.
     * Both {@link Collection}s must have the same size.
     *
     * @param left  left-hand values
     * @param right right-hand values
     * @param <L>   left-hand type
     * @param <R>   right-hand type
     * @return stream of zipped elements
     */
    public static <L, R> Stream<ImmutablePair<L, R>> zipExactly(Collection<L> left, Collection<R> right) {
        if (left.size() != right.size()) {
            throw new IllegalArgumentException("Sizes do not match: left=" + left.size() + ", right=" + right.size());
        }

        Stream.Builder<ImmutablePair<L, R>> out = Stream.builder();

        Iterator<L> itLeft = left.iterator();
        Iterator<R> itRight = right.iterator();

        while (itLeft.hasNext() && itRight.hasNext()) {
            out.add(new ImmutablePair<>(itLeft.next(), itRight.next()));
        }

        return out.build();
    }
}
