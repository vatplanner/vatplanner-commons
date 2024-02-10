package org.vatplanner.commons.fileaccess;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple abstraction to access files from different sources such as a local file system or stored inside archives
 * through a unified interface suitable for data retrieval (reading).
 * <p>
 * Only files are accessible. Directories can only be used to filter or list available files.
 * </p>
 * <p>
 * The intention for writing a custom "virtual file system" like this is to avoid extra dependencies where only basic
 * read access is required in a limited number of use-cases but through different providers.
 * </p>
 * <p>
 * Implementing classes should provide {@link RandomAccess} if possible. "Convenience methods" provide {@code default}
 * implementations built around basic use-cases that will work but may be very inefficient to use. Providers should
 * override those with specific implementations if more efficient handling is possible. Callers are advised to use those
 * more specific methods instead for potentially better performance.
 * </p>
 */
public interface FileAccessProvider {
    /**
     * Provides access to all available files as a {@link Stream}.
     * <p>
     * All files will be read into memory. Consider using a filtering variation of this method instead unless the
     * majority of all files are actually needed.
     * </p>
     * <p>
     * Note that a {@link LinearStream} may only stream files once and from where it was last left off.
     * </p>
     *
     * @return {@link Stream} of all available files
     */
    Stream<FileHolder> streamFiles();

    /**
     * Provides access to all available files matching the given filter as a {@link Stream}.
     * <p>
     * Implementing providers may be able to filter more efficiently using this method instead of calling
     * {@link Stream#filter(Predicate)} on just {@link #streamFiles()} as this method may be able to skip over
     * unwanted files instead of reading them unnecessarily.
     * </p>
     * <p>
     * Note that a {@link LinearStream} may only stream files once and from where it was last left off.
     * </p>
     *
     * @param filter used to filter files; entries tested {@code true} will be included, {@code false} will exclude entries
     * @return {@link Stream} of all available files matching the filter
     */
    default Stream<FileHolder> streamFiles(Predicate<FileInfo> filter) {
        return streamFiles().filter(filter);
    }

    /**
     * Provides access to all available files matching the specified path.
     * <p>
     * Implementing providers may be able to filter more efficiently using this method instead of calling
     * {@link Stream#filter(Predicate)} on just {@link #streamFiles()} as this method may be able to skip over
     * unwanted files instead of reading them unnecessarily.
     * </p>
     * <p>
     * Note that a {@link LinearStream} may only stream files once and from where it was last left off.
     * </p>
     *
     * @param path      path to check; may describe an exact file or a directory
     * @param recursive if {@code true}, then files from subdirectories are also accepted; if {@code false}, then only an exact match/direct children are accepted
     * @return {@link Stream} of all available files matching the requested path
     */
    default Stream<FileHolder> streamFiles(AccessPath path, boolean recursive) {
        return streamFiles(entry -> (!path.isDirectory() && path.equals(entry.getPath())) || path.contains(entry.getPath(), recursive));
    }

    /**
     * Calls the given {@link Consumer} for each available file that matches the given {@link Predicate}.
     * <p>
     * This may be more direct and memory-efficient than using {@link #streamFiles(Predicate)}, especially for larger
     * repositories, because file contents can be garbage-collected after each file as compared to collecting all into
     * one large {@link Stream}, if supported by the {@link FileAccessProvider}.
     * </p>
     *
     * @param filter   used to filter files; entries tested {@code true} will be included, {@code false} will exclude entries
     * @param consumer receives each file just after it has been read
     */
    void forEach(Predicate<FileInfo> filter, Consumer<FileHolder> consumer); // TODO: declare as standard method, then wrap streamFiles here?

    /**
     * A {@link FileAccessProvider} abstraction that is able to access files directly in any order and at any time.
     */
    interface RandomAccess extends FileAccessProvider {
        /**
         * Returns a specific file.
         *
         * @param path path to the file
         * @return the file; empty if not found
         */
        Optional<FileHolder> getFile(AccessPath path);

        /**
         * Returns a specific file.
         *
         * @param info information about the file
         * @return the file; empty if not found
         */
        default Optional<FileHolder> getFile(FileInfo info) {
            return getFile(info.getPath());
        }

        /**
         * Returns information about all available files.
         *
         * @return information about all available files
         */
        Set<FileInfo> listFiles();

        /**
         * Returns information about all available files matching the given filter.
         *
         * @param filter used to filter files; entries tested {@code true} will be included, {@code false} will exclude entries
         * @return information about all available files matching the filter
         */
        default Set<FileInfo> listFiles(Predicate<FileInfo> filter) {
            return listFiles().stream()
                              .filter(filter)
                              .collect(Collectors.toSet());
        }

        /**
         * Returns information about all available files matching the specified path.
         *
         * @param path      path to check; may describe an exact file or a directory
         * @param recursive if {@code true}, then files from subdirectories are also accepted; if {@code false}, then only an exact match/direct children are accepted
         * @return information about all available files matching the requested path
         */
        default Set<FileInfo> listFiles(AccessPath path, boolean recursive) {
            return listFiles(entry -> (!path.isDirectory() && path.equals(entry.getPath())) || path.contains(entry.getPath(), recursive));
        }
    }

    /**
     * A {@link FileAccessProvider} abstraction that can access files only sequentially. Previous entries cannot be reached
     * unless this is a {@link ResettableLinearStream}.
     */
    interface LinearStream extends FileAccessProvider {
        /**
         * Returns the next file, if available.
         *
         * @return next file read from linear stream; empty if unavailable
         */
        Optional<FileHolder> getNextFile();
    }

    /**
     * A {@link FileAccessProvider} abstraction that can access files only sequentially but can be reset to start over from the
     * first entry.
     */
    interface ResettableLinearStream extends LinearStream {
        /**
         * Resets the linear file stream to the beginning (before the first file).
         */
        void resetFileStream();
    }
}
