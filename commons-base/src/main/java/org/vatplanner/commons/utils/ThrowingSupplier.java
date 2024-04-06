package org.vatplanner.commons.utils;

/**
 * A variation of {@link java.util.function.Supplier} that allows a checked {@link Exception} to be thrown.
 *
 * @param <T> type returned by this supplier
 * @param <E> checked exception that may get thrown when invoking the supplier
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception> {
    /**
     * Returns the object supplied by this instance.
     *
     * @return supplied object
     * @throws E checked {@link Exception} that may get thrown during access
     */
    T get() throws E;
}
