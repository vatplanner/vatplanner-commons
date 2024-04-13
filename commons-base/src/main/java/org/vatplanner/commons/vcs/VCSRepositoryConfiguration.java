package org.vatplanner.commons.vcs;

import java.io.File;
import java.util.Optional;
import java.util.Properties;

import org.vatplanner.commons.utils.PropertiesHelper;

/**
 * Configuration as commonly needed for interaction with Version Control Systems.
 * <p>
 * This class is used internally by {@link VCSRepositoryControl} and implementations.
 * </p>
 * <p>
 * The following configuration keys are picked up from {@link Properties}:
 * </p>
 * <ul>
 * <li>{@value CONFIG_KEY_SYSTEM} - identifies the Version Control System to be interacted with</li>
 * <li>{@value CONFIG_KEY_STORAGE} - path to a local copy of the repository (may get initialized if empty/non-existent)</li>
 * <li>{@value CONFIG_KEY_URL} - optional upstream URL to sync the local repository with</li>
 * <li>{@value CONFIG_KEY_BRANCH} - optional branch name to limit default interaction to</li>
 * <li>{@value CONFIG_KEY_AUTH_USERNAME} - optional username to authenticate with upstream server</li>
 * <li>{@value CONFIG_KEY_AUTH_PASSWORD} - optional password to authenticate with upstream server</li>
 * </ul>
 */
public class VCSRepositoryConfiguration {
    private final String system;
    private final String url;
    private final File storage;
    private final String branch;
    private final String username;
    private final char[] password;

    private static final String CONFIG_KEY_SYSTEM = "system";
    private static final String CONFIG_KEY_URL = "url";
    private static final String CONFIG_KEY_STORAGE = "storage";
    private static final String CONFIG_KEY_BRANCH = "branch";
    private static final String CONFIG_KEY_AUTH_USERNAME = "auth.username";
    private static final String CONFIG_KEY_AUTH_PASSWORD = "auth.password";

    VCSRepositoryConfiguration(Properties config, String keyPrefix) {
        system = getMandatory(config, keyPrefix + CONFIG_KEY_SYSTEM);

        url = PropertiesHelper.getNonEmpty(config, keyPrefix + CONFIG_KEY_URL)
                              .orElse(null);

        storage = new File(getMandatory(config, keyPrefix + CONFIG_KEY_STORAGE));

        branch = PropertiesHelper.getNonEmpty(config, keyPrefix + CONFIG_KEY_BRANCH)
                                 .orElse(null);

        username = PropertiesHelper.getNonEmpty(config, keyPrefix + CONFIG_KEY_AUTH_USERNAME)
                                   .orElse(null);

        password = PropertiesHelper.getNonEmpty(config, keyPrefix + CONFIG_KEY_AUTH_PASSWORD)
                                   .map(String::toCharArray)
                                   .orElse(null);
    }

    public String getSystem() {
        return system;
    }

    public File getStorage() {
        return storage;
    }

    public Optional<String> getUrl() {
        return Optional.ofNullable(url);
    }

    public Optional<String> getBranch() {
        return Optional.ofNullable(branch);
    }

    public Optional<String> getUsername() {
        return Optional.ofNullable(username);
    }

    public Optional<char[]> getPassword() {
        return Optional.ofNullable(password);
    }

    private String getMandatory(Properties config, String key) {
        return PropertiesHelper.getNonEmpty(config, key)
                               .orElseThrow(() -> new MissingConfiguration(key));
    }

    private static class MissingConfiguration extends IllegalArgumentException {
        MissingConfiguration(String key) {
            super("VCS configuration is missing mandatory definition for '" + key + "'");
        }
    }
}
