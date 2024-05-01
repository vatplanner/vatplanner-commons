package org.vatplanner.commons.i18n;

import java.util.Locale;

/**
 * Formats a parameter value object for inclusion to a translation message.
 */
public interface I18NFormatter {
    /**
     * Formats the given object for the specified {@link Locale}. No specific format is requested; default formatting
     * should be applied. The {@link Locale} should be followed as closely as possible so that it matches the
     * surrounding message.
     * <p>
     * If formatting is not possible, an {@link IllegalArgumentException} is allowed to be thrown to make the
     * {@link I18NTranslator} fall through to the next available translation.
     * </p>
     *
     * @param locale {@link Locale} of the surrounding message; may be {@code null}
     * @param obj    object to be formatted using the default method
     * @return human-readable string representation of the value, matching the specified {@link Locale}
     * @throws IllegalArgumentException if {@link Locale} is not handled or object cannot be formatted;
     *                                  triggers fallback to next best available translation
     */
    String format(Locale locale, Object obj);

    /**
     * Formats the given object for the specified {@link Locale} using the requested formatting method.
     * The {@link Locale} should be followed as closely as possible so that it matches the surrounding message.
     * <p>
     * If formatting is not possible, an {@link IllegalArgumentException} is allowed to be thrown to make the
     * {@link I18NTranslator} fall through to the next available translation.
     * </p>
     *
     * @param locale     {@link Locale} of the surrounding message; may be {@code null}
     * @param obj        object to be formatted
     * @param methodName name of formatting method to be applied
     * @return human-readable string representation of the value, matching the specified {@link Locale}
     * @throws IllegalArgumentException if {@link Locale} or formatting method is not handled or object cannot be formatted;
     *                                  triggers fallback to next best available translation
     */
    String format(Locale locale, Object obj, String methodName);
}
