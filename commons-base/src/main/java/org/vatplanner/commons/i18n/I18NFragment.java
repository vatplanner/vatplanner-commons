package org.vatplanner.commons.i18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Fragment of a larger translation message, holding verbatim text or a parameter definition.
 * <p>
 * Messages are parsed to fragments using {@link #parseMessage(String)}. The messages can be parameterized in the form
 * of {@code ${parameterName:formatterMethodName}} or {@code ${parameterName}} if no specific formatter is requested.
 * </p>
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li>{@code "Hello ${firstName}!"} would format to {@code "Hello Joe!"} at runtime, having resolved {@code firstName}
 * to {@code "Joe"}.</li>
 * <li>{@code "Total: ${currency:symbol}${total:roundTwoDigits}"} would format to {@code "Total: $1.23"} having resolved
 * <ul>
 * <li>{@code total} to a double of {@code 1.2299875}, formatted by method {@code "roundTwoDigits"} to {@code "1.23"}
 * using {@code .} as the decimal point for an English {@link Locale}</li>
 * <li>{@code currency} to a {@link java.util.Currency} object (or similar), formatted by method {@code "symbol"} to
 * {@code "$"}</li>
 * </ul>
 * </li>
 * <li>{@code "Summe: ${total:roundTwoDigits}${currency:symbol}"} would format to {@code "Summe: 1.23€"} having resolved
 * <ul>
 * <li>{@code total} to a double of {@code 1.2299875}, formatted by method {@code "roundTwoDigits"} to {@code "1,23"}
 * using {@code ,} as the decimal point for a German {@link Locale}</li>
 * <li>{@code currency} to a {@link java.util.Currency} object (or similar), formatted by method {@code "symbol"} to
 * {@code "€"}</li>
 * </ul>
 * </li>
 * </ul>
 * <p>
 * Parameters will be resolved by {@link I18NTranslator} through {@link #process(Locale, Function, I18NFormatter)}, so
 * that the {@link I18NFragment} itself remains independent of runtime specific information such as {@link Locale} or
 * parameter values.
 * </p>
 */
public class I18NFragment {
    private final String output;

    private final String parameterName;
    private final String formatterMethod;

    private I18NFragment(String output, String parameterName, String formatterMethod) {
        this.output = output;
        this.parameterName = parameterName;
        this.formatterMethod = formatterMethod;
    }

    /**
     * Parses the given translation message into a sequence of {@link I18NFragment}s.
     * <p>
     * See {@link I18NFragment} class JavaDoc for a description of the supported format.
     * </p>
     *
     * @param message message to parse into fragments
     * @return fragments of the message, in sequence
     */
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

    /**
     * Processes the fragment to a literal string for composition of the translated message.
     *
     * @param locale             {@link Locale} to be used if the fragment needs formatting
     * @param parameterResolver  resolves a parameter name to a value object
     * @param parameterFormatter formats a parameter value object as requested
     * @return formatted fragment
     */
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