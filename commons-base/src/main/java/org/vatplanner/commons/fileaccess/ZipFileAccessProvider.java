package org.vatplanner.commons.fileaccess;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.vatplanner.commons.utils.Functions;
import org.vatplanner.commons.utils.IOStreams;
import org.vatplanner.commons.utils.Zip;

/**
 * A {@link FileAccessProvider} abstracting access to files within a ZIP archive.
 */
public class ZipFileAccessProvider implements FileAccessProvider.RandomAccess {
    // FIXME: this provider currently converts all paths to lower-case which is not described by API nor implemented for the local file system provider

    private final byte[] data;

    private static final Predicate<FileInfo> ACCEPT_ALL_FILES = x -> true;

    private enum VisitorContinuation {
        CONTINUE,
        BREAK;
    }

    private interface ZipEntryVisitor {
        VisitorContinuation visit(ZipInputStream zis, FileInfo info, ZipEntry entry) throws IOException;
    }

    private ZipFileAccessProvider(byte[] data) {
        this.data = data;
    }

    /**
     * Creates a new {@link ZipFileAccessProvider} sharing access to the given byte array.
     * The byte array must not be manipulated after this call.
     *
     * @param data raw bytes of the ZIP archive to access
     * @return provider backed by the given byte array
     */
    public static ZipFileAccessProvider sharing(byte[] data) {
        return new ZipFileAccessProvider(data);
    }

    /**
     * Creates a new {@link ZipFileAccessProvider} for the given file read from local storage. The raw (compressed) file
     * will be fully read into and kept in memory.
     *
     * @param file ZIP file to read into memory
     * @return provider for the given file at time of call
     * @throws IOException if the specified ZIP file cannot be read
     */
    public static ZipFileAccessProvider readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return new ZipFileAccessProvider(IOStreams.readAllBytes(fis));
        }
    }

    @Override
    public Stream<FileHolder> streamFiles() {
        return streamFiles(ACCEPT_ALL_FILES);
    }

    @Override
    public Stream<FileHolder> streamFiles(Predicate<FileInfo> filter) {
        Stream.Builder<FileHolder> streamBuilder = Stream.builder();
        forEach(filter, streamBuilder);
        return streamBuilder.build();
    }

    @Override
    public void forEach(Predicate<FileInfo> filter, Consumer<FileHolder> consumer) {
        forAllFiles(filter, (zis, info, entry) -> {
            consumer.accept(new FileHolder(info, Zip.readZipData(zis, entry)));
            return VisitorContinuation.CONTINUE;
        });
    }

    @Override
    public Set<FileInfo> listFiles() {
        return listFiles(ACCEPT_ALL_FILES);
    }

    @Override
    public Set<FileInfo> listFiles(Predicate<FileInfo> filter) {
        Set<FileInfo> out = new HashSet<>();

        forAllFiles(filter, (zis, info, entry) -> {
            out.add(info);
            return VisitorContinuation.CONTINUE;
        });

        return out;
    }

    @Override
    public Optional<FileHolder> getFile(AccessPath path) {
        // directories cannot be retrieved
        if (path.isDirectory()) {
            return Optional.empty();
        }

        AtomicReference<FileHolder> out = new AtomicReference<>();

        forAllFiles(matching(path), (zis, info, entry) -> {
            out.set(new FileHolder(info, Zip.readZipData(zis, entry)));
            return VisitorContinuation.BREAK;
        });

        return Optional.ofNullable(out.get());
    }

    private Predicate<FileInfo> matching(AccessPath path) {
        List<String> expectedPathSegments = path.getPathSegments();
        return info -> expectedPathSegments.equals(info.getPath().getPathSegments());
    }

    private void forAllFiles(Predicate<FileInfo> filter, ZipEntryVisitor visitor) {
        try (
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ZipInputStream zis = new ZipInputStream(bais);
        ) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // only files should be processed, no need to look into directories
                if (entry.isDirectory()) {
                    continue;
                }

                FileInfo info = describeFile(entry);
                if (!filter.test(info)) {
                    continue;
                }

                VisitorContinuation continuation = visitor.visit(zis, info, entry);
                if (continuation == VisitorContinuation.BREAK) {
                    break;
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new ReadFailed("Failed to read ZIP file", ex);
        }
    }

    private FileInfo describeFile(ZipEntry entry) {
        return FileInfo.builder()
                       .setPath(describePath(entry))
                       .setSize(entry.getSize())
                       .build();
    }

    private AccessPath describePath(ZipEntry entry) {
        List<String> pathSegments = splitPathSegments(entry.getName().toLowerCase());
        AccessPath.Type type = entry.isDirectory() ? AccessPath.Type.DIRECTORY : AccessPath.Type.FILE;
        return new AccessPath(pathSegments, type);
    }

    private List<String> splitPathSegments(String path) {
        return Arrays.stream(path.split("[/\\\\]"))
                     .filter(Functions.not(String::isEmpty))
                     .collect(Collectors.toList());
    }

    private static class ReadFailed extends RuntimeException {
        ReadFailed(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
