package org.vatplanner.commons.vcs;

/**
 * Interface to locate and instantiate adapters implementing {@link VCSRepositoryControl}. Must be registered through
 * SPI.
 */
public interface VCSRepositoryControlFactory {
    /**
     * Identifies the VCS system this factory creates implementations for.
     *
     * @return implemented VCS system
     */
    String getSupportedSystem();

    /**
     * Creates a new instance for given VCS configuration.
     *
     * @param config VCS configuration
     * @return instance handling the configured repository
     */
    VCSRepositoryControl createFromConfiguration(VCSRepositoryConfiguration config);
}
