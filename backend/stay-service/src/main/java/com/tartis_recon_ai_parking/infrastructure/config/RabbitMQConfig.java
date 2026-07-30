package com.tartis_recon_ai_parking.infrastructure.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // Indica a Spring que esta clase contiene reglas de configuración al arrancar
public class RabbitMQConfig {

    // Nombres de los componentes definidos previamente en nuestro Contrato del Evento
    public static final String EXCHANGE_NAME = "parking-events-exchange";
    public static final String ROUTING_KEY_STAY_CLOSED = "stay-closed-v1";

    // El publicador declara SOLO el exchange. Las colas de spot-service y
    // ticket-service las declara cada consumidor, que es quien conoce sus
    // argumentos (dead-lettering, TTL...). Declararlas aquí provocaba
    // PRECONDITION_FAILED en cuanto un consumidor añadía argumentos propios.
    @Bean // Construye la oficina central de repartos (Exchange) de tipo Topic
    public TopicExchange parkingEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean // Traductor automático que transforma nuestro objeto Java a formato JSON al enviar
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
