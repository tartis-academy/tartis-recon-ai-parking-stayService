package com.tartis_recon_ai_parking.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void shouldCreateExchangeWithCorrectName() {
        TopicExchange exchange = config.parkingEventsExchange();
        assertEquals(RabbitMQConfig.EXCHANGE_NAME, exchange.getName());
    }

    @Test
    void shouldCreateJsonMessageConverter() {
        MessageConverter converter = config.jsonMessageConverter();
        assertTrue(converter instanceof Jackson2JsonMessageConverter);
    }
}
