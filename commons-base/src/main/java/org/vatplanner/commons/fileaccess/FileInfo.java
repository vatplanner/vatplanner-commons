package org.vatplanner.commons.fileaccess;

import java.util.OptionalInt;

/**
 * Provides information about a file accessible through a {@link FileAccessProvider}.
 */
public class FileInfo {
    private final AccessPath path;
    private final Integer size;

    /**
     * Copies information from another {@link FileInfo} instance. Intended to be called by derived classes.
     *
     * @param original {@link FileInfo} to copy from
     */
    protected FileInfo(FileInfo original) {
        this.path = original.path;
        this.size = original.size;
    }

    private FileInfo(AccessPath path, Integer size) {
        if (path == null) {
            throw new IllegalArgumentException("path is mandatory");
        } else if (path.getType() != AccessPath.Type.FILE) {
            throw new IllegalArgumentException("path must describe a file but type is " + path.getType());
        }

        if ((size != null) && (size < 0)) {
            throw new IllegalArgumentException("file size must be positive or undefined but is " + size);
        }

        this.path = path;
        this.size = size;
    }

    /**
     * Returns the {@link AccessPath} to the described file.
     *
     * @return {@link AccessPath} to the described file
     */
    public AccessPath getPath() {
        return path;
    }

    /**
     * Returns the size of the described file.
     *
     * @return file size if known; empty if unknown
     */
    public OptionalInt getSize() {
        return (size != null) ? OptionalInt.of(size) : OptionalInt.empty();
    }

    /**
     * Creates a new {@link Builder} to instantiate {@link FileInfo} objects.
     *
     * @return new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@link Builder} to extend from the {@link FileInfo}.
     *
     * @return new {@link Builder} instance
     */
    public static Builder extend(FileInfo original) {
        return new Builder(original);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + path + ", size=" + size + ")";
    }

    /**
     * Builder for constructing {@link FileInfo} instances.
     */
    public static class Builder {
        private AccessPath path;
        private Integer size;

        private Builder() {
            // default constructor without initial data
        }

        private Builder(FileInfo original) {
            this.path = original.path;
            this.size = original.size;
        }

        /**
         * Sets the {@link AccessPath} to the file being described.
         *
         * @param path {@link AccessPath} to the file being described
         * @return same instance for method-chaining
         */
        public Builder setPath(AccessPath path) {
            this.path = path;
            return this;
        }

        /**
         * Sets the size of the file being described. File size is optional and, if set, must be consistent to the data
         * being retrieved as a {@link FileHolder}. Only set if known.
         *
         * @param size file size in bytes
         * @return same instance for method-chaining
         */
        public Builder setSize(int size) {
            if (size < 0) {
                throw new IllegalArgumentException("Size must not be negative: " + size);
            }

            this.size = size;

            return this;
        }

        /**
         * Sets the size of the file being described. File size is optional and, if set, must be consistent to the data
         * being retrieved as a {@link FileHolder}. Only set if known.
         *
         * @param size file size in bytes
         * @return same instance for method-chaining
         */
        public Builder setSize(long size) {
            if (size < 0) {
                throw new IllegalArgumentException("Size must not be negative: " + size);
            }

            if (size > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Size exceeds integer range: " + size);
            }

            this.size = (int) size;

            return this;
        }

        /**
         * Constructs a {@link FileInfo} instance from given information.
         *
         * @return {@link FileInfo} instance from given information
         */
        public FileInfo build() {
            return new FileInfo(path, size);
        }
    }
}
