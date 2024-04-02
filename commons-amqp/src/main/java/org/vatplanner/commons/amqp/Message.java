package org.vatplanner.commons.amqp;

import java.time.Instant;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * Required to be implemented to make a message class compatible with {@link MessageCodec} serialization/deserialization.
 */
public interface Message {
    /**
     * Returns the creation timestamp of the {@link Message}.
     * Used by receiving peers to check if a message is still relevant or timed out.
     *
     * @return creation timestamp of the {@link Message}
     */
    Instant getTimestamp();

    /**
     * Returns a unique identification of this message type.
     * Must be consistent with {@link Message.Parser#getMessageType()}.
     *
     * @return unique identification of this message type
     */
    String getMessageType();

    /**
     * Serializes the message contents to JSON.
     *
     * @return message content serialized to JSON
     */
    JsonObject toJson();

    /**
     * Deserializes messages of a specific type.
     * <p>
     * <strong>Important:</strong> Exactly one instance must be locatable via SPI for {@link MessageCodec} to be able
     * to locate the parser.
     * </p>
     *
     * @param <T> handled {@link Message}
     */
    interface Parser<T extends Message> {
        /**
         * Returns a unique identification of this message type.
         * Must be consistent with {@link Message#getMessageType()}.
         *
         * @return unique identification of this message type
         */
        String getMessageType();

        /**
         * Deserializes a {@link Message} from given JSON.
         *
         * @param json JSON representation of given message type
         * @return deserialized {@link Message}
         */
        T fromJson(JsonObject json);
    }
}
