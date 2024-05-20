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

    /**
     * Generates a {@link Stream} of elements from the given {@link Iterable} with odd and even elements zipped to
     * {@link ImmutablePair}s. The array must hold an even number of elements.
     *
     * @param flatValues holding an even number of elements alternating between left and right values
     * @param <T>        type of elements
     * @return stream of zipped elements with odd elements as left and even elements as right values
     */
    public static <T> Stream<ImmutablePair<T, T>> zipUnflattened(T[] flatValues) {
        if (flatValues.length % 2 != 0) {
            throw new IllegalArgumentException("Uneven number of elements provided (" + flatValues.length + ")");
        }

        return zipUnflattened(Arrays.asList(flatValues));
    }

    /**
     * Generates a {@link Stream} of elements from the given {@link Iterable} with odd and even elements zipped to
     * {@link ImmutablePair}s. The {@link Iterable} must return an even number of elements.
     *
     * @param flatValues holding an even number of elements alternating between left and right values
     * @param <T>        type of elements
     * @return stream of zipped elements with odd elements as left and even elements as right values
     */
    public static <T> Stream<ImmutablePair<T, T>> zipUnflattened(Iterable<T> flatValues) {
        Stream.Builder<ImmutablePair<T, T>> out = Stream.builder();

        Iterator<T> it = flatValues.iterator();
        while (it.hasNext()) {
            T leftValue = it.next();

            if (!it.hasNext()) {
                throw new IllegalArgumentException("Uneven number of elements provided");
            }
            T rightValue = it.next();

            out.add(new ImmutablePair<>(leftValue, rightValue));
        }

        return out.build();
    }
}
