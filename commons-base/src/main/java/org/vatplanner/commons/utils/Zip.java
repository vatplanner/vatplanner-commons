package org.vatplanner.commons.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common helper methods to work with ZIP files.
 */
public class Zip {
    private static final Logger LOGGER = LoggerFactory.getLogger(Zip.class);

    private static final int MAX_ALLOWED_SIZE = 100 * 1024 * 1024;

    /**
     * Verifies integrity of all contents within the given ZIP file.
     *
     * @param data ZIP file
     * @return {@code true} if integrity was confirmed for all entries within the ZIP file; {@code false} if the archive is corrupted
     */
    public static boolean verifyZipFile(byte[] data) {
        // whole file is verified, regardless of ignored files

        try (
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ZipInputStream zis = new ZipInputStream(bais);
        ) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Zip.readZipData(zis, entry);
            }
        } catch (IOException | IllegalArgumentException ex) {
            LOGGER.warn("Failed to read ZIP file", ex);
            return false;
        }

        return true;
    }

    /**
     * Reads the given entry from the {@link ZipInputStream}, checking data integrity and size.
     * <p>
     * The maximum allowed size for an entry is {@value #MAX_ALLOWED_SIZE} bytes.
     * </p>
     *
     * @param zis   stream to read from
     * @param entry entry to read
     * @return decompressed content of the entry
     * @throws IOException
     */
    public static byte[] readZipData(ZipInputStream zis, ZipEntry entry) throws IOException {
        long size = entry.getSize();
        if (size > MAX_ALLOWED_SIZE) {
            throw new IllegalArgumentException(
                "ZipEntry \"" + entry.getName() + "\" is too large, indicated "
                    + size + " bytes, at most " + MAX_ALLOWED_SIZE + " are allowed"
            );
        }

        byte[] data = IOStreams.readNBytes(zis, size);

        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        long actualCrc = crc.getValue();

        if (actualCrc != entry.getCrc()) {
            throw new IOException(
                "CRC mismatch for \"" + entry.getName()
                    + "\": calculated " + actualCrc
                    + ", expected " + entry.getCrc()
            );
        }

        return data;
    }
}
