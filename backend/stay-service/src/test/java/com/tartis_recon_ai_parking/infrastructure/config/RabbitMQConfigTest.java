package com.tartis_recon_ai_parking.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
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
    void shouldCreateTicketQueueWithCorrectName() {
        Queue queue = config.ticketStayClosedQueue();
        assertEquals(RabbitMQConfig.TICKET_QUEUE, queue.getName());
    }

    @Test
    void shouldCreateSpotQueueWithCorrectName() {
        Queue queue = config.spotStayClosedQueue();
        assertEquals(RabbitMQConfig.SPOT_QUEUE, queue.getName());
    }

    @Test
    void shouldBindTicketQueueToExchangeWithCorrectRoutingKey() {
        Queue queue = config.ticketStayClosedQueue();
        TopicExchange exchange = config.parkingEventsExchange();
        Binding binding = config.ticketBinding(queue, exchange);

        assertEquals(RabbitMQConfig.TICKET_QUEUE, binding.getDestination());
        assertEquals(Binding.DestinationType.QUEUE, binding.getDestinationType());
        assertEquals(RabbitMQConfig.EXCHANGE_NAME, binding.getExchange());
        assertEquals(RabbitMQConfig.ROUTING_KEY_STAY_CLOSED, binding.getRoutingKey());
    }

    @Test
    void shouldBindSpotQueueToExchangeWithCorrectRoutingKey() {
        Queue queue = config.spotStayClosedQueue();
        TopicExchange exchange = config.parkingEventsExchange();
        Binding binding = config.spotBinding(queue, exchange);

        assertEquals(RabbitMQConfig.SPOT_QUEUE, binding.getDestination());
        assertEquals(Binding.DestinationType.QUEUE, binding.getDestinationType());
        assertEquals(RabbitMQConfig.EXCHANGE_NAME, binding.getExchange());
        assertEquals(RabbitMQConfig.ROUTING_KEY_STAY_CLOSED, binding.getRoutingKey());
    }

    @Test
    void shouldCreateJsonMessageConverter() {
        MessageConverter converter = config.jsonMessageConverter();
        assertTrue(converter instanceof Jackson2JsonMessageConverter);
    }
}
