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

public class I18NPropertiesLocator {
    private final String propertyPrefix;
    private final List<Class<?>> locationReferenceClasses;

    public I18NPropertiesLocator(Object... locationReferenceObjects) {
        this("", locationReferenceObjects);
    }

    public I18NPropertiesLocator(String propertyPrefix, Object... locationReferenceObjects) {
        this(propertyPrefix, Arrays.stream(locationReferenceObjects).map(Object::getClass).toArray(Class[]::new));
    }

    public I18NPropertiesLocator(Class<?>... locationReferenceClasses) {
        this("", locationReferenceClasses);
    }

    public I18NPropertiesLocator(String propertyPrefix, Class<?>... locationReferenceClasses) {
        this.propertyPrefix = propertyPrefix;
        this.locationReferenceClasses = Arrays.asList(locationReferenceClasses);
    }

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
