package org.vatplanner.commons.fileaccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Describes the path to a file or directory for use with {@link FileAccessProvider}.
 */
public class AccessPath {
    private final List<String> pathSegments;
    private final String name;
    private final Type type;

    /**
     * Describes what type of entry the path points to.
     */
    public enum Type {
        /**
         * The path may be a file or directory; the type is not known at this time.
         */
        UNKNOWN,

        /**
         * The path ends with a file.
         */
        FILE,

        /**
         * The path ends with a directory.
         */
        DIRECTORY;
    }

    public AccessPath(List<String> pathSegments, Type type) {
        if (pathSegments.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException(
                "Path must not contain any empty segments: "
                    + pathSegments.stream()
                                  .collect(Collectors.joining("\", \"", "\"", "\""))
            );
        }

        if ((type == Type.FILE) && pathSegments.isEmpty()) {
            throw new IllegalArgumentException("An empty path can only describe a directory, not a file");
        }

        this.pathSegments = new ArrayList<>(pathSegments);
        this.name = pathSegments.isEmpty() ? null : pathSegments.get(pathSegments.size() - 1);
        this.type = type;
    }

    /**
     * Returns all segments of the path. The last segment is either a file or directory (see {@link #getType()} or
     * {@link #isDirectory()}), all other segments describe directories.
     *
     * @return all path segments from outermost to innermost entry; may be empty
     */
    public List<String> getPathSegments() {
        return Collections.unmodifiableList(pathSegments);
    }

    /**
     * Returns the name of the innermost (deepest nested) path segment.
     *
     * @return name of innermost path segment; empty if the path is empty
     */
    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the type of entry the full path refers to.
     *
     * @return type of entry at full path
     */
    public Type getType() {
        return type;
    }

    /**
     * Checks if the path is a directory.
     *
     * @return {@code true} if the path describes a directory, {@code false} if it describes a file or the type is unknown
     */
    public boolean isDirectory() {
        return type == Type.DIRECTORY;
    }

    /**
     * Checks if the given {@link AccessPath} is contained within the path described by this instance.
     *
     * @param other     {@link AccessPath} to check if it is contained by this path
     * @param recursive only direct children are accepted if {@code false}; any nested level is accepted if {@code true}
     * @return {@code true} if the given {@link AccessPath} is contained by the path described by this instance; {@code false} if not
     */
    public boolean contains(AccessPath other, boolean recursive) {
        // files do not contain anything else
        if (this.type == Type.FILE) {
            return false;
        }

        // no need to check further if other path is too short (possible parent, sibling or no match at all)
        if (other.pathSegments.size() <= this.pathSegments.size()) {
            return false;
        }

        boolean thisIsParent = this.pathSegments.equals(other.pathSegments.subList(0, this.pathSegments.size()));

        // any parent level is sufficient for recursive checks
        if (recursive) {
            return thisIsParent;
        }

        // non-recursive check requires exact child
        return thisIsParent && (this.pathSegments.size() == (other.pathSegments.size() - 1));
    }

    /**
     * Creates a new {@link AccessPath} describing a file location.
     *
     * @param pathSegments path to file; must not be empty
     * @return path describing file location
     */
    public static AccessPath forFile(String... pathSegments) {
        if (pathSegments.length < 1) {
            throw new IllegalArgumentException("no path segments specified");
        }

        return new AccessPath(Arrays.asList(pathSegments), Type.FILE);
    }

    /**
     * Creates a new {@link AccessPath} describing a file location underneath base path.
     *
     * @param basePath     directory path to start from
     * @param pathSegments path to file as continued underneath the base path; must not be empty
     * @return path describing file location
     */
    public static AccessPath forFile(AccessPath basePath, String... pathSegments) {
        if (pathSegments.length < 1) {
            throw new IllegalArgumentException("no path segments specified");
        }

        if (basePath.getType() == Type.FILE) {
            throw new IllegalArgumentException("base path is a file: " + basePath);
        }

        ArrayList<String> allPathSegments = new ArrayList<>();
        allPathSegments.addAll(basePath.getPathSegments());
        allPathSegments.addAll(Arrays.asList(pathSegments));

        return new AccessPath(allPathSegments, Type.FILE);
    }

    /**
     * Creates a new {@link AccessPath} describing a directory location.
     *
     * @param pathSegments path to directory
     * @return path describing directory location
     */
    public static AccessPath forDirectory(String... pathSegments) {
        return new AccessPath(Arrays.asList(pathSegments), Type.DIRECTORY);
    }

    @Override
    public String toString() {
        return "AccessPath(type=" + type.name()
            + ", pathSegments=[" + pathSegments.stream()
                                               .collect(Collectors.joining("\", \"", "\"", "\""))
            + "])";
    }
}
