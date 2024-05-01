package org.vatplanner.commons.i18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates a message loaded by the given {@link I18NPropertiesLocator} in the format described by
 * {@link I18NFragment}.
 * <p>
 * Note that this I18N system is currently only suitable to process a small number of messages as it (mainly the
 * {@link I18NPropertiesLocator}) is horrendously unoptimized. It is only intended as a basic substitute where no
 * established I18N system is feasible to be used.
 * </p>
 */
public class I18NTranslator {
    private static final Logger LOGGER = LoggerFactory.getLogger(I18NTranslator.class);

    private final I18NPropertiesLocator locator;
    private final Locale[] locales;

    /**
     * Instantiates a new translator resolving messages for the given {@link Locale}s in descending order of preference
     * using the specified locator. The anonymous default locale will always be included as a final fallback having the
     * lowest priority and does not need to be specified.
     *
     * @param locator used to load messages
     * @param locales wanted {@link Locale}s in descending order of preference
     */
    public I18NTranslator(I18NPropertiesLocator locator, Locale... locales) {
        this.locales = locales;
        this.locator = locator;
    }

    /**
     * Formats the specified message, using the given parameter resolver and formatters.
     * <p>
     * Available translations will be sought in the order of preference specified at construction, see
     * {@link I18NPropertiesLocator} for implementation details. Each message consists of {@link I18NFragment}s that
     * may either hold a verbatim string or reference a parameter. All parameters are attempted to be retrieved from
     * the given resolver as objects, then passed on to the given {@link I18NFormatter}.
     * </p>
     * <p>
     * Parameter retrieval and formatting is allowed to throw an {@link IllegalArgumentException} in case the parameter
     * is unavailable or cannot be formatted. This will cause the message to be discarded and the next, less preferred
     * translation to be attempted instead, until no other translation is available.
     * </p>
     *
     * @param messageKey         key of the translation message
     * @param parameterResolver  resolves a parameter name to a value object
     * @param parameterFormatter formats a parameter value object as requested
     * @return translated and formatted message
     * @throws IllegalArgumentException if no translation is available or parameter retrieval/formatting failed for all attempted translations
     */
    public String format(String messageKey, Function<String, ?> parameterResolver, I18NFormatter parameterFormatter) {
        Map<Locale, List<I18NFragment>> messagesByLocale = locator.getMessage(messageKey, locales);

        Throwable lastProcessingCause = null;

        for (Locale locale : withFallbackToDefault(locales)) {
            List<I18NFragment> fragments = messagesByLocale.get(locale);
            if (fragments == null) {
                // message is not defined, try next locale
                continue;
            }

            StringBuilder sb = new StringBuilder();

            for (I18NFragment fragment : fragments) {
                try {
                    sb.append(fragment.process(locale, parameterResolver, parameterFormatter));
                } catch (IllegalArgumentException ex) {
                    LOGGER.debug("failed to process messageKey={}, locale={}, fragment={}", messageKey, locale, fragment, ex);
                    lastProcessingCause = ex;
                    sb = null;
                    break;
                }
            }

            if (sb == null) {
                // processing failed, try next locale
                continue;
            }

            return sb.toString();
        }

        if (lastProcessingCause == null) {
            throw new IllegalArgumentException(
                "formatting failed for all locales " + Arrays.stream(locales).map(Objects::toString).collect(Collectors.joining(", "))
                    + ", messageKey=" + messageKey
            );
        } else {
            throw new IllegalArgumentException(
                "formatting failed for all locales " + Arrays.stream(locales).map(Objects::toString).collect(Collectors.joining(", "))
                    + ", messageKey=" + messageKey + "; stacktrace describes last formatting failure: "
                    + lastProcessingCause.getMessage(),
                lastProcessingCause
            );
        }
    }

    private static List<Locale> withFallbackToDefault(Locale[] locales) {
        List<Locale> list = new ArrayList<>();
        for (Locale locale : locales) {
            list.add(locale);
        }
        list.add(null);
        return list;
    }
}
