package org.vatplanner.commons.amqp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.rabbitmq.client.AMQP;

/**
 * Serializes and deserializes {@link Message}s with a basic container for transport over AMQP as JSON.
 */
public class MessageCodec {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageCodec.class);

    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String PGP_ENCRYPTED_CONTENT_TYPE = "application/pgp-encrypted";
    private static final int CURRENT_FORMAT_VERSION = 1;

    private final Map<String, Message.Parser<?>> parsersByMessageType = new HashMap<>();

    private enum ContainerKey implements JsonKey {
        FORMAT_VERSION("_containerFormatVersion"),
        MESSAGE_TYPE("_messageType");

        private final String key;

        ContainerKey(String key) {
            this.key = key;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return null;
        }
    }

    public MessageCodec() {
        for (Message.Parser<?> parser : ServiceLoader.load(Message.Parser.class)) {
            Message.Parser<?> previousParser = parsersByMessageType.put(parser.getMessageType(), parser);
            if (previousParser != null) {
                throw new AmbiguousParsers(previousParser, parser);
            }
        }
    }

    /**
     * Serializes the given {@link Message} for JSON-encoded transport over AMQP.
     * Encryption needs to be taken care of separately, if needed.
     *
     * @param propertiesBuilder will receive message-related properties
     * @param message           message to encode
     * @return serialized message (unencrypted)
     */
    public byte[] serialize(AMQP.BasicProperties.Builder propertiesBuilder, Message message) {
        String messageType = message.getMessageType();
        JsonObject out = message.toJson();

        requireNotSet(out, ContainerKey.MESSAGE_TYPE, "Message type blocks container key " + ContainerKey.MESSAGE_TYPE + "; unable to serialize.");
        requireNotSet(out, ContainerKey.FORMAT_VERSION, "Message type blocks container key " + ContainerKey.FORMAT_VERSION + "; unable to serialize.");

        out.put(ContainerKey.MESSAGE_TYPE, messageType);
        out.put(ContainerKey.FORMAT_VERSION, CURRENT_FORMAT_VERSION);

        byte[] bytes;
        try (
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        ) {
            out.toJson(osw);
            osw.flush();
            bytes = baos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to serialize " + message, ex);
        }

        propertiesBuilder.contentType(JSON_CONTENT_TYPE);

        return bytes;
    }

    private void requireNotSet(JsonObject out, JsonKey key, String msg) {
        if (out.containsKey(key.getKey())) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Deserializes the given {@link Message} as received via AMQP.
     * Decryption and signature verification is not taken care of.
     *
     * @param properties message properties as received from AMQP
     * @param body       serialized message to decode (must have been decrypted)
     * @return deserialized message; empty if the message is not handled
     */
    public Optional<Message> deserialize(AMQP.BasicProperties properties, byte[] body) {
        // TODO: provide content-type by argument, message to be deserialized may come from extra header where regular content-type header does not apply
        String contentType = properties.getContentType();
        if (contentType != null && !(JSON_CONTENT_TYPE.equals(contentType) || PGP_ENCRYPTED_CONTENT_TYPE.equals(contentType))) {
            LOGGER.warn("Message received from AMQP with unhandled content type \"{}\"", contentType);
            return Optional.empty();
        }

        JsonObject json;

        try (
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            InputStreamReader isr = new InputStreamReader(bais, StandardCharsets.UTF_8);
        ) {
            json = (JsonObject) Jsoner.deserialize(isr);
        } catch (ClassCastException | JsonException | IOException ex) {
            LOGGER.warn("Failed to read JSON message", ex);
            return Optional.empty();
        }

        try {
            json.requireKeys(ContainerKey.FORMAT_VERSION, ContainerKey.MESSAGE_TYPE);

            int actualFormatVersion = json.getInteger(ContainerKey.FORMAT_VERSION);
            if (actualFormatVersion != CURRENT_FORMAT_VERSION) {
                LOGGER.warn("Received message indicates format version {} but this implementation only supports {}", actualFormatVersion, CURRENT_FORMAT_VERSION);
                return Optional.empty();
            }

            String messageType = json.getString(ContainerKey.MESSAGE_TYPE);

            Message.Parser<?> parser = parsersByMessageType.get(messageType);
            if (parser == null) {
                LOGGER.warn("Unhandled message type \"{}\"", messageType);
                return Optional.empty();
            }

            return Optional.of(parser.fromJson(json));
        } catch (Exception ex) {
            LOGGER.warn("Failed to process JSON message", ex);
            return Optional.empty();
        }
    }

    private static class AmbiguousParsers extends RuntimeException {
        AmbiguousParsers(Message.Parser... parsers) {
            super(
                "Multiple parsers are present for message type \"" + parsers[0].getMessageType() + "\": "
                    + Arrays.stream(parsers)
                            .map(Object::getClass)
                            .map(Class::getCanonicalName)
                            .collect(Collectors.joining(", "))
            );
        }
    }
}
