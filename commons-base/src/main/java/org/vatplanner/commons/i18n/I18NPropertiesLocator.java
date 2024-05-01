package org.vatplanner.commons.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Resolves translation messages stored in properties files available through the JVM's classpath.
 * <p>
 * Note that this is currently horrendously unoptimized and only suitable for a small number of messages to be looked
 * up.
 * </p>
 * <p>
 * Messages are expected to be defined in the format described by {@link I18NFragment}, stored in properties files using
 * the following file name convention, assuming a <em>location reference class</em> of ${@code SomeClass}:
 * </p>
 * <ul>
 * <li>{@code SomeClass.properties} holds the default (fallback) translations and should always be present</li>
 * <li>{@code SomeClass.de.properties} would hold translations in German, not country-specific</li>
 * <li>{@code SomeClass.de_AT.properties} would hold translations in German, applicable only for Austria</li>
 * </ul>
 * <p>
 * For each requested {@link Locale}, the most specific file available will be preferred.
 * </p>
 * <p>
 * The properties files must be encoded as UTF-8 which may deviate from IDE defaults (in particular, editing in
 * IntelliJ may currently not be possible/require additional transcoding).
 * </p>
 * <p>
 * Multiple <em>location reference classes</em> can be specified for lookup, in case multiple options exist.
 * The object's class will be used to locate the properties files to be loaded:
 * </p>
 * <ul>
 * <li>the simple class name will be used to construct the file name to be loaded</li>
 * <li>the class-specific classloader will be used to resolve the properties file resource</li>
 * <li>the package path is expected to be maintained</li>
 * </ul>
 */
public class I18NPropertiesLocator {
    private final String propertyPrefix;
    private final List<Class<?>> locationReferenceClasses;

    /**
     * Initializes a new locator searching messages for the classes of all specified objects in order of preference.
     * <p>
     * See {@link I18NPropertiesLocator} class JavaDoc for details.
     * </p>
     *
     * @param locationReferenceObjects objects whose classes to load properties based on; in order of descending preference
     */
    public I18NPropertiesLocator(Object... locationReferenceObjects) {
        this("", locationReferenceObjects);
    }

    /**
     * Initializes a new locator searching messages using the given prefix for the classes of all specified objects in
     * order of preference.
     * <p>
     * See {@link I18NPropertiesLocator} class JavaDoc for details.
     * </p>
     *
     * @param propertyPrefix           prefix to all message property keys
     * @param locationReferenceObjects objects whose classes to load properties based on; in order of descending preference
     */
    public I18NPropertiesLocator(String propertyPrefix, Object... locationReferenceObjects) {
        this(propertyPrefix, Arrays.stream(locationReferenceObjects).map(Object::getClass).toArray(Class[]::new));
    }

    /**
     * Initializes a new locator searching messages for all specified classes in order of preference.
     * <p>
     * See {@link I18NPropertiesLocator} class JavaDoc for details.
     * </p>
     *
     * @param locationReferenceClasses classes to load properties based on; in order of descending preference
     */
    public I18NPropertiesLocator(Class<?>... locationReferenceClasses) {
        this("", locationReferenceClasses);
    }

    /**
     * Initializes a new locator searching messages using the given prefix for all specified classes in order of
     * preference.
     * <p>
     * See {@link I18NPropertiesLocator} class JavaDoc for details.
     * </p>
     *
     * @param propertyPrefix           prefix to all message property keys
     * @param locationReferenceClasses classes to load properties based on; in order of descending preference
     */
    public I18NPropertiesLocator(String propertyPrefix, Class<?>... locationReferenceClasses) {
        this.propertyPrefix = propertyPrefix;
        this.locationReferenceClasses = Arrays.asList(locationReferenceClasses);
    }

    /**
     * Loads all available messages for the requested {@link Locale}s as {@link I18NFragment} sequences. The anonymous
     * default translation will also be loaded, indexed by {@code null} in place of a {@link Locale}.
     *
     * @param messageKey    properties key identifying the message to be loaded
     * @param wantedLocales locales to load in no particular order
     * @return message fragment sequences indexed by {@link Locale}, incl. {@code null} {@link Locale} for anonymous
     *     default translation
     */
    public Map<Locale, List<I18NFragment>> getMessage(String messageKey, Locale... wantedLocales) {
        Map<Locale, List<I18NFragment>> out = new HashMap<>();

        HashSet<Locale> wantedLocalesSet = new HashSet<>();
        wantedLocalesSet.add(null); // default locale
        for (Locale wantedLocale : wantedLocales) {
            wantedLocalesSet.add(wantedLocale);
        }

        for (Locale wantedLocale : wantedLocalesSet) {
            for (Class<?> locationReferenceClass : locationReferenceClasses) {
                String message = loadMessage(wantedLocale, locationReferenceClass, messageKey);
                if (message != null) {
                    out.put(wantedLocale, I18NFragment.parseMessage(message));
                }
            }
        }

        return out;
    }

    private String loadMessage(Locale wantedLocale, Class<?> locationReferenceClass, String messageKey) {
        String basePath = "/" + locationReferenceClass.getCanonicalName().replace('.', '/');

        if (wantedLocale == null) {
            return loadMessage(locationReferenceClass, basePath + ".properties", messageKey);
        }

        String country = wantedLocale.getCountry();
        if (!country.isEmpty()) {
            String message = loadMessage(
                locationReferenceClass,
                basePath + "." + wantedLocale.getLanguage() + "_" + wantedLocale.getCountry() + ".properties",
                messageKey
            );

            if (message != null) {
                return message;
            }
        }

        return loadMessage(
            locationReferenceClass,
            basePath + "." + wantedLocale.getLanguage() + ".properties",
            messageKey
        );
    }

    private String loadMessage(Class<?> locationReferenceClass, String resourceName, String messageKey) {
        try (
            InputStream is = locationReferenceClass.getResourceAsStream(resourceName);
            InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        ) {
            Properties properties = new Properties();
            properties.load(isr);

            return properties.getProperty(propertyPrefix + messageKey);
        } catch (NullPointerException ex) {
            // file does not exist; ignore
            return null;
        } catch (IOException ex) {
            throw new UnsupportedOperationException("failed to read resource " + resourceName + " in reference to " + locationReferenceClass, ex);
        }
    }
}
