package org.vatplanner.commons.vcs;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vatplanner.commons.fileaccess.FileAccessProvider;

/**
 * Provides common functions to manage Version Control Systems through an abstracted API.
 * Access to files within a repository is not provided through this class; use {@link FileAccessProvider}s instead.
 */
public abstract class VCSRepositoryControl implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VCSRepositoryControl.class);

    /**
     * Creates a new control instance handling the configured repository.
     *
     * @param config configuration as documented in {@link VCSRepositoryConfiguration}
     * @return an instance to control the specified repository; must be closed when finished
     */
    public static VCSRepositoryControl forConfiguration(Properties config) {
        return forConfiguration(prepareConfiguration(config));
    }

    /**
     * Creates a new control instance handling the configured repository.
     *
     * @param config          configuration as documented in {@link VCSRepositoryConfiguration}
     * @param configKeyPrefix an extra prefix that will be added in front of all configuration keys
     * @return an instance to control the specified repository; must be closed when finished
     */
    public static VCSRepositoryControl forConfiguration(Properties config, String configKeyPrefix) {
        return forConfiguration(prepareConfiguration(config, configKeyPrefix));
    }

    /**
     * Creates a new control instance handling the configured repository.
     *
     * @param config a previously prepared configuration
     * @return an instance to control the specified repository; must be closed when finished
     */
    public static VCSRepositoryControl forConfiguration(VCSRepositoryConfiguration config) {
        return findFactory(config.getSystem()).createFromConfiguration(config);
    }

    /**
     * Prepares a {@link VCSRepositoryConfiguration} from given {@link Properties} for later use.
     * <p>
     * The returned configuration is not fully validated, using it may fail.
     * </p>
     *
     * @param config configuration as documented in {@link VCSRepositoryConfiguration}
     * @return repository configuration for later use, not fully validated
     */
    public static VCSRepositoryConfiguration prepareConfiguration(Properties config) {
        return prepareConfiguration(config, "");
    }

    /**
     * Prepares a {@link VCSRepositoryConfiguration} from given {@link Properties} for later use.
     * <p>
     * The returned configuration is not fully validated, using it may fail.
     * </p>
     *
     * @param config          configuration as documented in {@link VCSRepositoryConfiguration}
     * @param configKeyPrefix an extra prefix that will be added in front of all configuration keys
     * @return repository configuration for later use, not fully validated
     */
    public static VCSRepositoryConfiguration prepareConfiguration(Properties config, String configKeyPrefix) {
        return new VCSRepositoryConfiguration(config, configKeyPrefix);
    }

    private static VCSRepositoryControlFactory findFactory(String wantedSystem) {
        Collection<VCSRepositoryControlFactory> matched = new ArrayList<>();
        for (VCSRepositoryControlFactory factory : ServiceLoader.load(VCSRepositoryControlFactory.class)) {
            if (wantedSystem.equals(factory.getSupportedSystem())) {
                matched.add(factory);
            }
        }

        if (matched.isEmpty()) {
            throw new IllegalArgumentException("No implementation available for system '" + wantedSystem + "', check classloader");
        }

        VCSRepositoryControlFactory out = matched.iterator().next();
        if (matched.size() > 1) {
            LOGGER.warn(
                "Multiple implementations present for system '{}', using {} out of available {}",
                wantedSystem, out.getClass().getCanonicalName(),
                matched.stream()
                       .map(Object::getClass)
                       .map(Class::getCanonicalName)
                       .collect(Collectors.joining(", "))
            );
        }

        return out;
    }

    /**
     * Downloads revisions from the configured upstream repository, if supported.
     * <p>
     * No action will be performed if an upstream location has not been configured or the underlying VCS does not
     * support synchronization.
     * </p>
     * <p>
     * Synchronization will take a while; this method blocks until complete. A timeout must be specified to eventually
     * unblock any stuck operations.
     * </p>
     *
     * @param timeout timeout for data transfer to finish
     */
    public abstract void syncDown(Duration timeout);

    /**
     * Walks the given {@link RevisionVisitor} through the repository's revision log.
     * Parameters from {@link VCSRepositoryConfiguration} may restrict the branch being processed.
     *
     * @param visitor will be called for all revisions as configured
     */
    public abstract void walkLog(RevisionVisitor visitor);

    /**
     * Thrown if a configured parameter could not be accepted.
     */
    public static class ConfigurationFailed extends RuntimeException {
        public ConfigurationFailed(String msg) {
            super(msg);
        }

        public ConfigurationFailed(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /**
     * Thrown if the requested operation has failed.
     */
    public static class OperationFailed extends RuntimeException {
        public OperationFailed(String msg) {
            super(msg);
        }

        public OperationFailed(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
