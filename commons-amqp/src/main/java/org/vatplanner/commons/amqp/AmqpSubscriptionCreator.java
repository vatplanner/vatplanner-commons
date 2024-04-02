package org.vatplanner.commons.amqp;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Creates a new listener for a queue on AMQP to receive "raw" AMQP messages. The listener is only registered,
 * it is not managed any further by this class. To "unregister" either the {@link Channel} has to be closed or the
 * queue (if an existing one was bound) needs to be deleted.
 * <p>
 * The creator instance must not be modified after {@link #subscribe()}.
 * </p>
 */
public class AmqpSubscriptionCreator {
    // TODO: management could actually be provided in terms of an option to cancel the subscription using the consumer tag returned on registration; add if needed

    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpSubscriptionCreator.class);

    final Channel channel;

    String logPrefix = "";
    private String queueName;
    private boolean declareQueue = true;
    private final LinkedHashSet<String> queueRoutingKeys = new LinkedHashSet<>();
    private ExchangeParameters exchangeParameters;
    boolean autoAck = true;
    private AmqpMessageHandler amqpMessageHandler;
    ReceiptAction errorReceiptAction;

    /**
     * Describes how to handle message confirmation on AMQP.
     */
    public enum ReceiptAction {
        /**
         * Acknowledges the message.
         * <p>
         * This option has no effect if auto-ack is enabled.
         * </p>
         */
        ACKNOWLEDGE(true),
        /**
         * Rejects the message and requeues it in AMQP.
         * <p>
         * This option requires auto-ack to be disabled, see {@link #withAutoAck(boolean)}.
         * </p>
         */
        REQUEUE(false, true),
        /**
         * Rejects the message and discards it, so that it will not be requeued.
         * <p>
         * This option requires auto-ack to be disabled, see {@link #withAutoAck(boolean)}.
         * </p>
         */
        DISCARD(false, false),
        /**
         * To be used if the message has already been confirmed; disables further handling.
         */
        ALREADY_CONFIRMED();

        private final boolean successful;
        private final boolean requeue;

        ReceiptAction() {
            this(true);
        }

        ReceiptAction(boolean successful) {
            this(successful, false);
        }

        ReceiptAction(boolean successful, boolean requeue) {
            this.successful = successful;
            this.requeue = requeue;
        }
    }

    /**
     * Handles a message received via AMQP. This is just an extension of RabbitMQ's
     * {@link com.rabbitmq.client.Consumer#handleDelivery(String, Envelope, AMQP.BasicProperties, byte[])} interface to
     * additionally decide the {@link ReceiptAction} to be handled by the general abstraction provided by
     * {@link AmqpSubscriptionCreator}.
     */
    @FunctionalInterface
    public interface AmqpMessageHandler {
        /**
         * Handles a message received via AMQP. The message does not need to be confirmed within this call, instead the
         * desired {@link ReceiptAction} needs to be returned.
         *
         * @param consumerTag the AMQP consumer tag
         * @param envelope    message {@link Envelope}
         * @param properties  message header properties
         * @param body        raw message body
         * @return decision on how to confirm the message
         * @throws IOException expected to be thrown by further method calls involving AMQP communication
         * @see com.rabbitmq.client.Consumer#handleDelivery(String, Envelope, AMQP.BasicProperties, byte[])
         */
        ReceiptAction handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException;
    }

    AmqpSubscriptionCreator(Channel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("No channel provided (may be unavailable on AMQP connection).");
        }

        this.channel = channel;
    }

    /**
     * Instantiates a new {@link AmqpSubscriptionCreator} for the given {@link Channel}.
     *
     * @param channel {@link Channel} to perform all actions on
     * @return new instance of {@link AmqpSubscriptionCreator}
     */
    public static AmqpSubscriptionCreator usingChannel(Channel channel) {
        return new AmqpSubscriptionCreator(channel);
    }

    /**
     * Sets the message receipt to be sent to the AMQP server if an error occurred during message handling.
     *
     * @param receiptAction message receipt to send in case of errors
     * @return same instance for method-chaining
     */
    public AmqpSubscriptionCreator onError(ReceiptAction receiptAction) {
        this.errorReceiptAction = receiptAction;

        if (errorReceiptAction == ReceiptAction.ACKNOWLEDGE) {
            LOGGER.warn("{}Configured to send positive receipt in case of errors: {}", logPrefix, errorReceiptAction);
        }

        return this;
    }

    /**
     * Configures the listener to be subscribed to an existing queue.
     * <p>
     * Using an existing queue disables queue declaration and does not set up any bindings.
     * </p>
     *
     * @param queueName name of existing queue to be subscribed to
     * @return same instance for method-chaining
     */
    public AmqpSubscriptionCreator forExistingQueue(String queueName) {
        this.queueName = queueName;
        declareQueue = false;
        return this;
    }

    /**
     * Configures the routing key(s) the queue should be bound to.
     * <p>
     * This option has no effect if an existing queue will be subscribed to.
     * </p>
     *
     * @param queueRoutingKeys routing key(s) used to bind queue to exchange
     * @return same instance for method-chaining
     */
    public AmqpSubscriptionCreator withQueueRoutingKeys(String... queueRoutingKeys) {
        return withQueueRoutingKeys(Arrays.asList(queueRoutingKeys));
    }

    /**
     * Configures the routing keys the queue should be bound to.
     * <p>
     * This option has no effect if an existing queue will be subscribed to.
     * </p>
     *
     * @param queueRoutingKeys routing keys used to bind queue to exchange
     * @return same instance for method-chaining
     */
    public AmqpSubscriptionCreator withQueueRoutingKeys(Collection<String> queueRoutingKeys) {
        this.queueRoutingKeys.addAll(queueRoutingKeys);
        return this;
    }

    /**
     * Configures the prefix name to use for all log messages. The actual prefix will include brackets for additional
     * separation, only the name to go inside those brackets needs to be provided.
     *
     * @param logPrefix name to prefix log messages with
     * @return same instance for method-chaining
     */
    public AmqpSubscriptionCreator withLogPrefix(String logPrefix) {
        this.logPrefix = "[" + logPrefix + "] ";
        return this;
    }

    /**
     * Sets the {@link ExchangeParameters} to be used.
     *
     * @param exchangeParameters describes all relevant parameters to declare and bind to the wanted exchange
     * @return same instance for method-chaining
     */
    public AmqpSubscriptionCreator withExchangeParameters(ExchangeParameters exchangeParameters) {
        this.exchangeParameters = exchangeParameters;
        return this;
    }

    /**
     * Controls whether all messages should be automatically acknowledged.
     * <p>
     * See AMQP server/protocol for exact details but generally automated acknowledgments will be issued immediately
     * when a message has been sent which can happen even before the message ever reaches any handling implementations.
     * The majority of messages usually do not require explicit message confirmation, so auto-ack is enabled by default.
     * In order to be able to perform any {@link ReceiptAction}s to indicate actual message handling or errors to the
     * AMQP server, auto-ack needs to be disabled.
     * </p>
     *
     * @param autoAck {@code false} disables automated acknowledgements, {@code true} (default) enables them
     * @return same instance for method-chaining
     */
    public AmqpSubscriptionCreator withAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
        return this;
    }

    /**
     * Registers the {@link AmqpMessageHandler} to process received messages.
     *
     * @param amqpMessageHandler will be called for all received messages
     * @return same instance for method-chaining
     */
    public AmqpSubscriptionCreator onMessage(AmqpMessageHandler amqpMessageHandler) {
        this.amqpMessageHandler = amqpMessageHandler;
        return this;
    }

    /**
     * Subscribes to the exchange/queue as previously configured.
     * <p>
     * This is a terminal operation; this instance must not be altered or reused following this method call.
     * </p>
     */
    public void subscribe() {
        if (exchangeParameters == null && (declareQueue || queueName == null)) {
            throw new IncompleteConfiguration(logPrefix + "Neither exchange parameters nor existing queue have been configured");
        } else if (amqpMessageHandler == null) {
            throw new IncompleteConfiguration(logPrefix + "Message handler has not been configured");
        }

        if (errorReceiptAction == null) {
            errorReceiptAction = autoAck ? ReceiptAction.ALREADY_CONFIRMED : ReceiptAction.DISCARD;
            LOGGER.trace("{}Error receipt action defaults to {}", logPrefix, errorReceiptAction);
        }

        try {
            if (exchangeParameters != null) {
                if (exchangeParameters.shouldDeclare()) {
                    channel.exchangeDeclare(exchangeParameters.getName(), exchangeParameters.getType(), exchangeParameters.isDurable());
                    LOGGER.debug("{}Exchange {} declared ({}, durable: {})", logPrefix, exchangeParameters.getName(), exchangeParameters.getType(), exchangeParameters.isDurable());
                } else {
                    channel.exchangeDeclarePassive(exchangeParameters.getName());
                    LOGGER.debug("{}Exchange {} exists", logPrefix, exchangeParameters.getName());
                }
            }

            if (!declareQueue) {
                if (queueName == null) {
                    throw new IncompleteConfiguration(logPrefix + "Existing queue should be used but queue name has not been configured");
                }

                LOGGER.debug("{}Using existing queue: {}", logPrefix, queueName);
            } else {
                queueName = channel.queueDeclare().getQueue();
                LOGGER.debug("{}Queue declared: {}", logPrefix, queueName);

                if (queueRoutingKeys.isEmpty()) {
                    queueRoutingKeys.add(exchangeParameters.getDefaultRoutingKey());
                    LOGGER.debug("{}Queue routing key has not been configured, using exchange default routing key \"{}\"", logPrefix, exchangeParameters.getDefaultRoutingKey());
                }

                for (String queueRoutingKey : queueRoutingKeys) {
                    channel.queueBind(queueName, exchangeParameters.getName(), queueRoutingKey);
                    LOGGER.debug("{}Queue bound to exchange (routing key: \"{}\")", logPrefix, queueRoutingKey);
                }
            }

            channel.basicConsume(queueName, autoAck, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    ReceiptAction receiptAction;
                    try {
                        receiptAction = amqpMessageHandler.handleDelivery(consumerTag, envelope, properties, body);
                    } catch (Exception ex) {
                        LOGGER.warn("{}Failed to handle message", logPrefix, ex);
                        receiptAction = errorReceiptAction;
                    }

                    performReceiptAction(receiptAction, envelope);
                }
            });
        } catch (IOException ex) {
            throw new ConnectionFailed(logPrefix + "Failed to set up listener", ex);
        }
    }

    private void performReceiptAction(ReceiptAction receiptAction, Envelope envelope) {
        if (receiptAction == ReceiptAction.ALREADY_CONFIRMED) {
            LOGGER.trace("{}Handler has already confirmed message receipt, nothing to be done.", logPrefix);
        } else if (autoAck) {
            if (receiptAction == ReceiptAction.ACKNOWLEDGE) {
                LOGGER.trace("{}Auto-Ack is active, ignoring ACK confirmation from handler.", logPrefix);
            } else {
                LOGGER.warn("{}Auto-Ack is active but handler wanted to sent NACK ({}); unable to signal handling via AMQP", logPrefix, receiptAction);
            }
        } else if (receiptAction.successful) {
            LOGGER.trace("{}Sending ACK", logPrefix);
            try {
                channel.basicAck(envelope.getDeliveryTag(), false);
            } catch (IOException ex) {
                LOGGER.warn("{}Failed to send ACK", logPrefix, ex);
            }
        } else {
            LOGGER.trace("{}Sending NACK", logPrefix);
            try {
                channel.basicNack(envelope.getDeliveryTag(), false, receiptAction.requeue);
            } catch (IOException ex) {
                LOGGER.warn("{}Failed to send NACK (requeue: {})", logPrefix, receiptAction.requeue, ex);
            }
        }
    }

    /**
     * Thrown if configuration is insufficient to create a listener.
     */
    private static class IncompleteConfiguration extends RuntimeException {
        IncompleteConfiguration(String msg) {
            super(msg);
        }
    }
}
