package org.vatplanner.commons.utils;

/**
 * A variation of {@link java.util.function.Consumer} that allows a checked {@link Exception} to be thrown.
 *
 * @param <T> type accepted by this consumer
 * @param <E> checked exception that may get thrown when invoking the consumer
 */
@FunctionalInterface
public interface ThrowingConsumer<T, E extends Exception> {
    /**
     * Processes the supplied object.
     *
     * @param t object to process
     * @throws E checked {@link Exception} that may get thrown during access
     */
    void accept(T t) throws E;
}
