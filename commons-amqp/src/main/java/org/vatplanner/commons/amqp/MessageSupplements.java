package org.vatplanner.commons.amqp;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.vatplanner.commons.crypto.Cryptor;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

/**
 * Provides additional information and actions to {@link MessageSubscriptionCreator.MessageHandler}s.
 */
public class MessageSupplements {
    private final Envelope amqpEnvelope;
    private final AMQP.BasicProperties amqpProperties;
    private final Set<Long> verifiedKeyIds;
    private final byte[] payload;
    private final Supplier<Boolean> acknowledge;

    private MessageSupplements(Envelope amqpEnvelope, AMQP.BasicProperties amqpProperties, Set<Long> verifiedKeyIds, byte[] payload, Supplier<Boolean> acknowledge) {
        this.amqpEnvelope = amqpEnvelope;
        this.amqpProperties = amqpProperties;
        this.verifiedKeyIds = new HashSet<>(verifiedKeyIds);
        this.payload = payload;
        this.acknowledge = acknowledge;
    }

    /**
     * Returns the {@link Envelope} of the original AMQP message.
     *
     * @return AMQP message envelope
     */
    public Envelope getAmqpEnvelope() {
        return amqpEnvelope;
    }

    /**
     * Returns the {@link AMQP.BasicProperties} of the original AMQP message.
     *
     * @return AMQP message properties
     */
    public AMQP.BasicProperties getAmqpProperties() {
        return amqpProperties;
    }

    /**
     * Returns the IDs of all keys related to the signature used for the {@link Message} as known to the {@link MessageSubscriptionCreator}'s {@link Cryptor}
     * instance at time of reception.
     * <p>
     * An empty set indicates that the message either was not signed or the signature could not be verified. If IDs are present, the signature was verified for
     * related keys.
     * </p>
     *
     * @return IDs related to keys the message was verified for; empty indicates an unsigned or unverified message
     */
    public Set<Long> getVerifiedKeyIds() {
        return Collections.unmodifiableSet(verifiedKeyIds);
    }

    /**
     * Returns the payload that was sent together with the message, if present.
     *
     * @return payload; empty if only the message was received
     */
    public Optional<byte[]> getPayload() {
        return Optional.ofNullable(payload);
    }

    /**
     * Acknowledges the message on AMQP (ACK confirmation); visible to sender.
     *
     * @return {@code true} if successful; {@code false} if the acknowledgement could not be sent
     */
    public boolean acknowledgeMessage() {
        return acknowledge.get();
    }

    /**
     * Instantiates a new {@link Builder} to construct {@link MessageSupplements}.
     *
     * @return new {@link Builder} to construct {@link MessageSupplements}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MessageSupplements}.
     */
    public static class Builder {
        private Envelope amqpEnvelope;
        private AMQP.BasicProperties amqpProperties;
        private Set<Long> verifiedKeyIds;
        private byte[] payload;
        private Supplier<Boolean> acknowledge;

        private Builder() {
            // use static method instead
        }

        /**
         * Sets the AMQP message {@link Envelope} for the associated message.
         *
         * @param amqpEnvelope AMQP message envelope
         * @return same instance for method-chaining
         */
        public Builder setAmqpEnvelope(Envelope amqpEnvelope) {
            this.amqpEnvelope = amqpEnvelope;
            return this;
        }

        /**
         * Sets the AMQP message properties for the associated message.
         *
         * @param amqpProperties AMQP message properties
         * @return same instance for method-chaining
         */
        public Builder setAmqpProperties(AMQP.BasicProperties amqpProperties) {
            this.amqpProperties = amqpProperties;
            return this;
        }

        /**
         * Sets the payload for the associated message.
         *
         * @param payload binary payload
         * @return same instance for method-chaining
         */
        public Builder setPayload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        /**
         * Sets the key IDs related to signature verification as returned by {@link Cryptor#verify(byte[], Cryptor.Signature)}.
         *
         * @param verifiedKeyIds key IDs as returned for message signature verification by {@link Cryptor#verify(byte[], Cryptor.Signature)}
         * @return same instance for method-chaining
         */
        public Builder setVerifiedKeyIds(Set<Long> verifiedKeyIds) {
            this.verifiedKeyIds = new HashSet<>(verifiedKeyIds);
            return this;
        }

        /**
         * Sets the callback to be invoked through {@link MessageSupplements#acknowledgeMessage()}.
         * <p>
         * Must return success of operation as specified in {@link MessageSupplements#acknowledgeMessage()}.
         * </p>
         *
         * @param acknowledge callback to acknowledge the AMQP message
         * @return same instance for method-chaining
         * @see #acknowledgeMessage()
         */
        public Builder onAcknowledge(Supplier<Boolean> acknowledge) {
            this.acknowledge = acknowledge;
            return this;
        }

        /**
         * Builds a {@link MessageSupplements} instance as previously configured.
         *
         * @return {@link MessageSupplements} as configured
         */
        public MessageSupplements build() {
            return new MessageSupplements(amqpEnvelope, amqpProperties, verifiedKeyIds, payload, acknowledge);
        }
    }
}
