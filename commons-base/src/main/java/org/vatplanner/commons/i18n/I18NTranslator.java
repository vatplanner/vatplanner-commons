package org.vatplanner.commons.i18n;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class I18NTranslator {
    private static final Logger LOGGER = LoggerFactory.getLogger(I18NTranslator.class);

    private final I18NPropertiesLocator locator;
    private final Locale[] locales;

    public I18NTranslator(I18NPropertiesLocator locator, Locale... locales) {
        this.locales = locales;
        this.locator = locator;
    }

    public String format(String messageKey, Function<String, ?> parameterResolver, I18NFormatter parameterFormatter) {
        Map<Locale, List<I18NFragment>> messagesByLocale = locator.getMessage(messageKey, locales);

        Throwable lastProcessingCause = null;

        for (Locale locale : locales) {
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
                    + ", messageKey=" + messageKey + "; stracktrace describes last formatting failure: "
                    + lastProcessingCause.getMessage(),
                lastProcessingCause
            );
        }
    }
}
