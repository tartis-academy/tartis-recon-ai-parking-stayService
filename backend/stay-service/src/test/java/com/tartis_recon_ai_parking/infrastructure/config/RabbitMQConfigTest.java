package com.tartis_recon_ai_parking.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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

    @Test
    void shouldCreateTariffChangedQueueWithDeadLetterArguments() {
        Queue queue = config.tariffChangedQueue();

        assertEquals(RabbitMQConfig.TARIFF_CHANGED_QUEUE, queue.getName());
        assertEquals(RabbitMQConfig.DLX_EXCHANGE, queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RabbitMQConfig.ROUTING_KEY_TARIFF_CHANGED, queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void shouldBindTariffChangedQueueToSharedExchangeWithItsRoutingKey() {
        Queue queue = config.tariffChangedQueue();
        TopicExchange exchange = config.parkingEventsExchange();

        Binding binding = config.bindingTariffChanged(queue, exchange);

        assertEquals(RabbitMQConfig.EXCHANGE_NAME, binding.getExchange());
        assertEquals(RabbitMQConfig.TARIFF_CHANGED_QUEUE, binding.getDestination());
        assertEquals(RabbitMQConfig.ROUTING_KEY_TARIFF_CHANGED, binding.getRoutingKey());
    }

    @Test
    void shouldCreateSpotStatusChangedQueueWithDeadLetterArguments() {
        Queue queue = config.spotStatusChangedQueue();

        assertEquals(RabbitMQConfig.SPOT_STATUS_CHANGED_QUEUE, queue.getName());
        assertEquals(RabbitMQConfig.DLX_EXCHANGE, queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(RabbitMQConfig.ROUTING_KEY_SPOT_STATUS_CHANGED, queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void shouldBindSpotStatusChangedQueueToSharedExchangeWithItsRoutingKey() {
        Queue queue = config.spotStatusChangedQueue();
        TopicExchange exchange = config.parkingEventsExchange();

        Binding binding = config.bindingSpotStatusChanged(queue, exchange);

        assertEquals(RabbitMQConfig.EXCHANGE_NAME, binding.getExchange());
        assertEquals(RabbitMQConfig.SPOT_STATUS_CHANGED_QUEUE, binding.getDestination());
        assertEquals(RabbitMQConfig.ROUTING_KEY_SPOT_STATUS_CHANGED, binding.getRoutingKey());
    }

    @Test
    void shouldCreateDeadLetterExchangeAndQueuesBoundToOriginalRoutingKeys() {
        TopicExchange dlx = config.stayServiceEventsDLX();
        assertEquals(RabbitMQConfig.DLX_EXCHANGE, dlx.getName());

        Queue tariffDlq = config.tariffChangedDLQ();
        Binding tariffDlqBinding = config.bindingTariffChangedDLQ(tariffDlq, dlx);
        assertEquals(RabbitMQConfig.TARIFF_CHANGED_DLQ, tariffDlq.getName());
        assertEquals(RabbitMQConfig.ROUTING_KEY_TARIFF_CHANGED, tariffDlqBinding.getRoutingKey());

        Queue spotDlq = config.spotStatusChangedDLQ();
        Binding spotDlqBinding = config.bindingSpotStatusChangedDLQ(spotDlq, dlx);
        assertEquals(RabbitMQConfig.SPOT_STATUS_CHANGED_DLQ, spotDlq.getName());
        assertEquals(RabbitMQConfig.ROUTING_KEY_SPOT_STATUS_CHANGED, spotDlqBinding.getRoutingKey());
    }

    @Test
    void shouldCreateMessageRecovererPointingToDeadLetterExchange() {
        MessageRecoverer recoverer = config.messageRecoverer(mock(RabbitTemplate.class));
        assertTrue(recoverer instanceof RepublishMessageRecoverer);
    }
}
