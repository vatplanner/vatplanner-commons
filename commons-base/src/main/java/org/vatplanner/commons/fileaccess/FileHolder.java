package org.vatplanner.commons.fileaccess;

/**
 * Holds the contents of a file accessed through {@link FileAccessProvider}.
 */
public class FileHolder extends FileInfo {
    private final byte[] content;

    public FileHolder(FileInfo info, byte[] content) {
        super(withSize(info, content.length));
        this.content = content;
    }

    private static FileInfo withSize(FileInfo info, int size) {
        int originalSize = info.getSize().orElse(-1);
        if (originalSize > 0 && originalSize != size) {
            throw new IllegalArgumentException("Original size does not match actual content length " + size + ": " + info);
        }

        return extend(info)
            .setSize(size)
            .build();
    }

    public byte[] getContent() {
        return content;
    }
}
