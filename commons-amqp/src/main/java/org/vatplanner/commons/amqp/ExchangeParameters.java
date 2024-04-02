package org.vatplanner.commons.amqp;

import java.util.Objects;
import java.util.Properties;

import org.vatplanner.commons.utils.PropertiesHelper;

/**
 * Holds and provides a {@link Builder} for the parameters of AMQP exchanges as commonly used throughout VATPlanner
 * configuration {@link Properties}.
 * <p>
 * Configuration keys are expected to start with {@value #PREFIX} followed by an exchange-specific key part and ending
 * in {@value #DECLARE_SUFFIX}, {@value #DEFAULT_ROUTING_KEY_SUFFIX}, {@value #DURABLE_SUFFIX}, {@value #NAME_SUFFIX}
 * and {@value #TYPE_SUFFIX}. If any of the keys are left unconfigured, the configured {@link Builder} defaults will be
 * applied instead.
 * </p>
 */
public class ExchangeParameters {
    private final String name;
    private final boolean declare;
    private final String defaultRoutingKey;
    private final boolean durable;
    private final String type;

    private static final String PREFIX = "amqp.exchanges.";

    private static final String DECLARE_SUFFIX = ".declare";
    private static final String DEFAULT_ROUTING_KEY_SUFFIX = ".defaultRoutingKey";
    private static final String DURABLE_SUFFIX = ".durable";
    private static final String NAME_SUFFIX = ".name";
    private static final String TYPE_SUFFIX = ".type";

    private ExchangeParameters(String name, boolean declare, String defaultRoutingKey, boolean durable, String type) {
        this.name = name;
        this.declare = declare;
        this.defaultRoutingKey = defaultRoutingKey;
        this.durable = durable;
        this.type = type;
    }

    /**
     * Returns the name of the AMQP exchange.
     *
     * @return exchange name
     */
    public String getName() {
        return name;
    }

    /**
     * Indicates whether the exchange should be declared on AMQP.
     * A "passive declaration" (check for existence) should usually still be performed even if the exchange should
     * not be "actively" declared (redefined).
     *
     * @return {@code true} if the exchange should be declared, {@code false} if not (check only)
     */
    public boolean shouldDeclare() {
        return declare;
    }

    /**
     * Returns the routing key that should be used to bind queues to the exchange unless implementation has specific
     * requirements to use different keys.
     *
     * @return routing key to bind queues to the exchange unless specific requirements exist
     */
    public String getDefaultRoutingKey() {
        return defaultRoutingKey;
    }

    /**
     * Indicates whether the exchange should be declared durable.
     *
     * @return {@code true} if the exchange should be durable, {@code false} if not
     */
    public boolean isDurable() {
        return durable;
    }

    /**
     * Returns what type the exchange should be when being declared.
     *
     * @return exchange type
     */
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "ExchangeParameters(name=\"" + name
            + "\", type=\"" + type
            + "\", declare=" + declare
            + ", durable=" + durable
            + ", defaultRoutingKey=\"" + defaultRoutingKey
            + "\")";
    }

    /**
     * Returns a new {@link Builder} to construct {@link ExchangeParameters}.
     *
     * @return new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds {@link ExchangeParameters} from {@link Properties}, falling back to configurable default values for
     * undefined parameters.
     */
    public static class Builder {
        private String defaultName;
        private boolean defaultDeclare = true;
        private String defaultDefaultRoutingKey = "";
        private boolean defaultDurable = true;
        private String defaultType = "direct";

        /**
         * Sets the exchange name to default to if left unconfigured by {@link Properties}.
         *
         * @param name default exchange name
         * @return same instance for method-chaining
         */
        public Builder defaultingToName(String name) {
            this.defaultName = name;
            return this;
        }

        /**
         * Sets whether the exchange should be declared by default if left unconfigured by {@link Properties}.
         *
         * @param declare {@code true} declares the exchange by default, {@code false} does not
         * @return same instance for method-chaining
         */
        public Builder defaultingToDeclare(boolean declare) {
            this.defaultDeclare = declare;
            return this;
        }

        /**
         * Sets the default routing key to use for binding queues that have no other requirement (see {@link #getDefaultRoutingKey()} for details).
         * This setting only applies if left unconfigured in {@link Properties}.
         *
         * @param defaultRoutingKey default routing key to default to
         * @return same instance for method-chaining
         */
        public Builder defaultingToDefaultRoutingKey(String defaultRoutingKey) {
            this.defaultDefaultRoutingKey = defaultRoutingKey;
            return this;
        }

        /**
         * Sets if the exchange should be declared durable by default if left unconfigured by {@link Properties}.
         *
         * @param durable {@code true} declares the exchange as durable if it is supposed to be declared; {@code false} does not
         * @return same instance for method-chaining
         */
        public Builder defaultingToDurable(boolean durable) {
            this.defaultDurable = durable;
            return this;
        }

        /**
         * Sets the exchange type to default to if left unconfigured by {@link Properties}.
         *
         * @param type default exchange type
         * @return same instance for method-chaining
         */
        public Builder defaultingToType(String type) {
            this.defaultType = type;
            return this;
        }

        /**
         * Applies the given {@link Properties} using default naming conventions using the given exchange name; see
         * {@link ExchangeParameters} JavaDoc.
         *
         * @param config          {@link Properties} to apply configuration from
         * @param exchangeKeyName exchange name used in {@link Properties} keys
         * @return same instance for method-chaining
         */
        public ExchangeParameters build(Properties config, String exchangeKeyName) {
            Objects.requireNonNull(defaultName, "default name must be set");

            return new ExchangeParameters(
                PropertiesHelper.getNonEmpty(config, PREFIX + exchangeKeyName + NAME_SUFFIX)
                                .orElse(defaultName),

                PropertiesHelper.getNonEmpty(config, PREFIX + exchangeKeyName + DECLARE_SUFFIX)
                                .map(Boolean::parseBoolean)
                                .orElse(defaultDeclare),

                PropertiesHelper.getNonEmpty(config, PREFIX + exchangeKeyName + DEFAULT_ROUTING_KEY_SUFFIX)
                                .orElse(defaultDefaultRoutingKey),

                PropertiesHelper.getNonEmpty(config, PREFIX + exchangeKeyName + DURABLE_SUFFIX)
                                .map(Boolean::parseBoolean)
                                .orElse(defaultDurable),

                PropertiesHelper.getNonEmpty(config, PREFIX + exchangeKeyName + TYPE_SUFFIX)
                                .orElse(defaultType)
            );
        }
    }
}
