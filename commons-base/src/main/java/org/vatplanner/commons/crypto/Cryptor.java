package org.vatplanner.commons.crypto;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Implementations handle cryptography for use in limited context of the application (VATPlanner) this was originally
 * written for.
 * <p>
 * Note that all operations are specific to the needs and intended usage of the original application. In particular, if
 * a large number of uncontrolled keys is being used, verification may yield undesired results due to key ID collision.
 * </p>
 * <p>
 * <strong>Evaluate carefully before using in other projects/contexts and heed the warnings given in the readme file.
 * </strong> Using this class for anything other than the designed purpose may actually break an application's security
 * instead of improving it.
 * </p>
 */
public interface Cryptor {

    /**
     * Provides an OpenPGP signature in both ASCII armored and hex/byte format.
     */
    class Signature {
        private final String asciiArmored;
        private final byte[] bytes;

        protected Signature(String asciiArmored, byte[] bytes) {
            this.asciiArmored = asciiArmored;
            this.bytes = bytes;
        }

        /**
         * Creates a new holder for the given ASCII armored {@link Signature}.
         *
         * @param asciiArmored ASCII armored signature
         * @return holder object for use with other methods
         */
        public static Signature asciiArmored(String asciiArmored) {
            return new Signature(asciiArmored, null);
        }

        /**
         * Returns the ASCII armored representation of this signature.
         *
         * @return ASCII armored representation
         */
        public String getAsciiArmored() {
            return asciiArmored;
        }

        /**
         * Returns the binary representation of this signature.
         *
         * @return binary representation
         */
        public Optional<byte[]> getBytes() {
            if (bytes == null) {
                return Optional.empty();
            }
            return Optional.of(Arrays.copyOf(bytes, bytes.length));
        }
    }

    /**
     * Decrypts the given content.
     *
     * @param encrypted content to decrypt
     * @return decrypted content
     */
    byte[] decrypt(byte[] encrypted);

    /**
     * Decrypts the given stream.
     *
     * @param is stream to decrypt
     * @return decrypted content
     */
    byte[] decrypt(InputStream is);

    /**
     * Returns all key IDs related to the recipients listed by the given encrypted content, as currently known from this
     * instance's configuration.
     * <p>
     * PGP uses master and sub-keys, each of which could be used for communication. When exchanging information, for
     * example when trying to figure out if a signature matches to any recipient of a previously encrypted file, the
     * master key IDs must also be taken into account although the signature only indicates a sub-key.
     * </p>
     *
     * @param encrypted encrypted content
     * @return all known master and sub-keys IDs related to the recipients of the encrypted content
     */
    Set<Long> getRelatedRecipientKeyIds(byte[] encrypted);

    /**
     * Encrypts the given content in ASCII armored format to all public keys currently loaded.
     *
     * @param unencrypted content to encrypt
     * @return encrypted content in ASCII armored format
     */
    byte[] encryptArmored(byte[] unencrypted);

    /**
     * Encrypts the given content to unarmored binary format to all public keys currently loaded.
     * Besides not using an ASCII-compatible encoding, unarmored files also do not contain CRC checksums.
     *
     * @param unencrypted content to encrypt
     * @return encrypted content in unarmored binary format
     */
    byte[] encryptUnarmored(byte[] unencrypted);

    /**
     * Signs the given content, returning the detached {@link Signature}.
     *
     * @param data content to sign
     * @return detached signature
     */
    Signature sign(byte[] data);

    /**
     * Verifies if the given data and detached {@link Signature} match and returns all key IDs the signature was valid
     * for and their related key IDs. In case the signature does not match, the result will be empty.
     * <p>
     * Note that using the "expanded" key IDs for identification is potentially unsafe outside the limited context of
     * this application as there is a risk of key ID collisions if a larger number of keys is used, particularly if
     * the keys are not manually maintained.
     * </p>
     *
     * @param signedDataWithoutSignature content to check signature for
     * @param signature                  detached signature
     * @return key IDs that relate to those that signed the content; empty if signature did not match
     */
    Set<Long> verify(byte[] signedDataWithoutSignature, Signature signature);
}

