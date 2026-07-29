package com.tartis_recon_ai_parking.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // Indica a Spring que esta clase contiene reglas de configuración al arrancar
public class RabbitMQConfig {

    // Nombres de los componentes definidos previamente en nuestro Contrato del Evento
    public static final String EXCHANGE_NAME = "parking-events-exchange";
    public static final String TICKET_QUEUE = "ticket-service-stay-closed-queue";
    public static final String SPOT_QUEUE = "spot-service-stay-closed-queue";
    public static final String ROUTING_KEY_STAY_CLOSED = "stay-closed-v1";

    @Bean // Construye la oficina central de repartos (Exchange) de tipo Topic
    public TopicExchange parkingEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean // Construye el buzón (Cola) donde leerá el microservicio de tickets
    public Queue ticketStayClosedQueue() {
        return new Queue(TICKET_QUEUE);
    }

    @Bean // Construye el buzón (Cola) donde leerá el microservicio de plazas
    public Queue spotStayClosedQueue() {
        return new Queue(SPOT_QUEUE);
    }

    // NOTA: Spring Boot ejecuta cada @Bean una sola vez como una "fábrica de una sola pieza".
    // Por eso creamos dos métodos separados (uno por cada cola) en lugar de reutilizar un único método.
    @Bean // Enlaza la cola del ticket con la oficina central usando la etiqueta (routing key)
    public Binding ticketBinding(Queue ticketStayClosedQueue, TopicExchange parkingEventsExchange) {
        return BindingBuilder.bind(ticketStayClosedQueue)
                .to(parkingEventsExchange)
                .with(ROUTING_KEY_STAY_CLOSED);
    }

    @Bean // Enlaza la cola de plazas con la oficina central usando la misma etiqueta
    public Binding spotBinding(Queue spotStayClosedQueue, TopicExchange parkingEventsExchange) {
        return BindingBuilder.bind(spotStayClosedQueue)
                .to(parkingEventsExchange)
                .with(ROUTING_KEY_STAY_CLOSED);
    }

    @Bean // Traductor automático que transforma nuestro objeto Java a formato JSON al enviar
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
