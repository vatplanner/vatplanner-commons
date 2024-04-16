package org.vatplanner.commons.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyRing;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.util.io.Streams;
import org.pgpainless.PGPainless;
import org.pgpainless.decryption_verification.ConsumerOptions;
import org.pgpainless.decryption_verification.DecryptionStream;
import org.pgpainless.decryption_verification.MessageInspector;
import org.pgpainless.decryption_verification.SignatureVerification;
import org.pgpainless.encryption_signing.EncryptionOptions;
import org.pgpainless.encryption_signing.EncryptionStream;
import org.pgpainless.encryption_signing.ProducerOptions;
import org.pgpainless.encryption_signing.SigningOptions;
import org.pgpainless.key.SubkeyIdentifier;
import org.pgpainless.key.parsing.KeyRingReader;
import org.pgpainless.key.protection.SecretKeyRingProtector;
import org.pgpainless.util.ArmoredInputStreamFactory;
import org.pgpainless.util.MultiMap;
import org.pgpainless.util.Passphrase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vatplanner.commons.utils.Bytes;
import org.vatplanner.commons.utils.IOStreams;

/**
 * Handles cryptography for use in limited context of the application (VATPlanner) this was originally written for.
 * This implementation is a wrapper around PGPainless which in turn uses BouncyCastle.
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
public class PGCryptor implements Cryptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PGCryptor.class);

    private static final String PUBLIC_DIRECTORY = "public";
    private static final FilenameFilter FILTER_PUBLIC_KEYS = (dir, name) -> name.toLowerCase().endsWith(".pub") || name.toLowerCase().endsWith(".pub.asc");

    private static final String SECRET_DIRECTORY = "secret";
    private static final FilenameFilter FILTER_SECRET_KEYS = (dir, name) -> name.toLowerCase().endsWith(".sec") || name.toLowerCase().endsWith(".sec.asc");

    private static final AtomicReference<KeySet> activeKeySet = new AtomicReference<>();

    private static final String CONFIG_KEY_PASSPHRASE_FILE = "keys.passphraseFile";
    private static final String CONFIG_KEY_BASE_DIRECTORY = "keys.baseDir";
    private static final String CONFIG_KEY_SIGNING_KEY = "keys.signingKey";

    /**
     * Size of random bytes to generate for key set self-test.
     */
    private static final int KEY_TEST_NUM_BYTES = 128 * 1024;

    private final Random random = new Random();

    /**
     * Bundles all loaded keys and related information for consistent use.
     */
    private static class KeySet {
        final PGPPublicKeyRingCollection publicKeyRings;
        final PGPSecretKeyRingCollection secretKeyRings;
        final PGPSecretKeyRing signingKey;
        final SecretKeyRingProtector secretKeyRingProtector;
        final Map<Long, Long> masterKeyIdsBySubKeyId;

        KeySet(HierarchicalPublicKeyRings hierarchicalPublicKeyRings, PGPSecretKeyRingCollection secretKeyRings, PGPSecretKeyRing signingKey, SecretKeyRingProtector secretKeyRingProtector) {
            this.publicKeyRings = hierarchicalPublicKeyRings.ringCollection;
            this.secretKeyRings = secretKeyRings;
            this.signingKey = signingKey;
            this.secretKeyRingProtector = secretKeyRingProtector;
            this.masterKeyIdsBySubKeyId = hierarchicalPublicKeyRings.masterKeyIdsBySubKeyId;
        }
    }

    /**
     * Holds public keys and their relations from sub-key ID to master key ID.
     */
    private static class HierarchicalPublicKeyRings {
        final Map<Long, Long> masterKeyIdsBySubKeyId = new HashMap<>();
        PGPPublicKeyRingCollection ringCollection = new PGPPublicKeyRingCollection(Collections.emptyList());
    }

    private interface ThrowingSupplier<T, EX extends Throwable> {
        T get() throws EX;
    }

    /**
     * Provides an OpenPGP signature in both ASCII armored and hex/byte format.
     */
    public static class PGSignature extends Signature {
        private PGSignature(String asciiArmored, byte[] bytes) {
            super(asciiArmored, bytes);
        }

        private PGSignature(PGPSignature pgpSignature) {
            this(
                wrapIOException(() -> PGPainless.asciiArmor(pgpSignature)),
                wrapIOException(pgpSignature::getEncoded)
            );
        }

        private static <T> T wrapIOException(ThrowingSupplier<T, IOException> supplier) {
            try {
                return supplier.get();
            } catch (IOException ex) {
                throw new CryptoFailure("Signature could not be encoded", ex);
            }
        }
    }

    /**
     * Creates a new instance which expects that only a single secret key exists and uses that as the signing key.
     *
     * @param baseDirectory          base directory to read keys from (both public and secret)
     * @param secretKeyRingProtector a method to access (unprotect) all secret keys
     */
    public PGCryptor(File baseDirectory, SecretKeyRingProtector secretKeyRingProtector) {
        reconfigure(baseDirectory, secretKeyRingProtector);
    }

    /**
     * Creates a new instance using the specified key for signing.
     *
     * @param baseDirectory          base directory to read general keys from (both public and secret)
     * @param signingKeyFile         secret key to be used for signing
     * @param secretKeyRingProtector a method to access (unprotect) all secret keys
     */
    public PGCryptor(File baseDirectory, File signingKeyFile, SecretKeyRingProtector secretKeyRingProtector) {
        reconfigure(baseDirectory, signingKeyFile, secretKeyRingProtector);
    }

    /**
     * Creates a new instance from given textual configuration.
     * <p>
     * Supported options:
     * </p>
     * <ul>
     * <li>{@value CONFIG_KEY_BASE_DIRECTORY} must point to the base directory as required by the constructor</li>
     * <li>{@value CONFIG_KEY_PASSPHRASE_FILE} should point to a file holding the passphrase to unlock private keys; leave undefined if keys are unlocked</li>
     * <li>{@value CONFIG_KEY_SIGNING_KEY} can optionally points to a specific file holding the key to be used to sign</li>
     * </ul>
     *
     * @param config configuration as described above
     * @return configured instance
     */
    public static PGCryptor fromConfig(Properties config) {
        SecretKeyRingProtector secretKeyRingProtector = SecretKeyRingProtector.unprotectedKeys();
        String passphraseFilePath = config.getProperty(CONFIG_KEY_PASSPHRASE_FILE);
        if (passphraseFilePath != null) {
            try {
                SecretKeyRingProtector.unlockAnyKeyWith(Passphrase.fromPassword(Files.readAllLines(new File(passphraseFilePath).toPath()).iterator().next()));
            } catch (IOException ex) {
                throw new CryptoFailure("Failed to read passphrase from " + passphraseFilePath, ex);
            }
        }

        File keyBaseDir = new File(config.getProperty(CONFIG_KEY_BASE_DIRECTORY));
        String signingKeyFilePath = config.getProperty(CONFIG_KEY_SIGNING_KEY);

        if (signingKeyFilePath == null) {
            return new PGCryptor(keyBaseDir, secretKeyRingProtector);
        } else {
            return new PGCryptor(keyBaseDir, new File(signingKeyFilePath), secretKeyRingProtector);
        }
    }

    /**
     * Reconfigures this instance. It is expected that only a single secret key exists which will be used as the signing key.
     * <p>
     * If the found keys are not usable, {@link CryptoFailure} will be thrown and the previous configuration remains in effect.
     * </p>
     *
     * @param baseDirectory          base directory to read keys from (both public and secret)
     * @param secretKeyRingProtector a method to access (unprotect) all secret keys
     */
    public void reconfigure(File baseDirectory, SecretKeyRingProtector secretKeyRingProtector) {
        // TODO: watch directories for changes and/or retrigger from outside

        File secretDirectory = new File(baseDirectory, SECRET_DIRECTORY);

        File[] secretKeyFiles = secretDirectory.listFiles(FILTER_SECRET_KEYS);
        if (secretKeyFiles == null) {
            throw new CryptoFailure("Directory cannot be listed: " + secretDirectory);
        }

        if (secretKeyFiles.length != 1) {
            throw new CryptoFailure("Signing key has not been specified and cannot be determined; expected exactly a single file but found " + secretKeyFiles.length);
        }

        reconfigure(baseDirectory, secretKeyFiles[0], secretKeyRingProtector);
    }

    /**
     * Reconfigures this instance. The specified key will be used for signing.
     * <p>
     * If the found keys are not usable, {@link CryptoFailure} will be thrown and the previous configuration remains in effect.
     * </p>
     *
     * @param baseDirectory          base directory to read general keys from (both public and secret)
     * @param signingKeyFile         secret key to be used for signing
     * @param secretKeyRingProtector a method to access (unprotect) all secret keys
     */
    public void reconfigure(File baseDirectory, File signingKeyFile, SecretKeyRingProtector secretKeyRingProtector) {
        // TODO: watch directories for changes and/or retrigger from outside

        LOGGER.info("Reconfiguring to {}, signing key: {}", baseDirectory, signingKeyFile);

        KeySet newKeySet = new KeySet(
            readPublicKeyRings(new File(baseDirectory, PUBLIC_DIRECTORY)),
            readSecretKeyRings(new File(baseDirectory, SECRET_DIRECTORY)),
            readSingleSecretKeyRing(signingKeyFile, "signing"),
            secretKeyRingProtector
        );

        // TODO: fail if signing key has expired
        // TODO: warn if signing key is "close" to expiration (-6 months?)

        test(newKeySet);

        activeKeySet.set(newKeySet);
    }

    private void test(KeySet keys) {
        LOGGER.debug("Running self-test for new keys");

        try {
            byte[] unencrypted = new byte[KEY_TEST_NUM_BYTES];
            random.nextBytes(unencrypted);

            Cryptor.Signature signature = sign(unencrypted, keys);
            if (verify(unencrypted, signature, keys).isEmpty()) {
                throw new CryptoFailure("Signature failed to verify");
            }

            // encrypt already decrypts for verification
            encrypt(unencrypted, false, keys);
            encrypt(unencrypted, true, keys);
        } catch (CryptoFailure ex) {
            throw new CryptoFailure("Self-test for new key set failed", ex);
        }

        LOGGER.debug("Self-test finished successfully");
    }

    private PGPSecretKeyRing readSingleSecretKeyRing(File file, String type) {
        // TODO: check for key to be decodeable
        return readSingleKeyRing(file, type, KeyRingReader::secretKeyRing);
    }

    private PGPPublicKeyRing readSinglePublicKeyRing(File file) {
        return readSingleKeyRing(file, "public", KeyRingReader::publicKeyRing);
    }

    @FunctionalInterface
    private interface KeyRingByteLoader<T extends PGPKeyRing> {
        T apply(KeyRingReader reader, byte[] bytes) throws IOException;
    }

    private <T extends PGPKeyRing> T readSingleKeyRing(File file, String type, KeyRingByteLoader<T> method) {
        LOGGER.debug("loading {} key from {}", type, file);

        try {
            T ring = method.apply(PGPainless.readKeyRing(), Files.readAllBytes(file.toPath()));

            if (ring == null) {
                throw new CryptoFailure("Failed to read " + type + " key from " + file);
            }

            int numMasterKeys = 0;
            Iterator<PGPPublicKey> it = ring.getPublicKeys();
            while (it.hasNext()) {
                PGPPublicKey key = it.next();
                if (key.isMasterKey()) {
                    numMasterKeys++;
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("key found in {}: ID {} / master? {} / alg {} @ {} bits / finger print {}", file, key.getKeyID(), key.isMasterKey(), key.getAlgorithm(), key.getBitStrength(), Bytes.toHexString(key.getFingerprint()));
                }
            }

            if (numMasterKeys != 1) {
                throw new CryptoFailure("Failed to read " + type + " key from " + file + " (read " + numMasterKeys + " master keys, expected exactly one)");
            }

            return ring;
        } catch (IOException ex) {
            throw new CryptoFailure("Failed to read " + type + " key from " + file);
        }
    }

    private HierarchicalPublicKeyRings readPublicKeyRings(File directory) {
        HierarchicalPublicKeyRings out = new HierarchicalPublicKeyRings();

        File[] files = directory.listFiles(FILTER_PUBLIC_KEYS);
        if (files == null) {
            throw new IllegalArgumentException("Directory cannot be listed: " + directory);
        }

        for (File file : files) {
            PGPPublicKeyRing ring = readSinglePublicKeyRing(file);

            Long masterKeyId = null;
            Set<Long> subKeyIds = new HashSet<>();
            Iterator<PGPPublicKey> it = ring.getPublicKeys();
            while (it.hasNext()) {
                PGPPublicKey key = it.next();
                long keyId = key.getKeyID();
                if (key.isMasterKey()) {
                    if (masterKeyId != null) {
                        throw new CryptoFailure("multiple master keys found in key ring " + file);
                    }
                    masterKeyId = keyId;
                } else {
                    if (!subKeyIds.add(keyId)) {
                        throw new CryptoFailure("sub key listed multiple times in key ring " + file);
                    }
                }
            }

            for (long subKeyId : subKeyIds) {
                if (out.masterKeyIdsBySubKeyId.put(subKeyId, masterKeyId) != null) {
                    throw new CryptoFailure("sub key ID " + subKeyId + " is ambiguous");
                }
            }

            out.ringCollection = PGPPublicKeyRingCollection.addPublicKeyRing(out.ringCollection, ring);
        }

        if (out.ringCollection.size() < 1) {
            throw new CryptoFailure("Public keys could not be loaded");
        }

        return out;
    }

    private PGPSecretKeyRingCollection readSecretKeyRings(File directory) {
        PGPSecretKeyRingCollection ringCollection = new PGPSecretKeyRingCollection(Collections.emptyList());

        File[] files = directory.listFiles(FILTER_SECRET_KEYS);
        if (files == null) {
            throw new IllegalArgumentException("Directory cannot be listed: " + directory);
        }

        for (File file : files) {
            ringCollection = PGPSecretKeyRingCollection.addSecretKeyRing(ringCollection, readSingleSecretKeyRing(file, "secret"));
        }

        if (ringCollection.size() < 1) {
            throw new CryptoFailure("Secret keys could not be loaded");
        }

        return ringCollection;
    }

    @Override
    public byte[] decrypt(byte[] encrypted) {
        return decrypt(new ByteArrayInputStream(encrypted));
    }

    private byte[] decrypt(byte[] encrypted, KeySet keySet) {
        return decrypt(new ByteArrayInputStream(encrypted), keySet);
    }

    @Override
    public byte[] decrypt(InputStream is) {
        return decrypt(is, activeKeySet.get());
    }

    private byte[] decrypt(InputStream is, KeySet keySet) {
        synchronized (keySet) { // mitigates race condition in BouncyCastle (operations share state)
            try (
                DecryptionStream ds = PGPainless.decryptAndOrVerify()
                                                .onInputStream(is)
                                                .withOptions(new ConsumerOptions()
                                                                 .addDecryptionKeys(keySet.secretKeyRings, keySet.secretKeyRingProtector)
                                                );
            ) {
                byte[] decrypted = Streams.readAll(ds);
                ds.close();

                SubkeyIdentifier key = ds.getMetadata().getDecryptionKey();
                if (key == null) {
                    throw new CryptoFailure("Content is not encrypted");
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Decrypted using key {} (primary {}, subkey {}), fingerprint {}", key.getKeyId(), key.getPrimaryKeyId(), key.getSubkeyId(), key.getFingerprint());
                }

                return decrypted;
            } catch (PGPException | IOException ex) {
                throw new CryptoFailure("Failed to decrypt", ex);
            }
        }
    }

    @Override
    public Set<Long> getRelatedRecipientKeyIds(byte[] encrypted) {
        return getRelatedPublicKeyIds(getRecipientKeyIds(encrypted), activeKeySet.get());
    }

    private Set<Long> getRelatedPublicKeyIds(KeySet keySet, Long... keyIds) {
        return getRelatedPublicKeyIds(Arrays.asList(keyIds), keySet);
    }

    private Set<Long> getRelatedPublicKeyIds(Collection<Long> keyIds, KeySet keySet) {
        // TODO: current search is only from sub-key to master key but we may need to also return all sub-keys for each master key
        Set<Long> relatedKeyIds = new HashSet<>(keyIds);
        relatedKeyIds.addAll(getPublicMasterKeyIds(keyIds, keySet));
        return relatedKeyIds;
    }

    private Set<Long> getPublicMasterKeyIds(Collection<Long> masterOrSubKeyIds, KeySet keySet) {
        Set<Long> out = new HashSet<>();

        synchronized (keySet) { // mitigates race condition in BouncyCastle (operations share state)
            for (long keyId : masterOrSubKeyIds) {
                PGPPublicKey key = keySet.publicKeyRings.getPublicKey(keyId);
                if (key.isMasterKey()) {
                    out.add(key.getKeyID());
                } else {
                    Long masterKeyId = keySet.masterKeyIdsBySubKeyId.get(keyId);
                    if (masterKeyId == null) {
                        LOGGER.warn("Master key not known for sub key ID {}", keyId);
                        continue;
                    }
                    out.add(masterKeyId);
                }
            }
        }

        return out;
    }

    private Collection<Long> getRecipientKeyIds(byte[] encrypted) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(encrypted)) {
            return MessageInspector.determineEncryptionInfoForMessage(bais)
                                   .getKeyIds();
        } catch (IOException | PGPException ex) {
            throw new CryptoFailure("Failed to read encryption info", ex);
        }
    }

    @Override
    public byte[] encryptArmored(byte[] unencrypted) {
        return encrypt(unencrypted, false);
    }

    @Override
    public byte[] encryptUnarmored(byte[] unencrypted) {
        return encrypt(unencrypted, true);
    }

    private byte[] encrypt(byte[] unencrypted, boolean shouldUnarmor) {
        return encrypt(unencrypted, shouldUnarmor, activeKeySet.get());
    }

    private byte[] encrypt(byte[] unencrypted, boolean shouldUnarmor, KeySet keySet) {
        // TODO: filter out expired keys?

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        synchronized (keySet) { // mitigates race condition in BouncyCastle (operations share state)
            try (
                EncryptionStream es = PGPainless.encryptAndOrSign()
                                                .onOutputStream(baos)
                                                .withOptions(ProducerOptions.encrypt(
                                                                 new EncryptionOptions()
                                                                     .addRecipients(keySet.publicKeyRings)
                                                             )
                                                );
            ) {
                es.write(unencrypted);
                es.flush();
            } catch (PGPException | IOException ex) {
                throw new CryptoFailure("Failed to encrypt", ex);
            }
        }

        // PGPainless always encrypts to ASCII armored format
        byte[] encrypted = shouldUnarmor ? unarmor(baos.toByteArray()) : baos.toByteArray();

        // verify that we are still able to decrypt what we just encrypted
        byte[] decrypted = decrypt(encrypted, keySet);
        if (!Arrays.equals(unencrypted, decrypted)) {
            throw new CryptoFailure("Previously encrypted data failed to decrypt");
        }

        return encrypted;
    }

    private byte[] unarmor(byte[] armored) {
        try (
            ByteArrayInputStream bais = new ByteArrayInputStream(armored);
            ArmoredInputStream ais = ArmoredInputStreamFactory.get(bais);
        ) {
            return IOStreams.readAllBytes(ais);
        } catch (IOException ex) {
            throw new CryptoFailure("Failed to remove ASCII armor from data", ex);
        }
    }

    @Override
    public Signature sign(byte[] data) {
        return sign(data, activeKeySet.get());
    }

    private Signature sign(byte[] data, KeySet keySet) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        synchronized (keySet) { // mitigates race condition in BouncyCastle (operations share state)
            try (
                EncryptionStream es = PGPainless.encryptAndOrSign()
                                                .onOutputStream(baos)
                                                .withOptions(ProducerOptions.sign(
                                                                 new SigningOptions()
                                                                     .addDetachedSignature(keySet.secretKeyRingProtector, keySet.signingKey)
                                                             )
                                                );
            ) {
                es.write(data);
                es.flush();
                es.close();

                MultiMap<SubkeyIdentifier, PGPSignature> multiSignatures = es.getResult().getDetachedSignatures();
                if (multiSignatures.size() != 1) {
                    throw new CryptoFailure("Failed to sign; expected exactly one signature but got " + multiSignatures.size() + " on key-level");
                }

                Set<PGPSignature> signatures = multiSignatures.values().iterator().next();
                if (signatures.size() != 1) {
                    throw new CryptoFailure("Failed to sign; expected exactly one signature but got " + signatures.size() + " on value-level");
                }

                return new PGSignature(signatures.iterator().next());
            } catch (PGPException | IOException ex) {
                throw new CryptoFailure("Failed to sign", ex);
            }
        }
    }

    @Override
    public Set<Long> verify(byte[] signedDataWithoutSignature, Signature signature) {
        return verify(signedDataWithoutSignature, signature, activeKeySet.get());
    }

    private Set<Long> verify(byte[] signedDataWithoutSignature, Signature signature, KeySet keySet) {
        ByteArrayInputStream dataStream = new ByteArrayInputStream(signedDataWithoutSignature);
        ByteArrayInputStream signatureStream = new ByteArrayInputStream(signature.getBytes().orElse(signature.getAsciiArmored().getBytes(StandardCharsets.US_ASCII)));

        synchronized (keySet) { // mitigates race condition in BouncyCastle (operations share state)
            try (
                DecryptionStream ds = PGPainless.decryptAndOrVerify()
                                                .onInputStream(dataStream)
                                                .withOptions(new ConsumerOptions()
                                                                 .addVerificationCerts(keySet.publicKeyRings)
                                                                 .addVerificationOfDetachedSignatures(signatureStream)
                                                                 .forceNonOpenPgpData()
                                                );
            ) {
                IOStreams.consume(ds);
                ds.close();

                List<SignatureVerification> verified = ds.getMetadata().getVerifiedDetachedSignatures();
                if (verified.size() > 1) {
                    throw new CryptoFailure("Signature verification failed: " + verified.size() + " keys matched but only a single one is allowed");
                }

                Set<Long> allRelatedKeyIds = new HashSet<>();

                for (SignatureVerification item : verified) {
                    SubkeyIdentifier key = item.getSigningKey();

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Signature verified for key {} (primary {}, subkey {}), fingerprint: {}", key.getKeyId(), key.getPrimaryKeyId(), key.getSubkeyId(), key.getFingerprint().prettyPrint());
                    }

                    // NOTE: adding related keys is not cryptographically safe but sane in the limited context of this
                    //       application as there is no public key retrieval - do not reuse like this for anything else!

                    Set<Long> relatedKeyIds = getRelatedPublicKeyIds(keySet, key.getKeyId(), key.getPrimaryKeyId(), key.getSubkeyId());
                    if (relatedKeyIds.isEmpty()) {
                        LOGGER.warn("no related key IDs for key {} (primary {}, subkey {}), fingerprint: {}", key.getKeyId(), key.getPrimaryKeyId(), key.getSubkeyId(), key.getFingerprint().prettyPrint());
                    }

                    allRelatedKeyIds.addAll(relatedKeyIds);
                }

                return Collections.unmodifiableSet(allRelatedKeyIds);
            } catch (PGPException | IOException ex) {
                throw new CryptoFailure("Signature verification failed", ex);
            }
        }
    }

    // TODO: find a better way than synchronizing on KeySet to work around unsafe race conditions in PGPainless/BouncyCastle; related to https://github.com/bcgit/bc-java/issues/1379
}
