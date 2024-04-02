package org.vatplanner.commons.utils;

import java.util.regex.Pattern;

/**
 * Common helper methods to work with binary content.
 */
public class Bytes {
    private static final Pattern HEX_STRING_PATTERN = Pattern.compile("^[0-9a-f]*$");

    private Bytes() {
        // utility class; hide constructor
    }

    /**
     * Encodes the given bytes to hexadecimal string representation.
     *
     * @param bytes bytes to encode
     * @return hexadecimal representation of the given bytes
     */
    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();

        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    /**
     * Decodes the bytes from a given hexadecimal string representation.
     *
     * @param s hexadecimal string
     * @return bytes described by the string
     */
    public static byte[] parseHexString(String s) {
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("number of characters is not even");
        }

        if (!HEX_STRING_PATTERN.matcher(s).matches()) {
            throw new IllegalArgumentException("not a hex string");
        }

        byte[] out = new byte[s.length() / 2];

        int pos = 0;
        for (int i = 0; i < out.length; i++) {
            int start = pos;
            pos += 2;

            short value = Short.valueOf(s.substring(start, pos), 16);
            if (value <= 127) {
                out[i] = (byte) value;
            } else {
                out[i] = (byte) (value - 256);
            }
        }

        return out;
    }
}
