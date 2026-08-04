package com.tartis_recon_ai_parking.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // Indica a Spring que esta clase contiene reglas de configuración al arrancar
public class RabbitMQConfig {

    // Nombres de los componentes definidos previamente en nuestro Contrato del Evento
    public static final String EXCHANGE_NAME = "parking-events-exchange";
    public static final String ROUTING_KEY_STAY_CLOSED = "stay-closed-v1";

    // SSE-06: stay-service pasa de ser solo publicador a ser tambien consumidor
    // de los eventos que nacen en tariff-service y spot-service, para
    // reenviarlos por SSE (StayEventStreamPublisher).
    public static final String ROUTING_KEY_TARIFF_CHANGED = "tariff-changed-v1";
    public static final String TARIFF_CHANGED_QUEUE = "stay-service-tariff-changed-queue";

    public static final String ROUTING_KEY_SPOT_STATUS_CHANGED = "spot-status-changed-v1";
    public static final String SPOT_STATUS_CHANGED_QUEUE = "stay-service-spot-status-changed-queue";

    // DLX/DLQ compartido para los consumidores nuevos, siguiendo el mismo
    // patron que spot-service/ticket-service ya usan para stay-closed-v1
    // (ASY-08). Un unico DLX topic con una DLQ por evento: RepublishMessageRecoverer
    // sin routing key fija reenvia usando la routing key ORIGINAL del mensaje,
    // asi que cada fallo cae en su propia DLQ aunque el recoverer sea uno solo.
    public static final String DLX_EXCHANGE = "stay-service-events-dlx";
    public static final String TARIFF_CHANGED_DLQ = "stay-service-tariff-changed-dlq";
    public static final String SPOT_STATUS_CHANGED_DLQ = "stay-service-spot-status-changed-dlq";

    // El publicador declara SOLO el exchange. Las colas de spot-service y
    // ticket-service las declara cada consumidor, que es quien conoce sus
    // argumentos (dead-lettering, TTL...). Declararlas aquí provocaba
    // PRECONDITION_FAILED en cuanto un consumidor añadía argumentos propios.
    @Bean // Construye la oficina central de repartos (Exchange) de tipo Topic
    public TopicExchange parkingEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue tariffChangedQueue() {
        return QueueBuilder.durable(TARIFF_CHANGED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_TARIFF_CHANGED)
                .build();
    }

    @Bean
    public Binding bindingTariffChanged(Queue tariffChangedQueue, TopicExchange parkingEventsExchange) {
        return BindingBuilder.bind(tariffChangedQueue)
                .to(parkingEventsExchange)
                .with(ROUTING_KEY_TARIFF_CHANGED);
    }

    @Bean
    public Queue spotStatusChangedQueue() {
        return QueueBuilder.durable(SPOT_STATUS_CHANGED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_SPOT_STATUS_CHANGED)
                .build();
    }

    @Bean
    public Binding bindingSpotStatusChanged(Queue spotStatusChangedQueue, TopicExchange parkingEventsExchange) {
        return BindingBuilder.bind(spotStatusChangedQueue)
                .to(parkingEventsExchange)
                .with(ROUTING_KEY_SPOT_STATUS_CHANGED);
    }

    // =========================================================================
    // DEAD LETTER QUEUE (DLQ) & EXCHANGE (DLX) PARA LOS CONSUMIDORES DE STAY-SERVICE
    // =========================================================================
    @Bean
    public TopicExchange stayServiceEventsDLX() {
        return new TopicExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue tariffChangedDLQ() {
        return QueueBuilder.durable(TARIFF_CHANGED_DLQ).build();
    }

    @Bean
    public Binding bindingTariffChangedDLQ(Queue tariffChangedDLQ, TopicExchange stayServiceEventsDLX) {
        return BindingBuilder.bind(tariffChangedDLQ)
                .to(stayServiceEventsDLX)
                .with(ROUTING_KEY_TARIFF_CHANGED);
    }

    @Bean
    public Queue spotStatusChangedDLQ() {
        return QueueBuilder.durable(SPOT_STATUS_CHANGED_DLQ).build();
    }

    @Bean
    public Binding bindingSpotStatusChangedDLQ(Queue spotStatusChangedDLQ, TopicExchange stayServiceEventsDLX) {
        return BindingBuilder.bind(spotStatusChangedDLQ)
                .to(stayServiceEventsDLX)
                .with(ROUTING_KEY_SPOT_STATUS_CHANGED);
    }

    @Bean
    public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, DLX_EXCHANGE);
    }

    @Bean // Traductor automático que transforma nuestro objeto Java a formato JSON al enviar
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
