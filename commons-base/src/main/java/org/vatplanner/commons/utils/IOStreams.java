package org.vatplanner.commons.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Common helper methods for working with {@link InputStream}s and {@link OutputStream}s.
 */
public class IOStreams {
    private static final int BUFFER_SIZE = 4096;

    private IOStreams() {
        // utility class; hide constructor
    }

    /**
     * Reads all content from the given {@link InputStream} until end of file is encountered. The content is not used.
     *
     * @param is stream to read
     * @throws IOException
     */
    public static void consume(InputStream is) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;

        do {
            read = is.read(buffer);
        } while (read > 0);

        if (read != -1) {
            throw new IOException("Unexpected number of bytes read indicated, should be -1 at EOF but got " + read);
        }
    }

    /**
     * Reads and returns all content from the given {@link InputStream} until end of file is encountered.
     *
     * @param is stream to read
     * @return read content
     * @throws IOException
     */
    public static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = is.read(buffer)) > 0) {
            baos.write(buffer, 0, read);
        }
        if (read != -1) {
            throw new IOException("Unexpected number of bytes read indicated, should be -1 at EOF but got " + read);
        }
        baos.flush();

        return baos.toByteArray();
    }

    /**
     * Reads exactly the given number of bytes from the given {@link InputStream}.
     *
     * @param is        stream to read
     * @param remaining number of bytes to read
     * @return read content
     * @throws IOException
     */
    public static byte[] readNBytes(InputStream is, long remaining) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read = -1;
        while (remaining > 0 && (read = is.read(buffer)) > 0) {
            baos.write(buffer, 0, read);
            remaining -= read;
        }
        baos.flush();
        if (remaining > 0) {
            throw new IOException("Incomplete: " + remaining + " bytes missing");
        }
        if (read < -1 || read == 0) {
            throw new IOException("Unexpected return value for read: " + read);
        }
        return baos.toByteArray();
    }
}
