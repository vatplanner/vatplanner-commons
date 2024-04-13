package org.vatplanner.commons.vcs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Represents a revision in a Version Control System (VCS).
 */
public class Revision {
    private final String id;
    private final String[] parentIds;
    private final Instant timestamp;
    private final String message;

    private Revision(String id, String[] parentIds, Instant timestamp, String message) {
        this.id = id;
        this.parentIds = parentIds;
        this.timestamp = timestamp;
        this.message = message;
    }

    /**
     * Returns the revision's ID, specific to the underlying VCS.
     *
     * @return revision ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns all known parent revision IDs. Presence, meaning and number of parents is specific to the underlying VCS.
     *
     * @return all known parent revision IDs; may be empty
     */
    public List<String> getParentIds() {
        return (parentIds != null) ? Arrays.asList(parentIds) : Collections.emptyList();
    }

    /**
     * Returns the revision's timestamp. Availability and reliability of timestamps depends on the underlying VCS.
     * <p>
     * Some VCS may use server-side timestamps at time of repository uplink while others use user-local timestamps that
     * could be out of sync or even manipulated. Additionally, timestamps may not be consecutive, depending on how
     * the VCS and processing servers handle branches and uplinks.
     * </p>
     *
     * @return revision timestamp, if available; may not be accurate
     */
    public Optional<Instant> getTimestamp() {
        return Optional.ofNullable(timestamp);
    }

    /**
     * Returns the message attached to this revision.
     *
     * @return revision message, if available
     */
    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    /**
     * Creates a new {@link Builder} instance.
     *
     * @return new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Revision(");

        sb.append(id);

        if (timestamp != null) {
            sb.append(", timestamp=");
            sb.append(timestamp);
        }

        if (parentIds != null) {
            sb.append(", parents=[");
            sb.append(String.join(", ", parentIds));
            sb.append("]");
        }

        if (message != null) {
            sb.append(", message=\"");
            sb.append(message);
            sb.append("\"");
        }

        sb.append(")");

        return sb.toString();
    }

    public static class Builder {
        private String id;
        private List<String> parentIds = new ArrayList<>();
        private Instant timestamp;
        private String message;

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder setParentIds(Collection<String> parentIds) {
            this.parentIds = new ArrayList<>(parentIds);
            return this;
        }

        public Builder setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Revision build() {
            if (id == null) {
                throw new IllegalArgumentException("ID must be set");
            }

            return new Revision(
                id,
                parentIds.isEmpty() ? null : parentIds.toArray(new String[0]),
                timestamp,
                ((message == null) || message.isEmpty()) ? null : message
            );
        }
    }
}
