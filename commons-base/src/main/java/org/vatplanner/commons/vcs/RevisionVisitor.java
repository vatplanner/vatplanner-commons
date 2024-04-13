package org.vatplanner.commons.vcs;

/**
 * A visitor for VCS {@link Revision}s.
 */
public interface RevisionVisitor {
    /**
     * Determines how the walker should proceed.
     */
    enum Action {
        /**
         * Instructs the walker to continue with the next revision.
         */
        CONTINUE_WALK,

        /**
         * Instructs the walker to abort.
         */
        ABORT_WALK;
    }

    /**
     * Called for all {@link Revision}s, one at a time.
     *
     * @param revision a VCS {@link Revision}
     * @return instruction to walker on how to proceed
     */
    Action visit(Revision revision);
}
