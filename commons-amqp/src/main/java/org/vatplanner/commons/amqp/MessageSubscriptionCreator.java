package org.vatplanner.commons.amqp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vatplanner.commons.crypto.CryptoFailure;
import org.vatplanner.commons.crypto.Cryptor;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;

/**
 * Creates a new listener for a queue on AMQP to receive deserialized {@link Message}s. The listener is only registered,
 * it is not managed any further by this class. To "unregister" either the {@link Channel} has to be closed or the
 * queue (if an existing one was bound) needs to be deleted.
 * <p>
 * The creator instance must not be modified after {@link #subscribe()}.
 * </p>
 */
public class MessageSubscriptionCreator extends AmqpSubscriptionCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSubscriptionCreator.class);

    private final Set<Class<? extends Message>> ignoredMessageClasses = new HashSet<>();
    private final Map<Class<? extends Message>, MessageHandler<?>> messageHandlersByClass = new HashMap<>();
    private final MessageCodec messageCodec = new MessageCodec();

    private Cryptor cryptor;
    private Duration messageTimeout = Duration.ofMinutes(30);
    private AtomicReference<Instant> minimumMessageTimestampRef;
    private Instant maximumMessageTimestamp;
    private ReceiptAction ignoreReceiptAction = ReceiptAction.ACKNOWLEDGE;

    private static final Duration CLOCK_DRIFT_ALLOWANCE = Duration.ofSeconds(5);
    private static final String SIGNATURE_HEADER = "pgpSignature"; // TODO: extract
    private static final String MIME_PGP_ENCRYPTED = "application/pgp-encrypted"; // TODO: extract

    private static final String MESSAGE_HEADER_UNENCRYPTED = "jsonMessage"; // TODO: extract
    private static final String MESSAGE_HEADER_PGP_ENCRYPTED = "pgpJsonMessage"; // TODO: extract

    private MessageSubscriptionCreator(Channel channel) {
        super(channel);
        onMessage(this::handleDelivery);
    }

    /**
     * Instantiates a new {@link MessageSubscriptionCreator} for the given {@link Channel}.
     *
     * @param channel {@link Channel} to perform all actions on
     * @return new instance of {@link MessageSubscriptionCreator}
     */
    public static MessageSubscriptionCreator usingChannel(Channel channel) {
        return new MessageSubscriptionCreator(channel);
    }

    /**
     * Provides a {@link Cryptor} to decrypt messages and verify signatures.
     *
     * @param cryptor used for decryption and signature verification
     * @return same instance for method-chaining
     */
    public MessageSubscriptionCreator withCryptor(Cryptor cryptor) {
        this.cryptor = cryptor;
        return this;
    }

    /**
     * Sets the message timeout. Messages indicating an older timestamp will be ignored as configured via
     * {@link #onIgnore(ReceiptAction)}.
     * <p>
     * Note that an additional {@link #CLOCK_DRIFT_ALLOWANCE} is tolerated to permit slight differences due to all
     * timestamps referencing local real-time system clocks instead of a uniform network clock.
     * </p>
     *
     * @param messageTimeout maximum age of messages to be processed
     * @return same instance for method-chaining
     */
    public MessageSubscriptionCreator notOlderThan(Duration messageTimeout) {
        this.messageTimeout = messageTimeout;
        return this;
    }

    /**
     * Sets the minimum timestamp received messages must indicate. Messages sent with an earlier timestamp will be
     * ignored as configured via {@link #onIgnore(ReceiptAction)}. This is different from the
     * {@link #notOlderThan(Duration)} timeout which checks the message age compared to time of processing whereas
     * this minimum timestamp is a fixed absolute point in time.
     * <p>
     * Note that an additional {@link #CLOCK_DRIFT_ALLOWANCE} is tolerated to permit slight differences due to all
     * timestamps referencing local real-time system clocks instead of a uniform network clock.
     * </p>
     *
     * @param minimumMessageTimestamp earliest point in time to accept messages from
     * @return same instance for method-chaining
     */
    public MessageSubscriptionCreator notSentBefore(Instant minimumMessageTimestamp) {
        this.minimumMessageTimestampRef = new AtomicReference<>(minimumMessageTimestamp);
        return this;
    }

    /**
     * Sets the reference to read the minimum timestamp from which received messages must indicate. The timestamp is
     * allowed to become available after subscription and message processing has already been started.
     * Messages sent with an earlier timestamp will be ignored as configured via {@link #onIgnore(ReceiptAction)}. This is different from the
     * {@link #notOlderThan(Duration)} timeout which checks the message age compared to time of processing whereas
     * this minimum timestamp is a fixed absolute point in time.
     * <p>
     * Note that an additional {@link #CLOCK_DRIFT_ALLOWANCE} is tolerated to permit slight differences due to all
     * timestamps referencing local real-time system clocks instead of a uniform network clock.
     * </p>
     *
     * @param minimumMessageTimestamp earliest point in time to accept messages from
     * @return same instance for method-chaining
     */
    public MessageSubscriptionCreator notSentBefore(AtomicReference<Instant> minimumMessageTimestamp) {
        this.minimumMessageTimestampRef = minimumMessageTimestamp;
        return this;
    }

    /**
     * Sets the maximum timestamp received messages must indicate. Messages sent with a later timestamp will be
     * ignored as configured via {@link #onIgnore(ReceiptAction)}. This filter only stops further processing but
     * the listener will continue to receive new messages; the {@link Channel} needs to be closed to actually stop
     * receiving useless messages after the given point in time has been reached.
     * <p>
     * Note that an additional {@link #CLOCK_DRIFT_ALLOWANCE} is tolerated to permit slight differences due to all
     * timestamps referencing local real-time system clocks instead of a uniform network clock.
     * </p>
     *
     * @param maximumMessageTimestamp latest point in time to accept messages from
     * @return same instance for method-chaining
     */
    public MessageSubscriptionCreator notSentAfter(Instant maximumMessageTimestamp) {
        this.maximumMessageTimestamp = maximumMessageTimestamp;
        return this;
    }

    /**
     * Registers a {@link MessageHandler} for the given {@link Message} type.
     *
     * @param messageClass   {@link Message} type to handle
     * @param messageHandler will receive all messages of the given type
     * @param <M>            {@link Message} type to handle
     * @return same instance for method-chaining
     */
    public <M extends Message> MessageSubscriptionCreator onMessage(Class<M> messageClass, MessageHandler<M> messageHandler) {
        if (messageHandlersByClass.put(messageClass, messageHandler) != null) {
            LOGGER.warn("{}Message handler for {} had been set before and is now overridden", logPrefix, messageClass);
        }

        if (ignoredMessageClasses.contains(messageClass)) {
            throw new IllegalArgumentException(logPrefix + "Message class is configured to be ignored: " + messageClass.getCanonicalName());
        }

        return this;
    }

    /**
     * Registers the given {@link Message} types to be ignored.
     * <p>
     * This must be set if multiple {@link Message} types are expected to be received on the same queue which have no
     * {@link MessageHandler}s registered as all non-ignored but unhandled messages are treated as errors.
     * </p>
     *
     * @param messageClasses {@link Message} types to be ignored if received
     * @return same instance for method-chaining
     */
    @SafeVarargs
    public final MessageSubscriptionCreator ignoringMessages(Class<? extends Message>... messageClasses) {
        return ignoringMessages(Arrays.asList(messageClasses));
    }

    /**
     * Registers the given {@link Message} types to be ignored.
     * <p>
     * This must be set if multiple {@link Message} types are expected to be received on the same queue which have no
     * {@link MessageHandler}s registered as all non-ignored but unhandled messages are treated as errors.
     * </p>
     *
     * @param messageClasses {@link Message} types to be ignored if received
     * @return same instance for method-chaining
     */
    public MessageSubscriptionCreator ignoringMessages(Collection<Class<? extends Message>> messageClasses) {
        for (Class<? extends Message> messageClass : messageClasses) {
            if (messageHandlersByClass.containsKey(messageClass)) {
                throw new IllegalArgumentException(logPrefix + "Message class is configured to be handled: " + messageClass);
            }

            this.ignoredMessageClasses.add(messageClass);
        }

        return this;
    }

    /**
     * Sets the {@link ReceiptAction} for all ignored messages (filtered by timestamp or message type).
     *
     * @param receiptAction action to perform for ignored messages
     * @return same instance for method-chaining
     */
    public MessageSubscriptionCreator onIgnore(ReceiptAction receiptAction) {
        this.ignoreReceiptAction = receiptAction;
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <M extends Message> ReceiptAction handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        try {
            Instant minimumMessageTimestamp = null;
            if (minimumMessageTimestampRef != null) {
                minimumMessageTimestamp = minimumMessageTimestampRef.get();
                if (minimumMessageTimestamp == null) {
                    LOGGER.warn("{}Received message before minimum allowed timestamp was available; ignoring: {}", logPrefix, body);
                    return ignoreReceiptAction;
                }
            }

            String bodyContentType = properties.getContentType();
            LOGGER.trace("{}Message body is indicated to have content type {}", logPrefix, bodyContentType);

            // signature is only verified later on and needs the (possibly) encrypted content, not the decrypted one
            byte[] signedData = body;

            boolean isEncryptedBody = MIME_PGP_ENCRYPTED.equals(bodyContentType);
            if (isEncryptedBody) {
                if (body.length == 0) {
                    LOGGER.debug("{}Message body has zero length, nothing to decrypt.", logPrefix);
                } else {
                    if (cryptor == null) {
                        LOGGER.warn("{}Received an encrypted message body although no Cryptor instance was provided, unable to decrypt!", logPrefix);
                        return errorReceiptAction;
                    }

                    try {
                        LOGGER.debug("{}Decrypting message body ({} bytes)", logPrefix, body.length);
                        body = cryptor.decrypt(body);
                    } catch (Exception ex) {
                        LOGGER.warn("{}Failed to decrypt message body", logPrefix, ex);
                        return errorReceiptAction;
                    }
                }
            }

            Map<String, Object> headers = properties.getHeaders();

            byte[] serializedMessage = body;
            Object unencryptedMessageHeader = (headers != null) ? headers.get(MESSAGE_HEADER_UNENCRYPTED) : null;
            Object encryptedMessageHeader = (headers != null) ? headers.get(MESSAGE_HEADER_PGP_ENCRYPTED) : null;

            if ((unencryptedMessageHeader != null) && (encryptedMessageHeader != null)) {
                LOGGER.warn("{}AMQP message contains an encrypted and an unencrypted message in properties header, unable to decide on message: {}", logPrefix, headers);
                return errorReceiptAction;
            }

            if (unencryptedMessageHeader != null) {
                LOGGER.debug("{}AMQP message contains an unencrypted message header; overriding deserialization input", logPrefix);
                serializedMessage = unencryptedMessageHeader.toString().getBytes(StandardCharsets.UTF_8);
                signedData = serializedMessage;
            } else if (encryptedMessageHeader != null) {
                if (cryptor == null) {
                    LOGGER.warn("{}Received an encrypted message although no Cryptor instance was provided, unable to decrypt!", logPrefix);
                    return errorReceiptAction;
                }

                LOGGER.debug("{}AMQP message contains an encrypted message header; overriding deserialization input", logPrefix);

                byte[] encryptedMessage = encryptedMessageHeader.toString().getBytes(StandardCharsets.UTF_8);
                signedData = encryptedMessage;

                try {
                    serializedMessage = cryptor.decrypt(encryptedMessage);
                } catch (Exception ex) {
                    LOGGER.warn("{}Failed to decrypt message from header", logPrefix, ex);
                    return errorReceiptAction;
                }
            }

            Message message = messageCodec.deserialize(properties, serializedMessage).orElse(null);
            if (message == null) {
                LOGGER.warn("{}Failed to deserialize message: {}", logPrefix, serializedMessage);
                return errorReceiptAction;
            }

            if (ignoredMessageClasses.contains(message.getClass())) {
                LOGGER.trace("{}Ignoring message: {}", logPrefix, message);
                return ignoreReceiptAction;
            }

            Class<M> messageClass = (Class) message.getClass();
            MessageHandler<M> messageHandler = (MessageHandler) messageHandlersByClass.get(messageClass);
            if (messageHandler == null) {
                LOGGER.warn("{}Unhandled message: {}", logPrefix, message);
                return errorReceiptAction;
            }

            Set<Long> verifiedKeyIds = Collections.emptySet();
            Object signatureHeader = headers != null ? headers.get(SIGNATURE_HEADER) : null;
            if (signatureHeader != null) {
                try {
                    verifiedKeyIds = cryptor.verify(signedData, Cryptor.Signature.asciiArmored(signatureHeader.toString()));
                } catch (CryptoFailure ex) {
                    LOGGER.warn("{}Message signature could not be verified", logPrefix, ex);
                }
            }

            Instant messageTimestamp = message.getTimestamp();
            if ((minimumMessageTimestamp != null) && messageTimestamp.isBefore(minimumMessageTimestamp.minus(CLOCK_DRIFT_ALLOWANCE))) {
                LOGGER.warn("{}Received message with timestamp {} but expected minimum timestamp is {}; ignoring: {}, AMQP properties: {}", logPrefix, messageTimestamp, minimumMessageTimestamp, message, properties);
                return ignoreReceiptAction;
            } else if ((maximumMessageTimestamp != null) && messageTimestamp.isAfter(maximumMessageTimestamp.plus(CLOCK_DRIFT_ALLOWANCE))) {
                LOGGER.warn("{}Received message with timestamp {} but expected maximum timestamp is {}; ignoring: {}, AMQP properties: {}", logPrefix, messageTimestamp, maximumMessageTimestamp, message, properties);
                return ignoreReceiptAction;
            }

            Duration messageAge = Duration.between(messageTimestamp, Instant.now());
            if (messageAge.isNegative()) {
                LOGGER.warn("{}Real-time clocks are out of sync: Received message {} with timestamp {} before current local clock (diff {}).", logPrefix, message, messageTimestamp, messageAge);
            } else if (messageAge.compareTo(messageTimeout.plus(CLOCK_DRIFT_ALLOWANCE)) > 0) {
                LOGGER.warn("{}Ignoring outdated message {} from network (age {} > timeout {})", logPrefix, message, messageAge, messageTimeout);
                return ignoreReceiptAction;
            }

            MessageSupplements.Builder supplementsBuilder = MessageSupplements.builder()
                                                                              .setAmqpEnvelope(envelope)
                                                                              .setAmqpProperties(properties)
                                                                              .setVerifiedKeyIds(verifiedKeyIds);

            supplementsBuilder.onAcknowledge(() -> {
                try {
                    LOGGER.trace("{}Acknowledging message via MessageSupplements callback: {}", logPrefix, message);
                    channel.basicAck(envelope.getDeliveryTag(), false);
                } catch (IOException ex) {
                    LOGGER.warn("{}Failed to acknowledge message from MessageSupplements callback: {}", logPrefix, message, ex);
                    return false;
                }

                return true;
            });

            if (body != serializedMessage) {
                LOGGER.trace("{}Message contains extra payload ({} bytes)", body.length);
                supplementsBuilder.setPayload(body);
            }

            LOGGER.trace("{}Dispatch to message handler: {}", logPrefix, message);
            return messageHandler.handleMessage((M) message, supplementsBuilder.build());
        } catch (Exception ex) {
            LOGGER.warn("{}Failed to process message", logPrefix, ex);
            return errorReceiptAction;
        }
    }

    // <editor-fold defaultstate="collapsed" desc="return type overload">
    @Override
    public MessageSubscriptionCreator forExistingQueue(String queueName) {
        super.forExistingQueue(queueName);
        return this;
    }

    @Override
    public MessageSubscriptionCreator onError(ReceiptAction receiptAction) {
        super.onError(receiptAction);
        return this;
    }

    @Override
    public MessageSubscriptionCreator withAutoAck(boolean autoAck) {
        super.withAutoAck(autoAck);
        return this;
    }

    @Override
    public MessageSubscriptionCreator withExchangeParameters(ExchangeParameters exchangeParameters) {
        super.withExchangeParameters(exchangeParameters);
        return this;
    }

    @Override
    public MessageSubscriptionCreator withLogPrefix(String logPrefix) {
        super.withLogPrefix(logPrefix);
        return this;
    }

    @Override
    public MessageSubscriptionCreator withQueueRoutingKeys(String... queueRoutingKeys) {
        super.withQueueRoutingKeys(queueRoutingKeys);
        return this;
    }

    @Override
    public MessageSubscriptionCreator withQueueRoutingKeys(Collection<String> queueRoutingKeys) {
        super.withQueueRoutingKeys(queueRoutingKeys);
        return this;
    }
    // </editor-fold>

    @FunctionalInterface
    public interface MessageHandler<M extends Message> {
        ReceiptAction handleMessage(M message, MessageSupplements supplements);
    }
}
