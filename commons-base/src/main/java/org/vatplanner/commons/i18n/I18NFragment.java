package org.vatplanner.commons.i18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class I18NFragment {
    private final String output;

    private final String parameterName;
    private final String formatterMethod;

    private I18NFragment(String output, String parameterName, String formatterMethod) {
        this.output = output;
        this.parameterName = parameterName;
        this.formatterMethod = formatterMethod;
    }

    public static List<I18NFragment> parseMessage(String message) {
        // FIXME: unit tests

        List<I18NFragment> out = new ArrayList<>();

        char[] chars = message.toCharArray();
        int length = message.length();

        boolean inParameterInstruction = false;
        int start = 0;
        for (int i = 0; i < length; i++) {
            char ch = chars[i];
            char next = ((i + 1) < length) ? chars[i + 1] : '\0';
            if (inParameterInstruction) {
                if (ch == '}') {
                    inParameterInstruction = false;

                    String[] parameter = message.substring(start, i).split(":");
                    start = i + 1;
                    if (parameter.length > 2) {
                        throw new IllegalArgumentException("Too many parameter options: " + Arrays.toString(parameter));
                    }

                    String parameterName = parameter[0];
                    String formatterMethod = (parameter.length > 1) ? parameter[1] : null;

                    if (parameterName.isEmpty()) {
                        throw new IllegalArgumentException("Empty parameter name: \"" + message + "\"");
                    }

                    if (formatterMethod != null && formatterMethod.isEmpty()) {
                        throw new IllegalArgumentException("Empty formatter method for parameter '" + parameterName + "': \"" + message + "\"");
                    }

                    out.add(new I18NFragment(null, parameterName, formatterMethod));
                }
            } else if (ch == '$' && next == '{') {
                if (i > start) {
                    String verbatim = message.substring(start, i);
                    start = i + 2;

                    out.add(new I18NFragment(verbatim, null, null));
                }

                inParameterInstruction = true;
                i++; // skip next character
            }
        }
        if (inParameterInstruction) {
            throw new IllegalArgumentException("Invalid message format: \"" + message + "\"");
        }
        if (start < length) {
            out.add(new I18NFragment(message.substring(start), null, null));
        }

        return out;
    }

    public String process(Locale locale, Function<String, ?> parameterResolver, I18NFormatter parameterFormatter) {
        if (parameterName == null) {
            return output;
        }

        Object parameterObject = parameterResolver.apply(parameterName);
        if (formatterMethod == null) {
            return parameterFormatter.format(locale, parameterObject);
        } else {
            return parameterFormatter.format(locale, parameterObject, formatterMethod);
        }
    }
}