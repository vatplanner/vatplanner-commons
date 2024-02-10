package org.vatplanner.commons.fileaccess;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.vatplanner.commons.utils.IOStreams;

/**
 * A {@link FileAccessProvider} abstracting access to a local files reachable underneath a base directory.
 * <p>
 * Symbolic links etc. can be followed which also means that files outside the base directory may become accessible
 * that way. Free traversal through {@link #getFile(AccessPath)} is attempted top be mitigated however; every file to
 * be accessed needs to be reachable from the specified base directory being traversed down. Issues caused by bad naming
 * of actually present files/directories (e.g. if {@code ./..} should be an actual directory name) may still occur.
 * </p>
 */
public class LocalFileAccessProvider implements FileAccessProvider.RandomAccess {
    private final File basePath;

    private static final Predicate<FileInfo> ALL_FILES = x -> true;

    public LocalFileAccessProvider(File basePath) {
        this.basePath = basePath;
    }

    @Override
    public Stream<FileHolder> streamFiles() {
        return streamFiles(ALL_FILES);
    }

    @Override
    public Stream<FileHolder> streamFiles(Predicate<FileInfo> filter) {
        Stream.Builder<FileHolder> streamBuilder = Stream.builder();
        forEach(filter, streamBuilder);
        return streamBuilder.build();
    }

    @Override
    public void forEach(Predicate<FileInfo> filter, Consumer<FileHolder> consumer) {
        forAllFiles(
            basePath,
            filter,
            (fileInfo, file) -> consumer.accept(readFile(fileInfo, file))
        );
    }

    @Override
    public Optional<FileHolder> getFile(AccessPath path) {
        if (path.isDirectory() || path.getPathSegments().isEmpty()) {
            return Optional.empty();
        }

        // Path segments might contain (accidentally or by malicious intent) characters that conflict with file-system
        // control sequences (e.g. traversal through ".." or a concatenated path "dir/file.txt"). We try to protect
        // against such issues by checking if each path segment can actually be found with an exact name match in each
        // directory reachable by traversal from the original base path. This is not 100% safe in case there are
        // problematic sequences in the actually found path segments as we still depend on what Java does internally but
        // it should help against most unwanted behaviour via just invalid/malicious input as long as the actual
        // directory and file names are clear of issues.
        File file = basePath;
        for (String pathSegment : path.getPathSegments()) {
            File[] items = file.listFiles();
            if (items == null) {
                throw new ReadFailed("Unable to list local directory " + file);
            }

            file = Arrays.stream(items)
                         .filter(x -> pathSegment.equals(x.getName()))
                         .findFirst()
                         .orElse(null);

            if (file == null || !file.canRead()) {
                return Optional.empty();
            }
        }

        if (!file.isFile()) {
            return Optional.empty();
        }

        return Optional.of(readFile(createFileInfo(path.getPathSegments(), file), file));
    }

    @Override
    public Set<FileInfo> listFiles() {
        return listFiles(ALL_FILES);
    }

    @Override
    public Set<FileInfo> listFiles(Predicate<FileInfo> filter) {
        if (!basePath.isDirectory() || !basePath.canRead()) {
            return Collections.emptySet();
        }

        Set<FileInfo> out = new HashSet<>();

        forAllFiles(
            basePath,
            filter,
            Collections.emptyList(),
            (fileInfo, file) -> out.add(fileInfo)
        );

        return out;
    }

    private void forAllFiles(File parent, Predicate<FileInfo> filter, BiConsumer<FileInfo, File> consumer) {
        forAllFiles(parent, filter, Collections.emptyList(), consumer);
    }

    private void forAllFiles(File parent, Predicate<FileInfo> filter, List<String> parentPathSegments, BiConsumer<FileInfo, File> consumer) {
        File[] files = parent.listFiles();
        if (files == null) {
            throw new ReadFailed("Unable to list local directory " + parent);
        }

        for (File file : files) {
            if (!file.canRead()) {
                continue;
            }

            ArrayList<String> filePathSegments = new ArrayList<>(parentPathSegments);
            filePathSegments.add(file.getName());

            if (file.isDirectory()) {
                forAllFiles(file, filter, filePathSegments, consumer);
            } else if (file.isFile()) {
                FileInfo fileInfo = createFileInfo(filePathSegments, file);
                if (filter.test(fileInfo)) {
                    consumer.accept(fileInfo, file);
                }
            }
        }
    }

    private FileInfo createFileInfo(List<String> pathSegments, File file) {
        return FileInfo.builder()
                       .setPath(new AccessPath(pathSegments, AccessPath.Type.FILE))
                       .setSize(file.length())
                       .build();
    }

    private FileHolder readFile(FileInfo fileInfo, File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return new FileHolder(fileInfo, IOStreams.readAllBytes(fis));
        } catch (IOException ex) {
            throw new ReadFailed("Failed to read " + fileInfo + " from local file " + file, ex);
        }
    }

    private static class ReadFailed extends RuntimeException {
        ReadFailed(String msg) {
            super(msg);
        }

        ReadFailed(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
