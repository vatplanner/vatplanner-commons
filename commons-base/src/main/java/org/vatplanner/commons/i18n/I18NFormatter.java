package org.vatplanner.commons.i18n;

import java.util.Locale;

public interface I18NFormatter {
    String format(Locale locale, Object obj);

    String format(Locale locale, Object obj, String methodName);
}
