package com.tartis_recon_ai_parking.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
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

    public static final String ROUTING_KEY_TICKET_CHANGED = "ticket-changed-v1";
    public static final String TICKET_CHANGED_QUEUE = "stay-service-ticket-changed-queue";

    public static final String ROUTING_KEY_VEHICLE_CHANGED = "vehicle-changed-v1";
    public static final String VEHICLE_CHANGED_QUEUE = "stay-service-vehicle-changed-queue";

    // DLX/DLQ compartido para los consumidores nuevos, siguiendo el mismo
    // patron que spot-service/ticket-service ya usan para stay-closed-v1
    // (ASY-08). Un unico DLX topic con una DLQ por evento: RepublishMessageRecoverer
    // republica con errorRoutingKeyPrefix + routing key ORIGINAL del mensaje,
    // asi que cada fallo cae en su propia DLQ aunque el recoverer sea uno solo.
    // El prefijo por defecto es "error." (no vacio), asi que hay que anularlo
    // explicitamente con errorRoutingKeyPrefix("") para que la routing key
    // republicada case con los bindings de abajo, que no llevan prefijo.
    public static final String DLX_EXCHANGE = "stay-service-events-dlx";
    public static final String TARIFF_CHANGED_DLQ = "stay-service-tariff-changed-dlq";
    public static final String SPOT_STATUS_CHANGED_DLQ = "stay-service-spot-status-changed-dlq";
    public static final String TICKET_CHANGED_DLQ = "stay-service-ticket-changed-dlq";
    public static final String VEHICLE_CHANGED_DLQ = "stay-service-vehicle-changed-dlq";

    // El publicador declara SOLO el exchange. Las colas de spot-service y
    // ticket-service las declara cada consumidor, que es quien conoce sus
    // argumentos (dead-lettering, TTL...). Declararlas aquí provocaba
    // RES-07: RECONCILIACIÓN DE TICKETS OFFLINE (NUEVO)
    public static final String ROUTING_KEY_ENTRY_TICKET_OFFLINE = "entry-ticket-offline-v1";

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

    @Bean
    public Queue ticketChangedQueue() {
        return QueueBuilder.durable(TICKET_CHANGED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_TICKET_CHANGED)
                .build();
    }

    @Bean
    public Binding bindingTicketChanged(Queue ticketChangedQueue, TopicExchange parkingEventsExchange) {
        return BindingBuilder.bind(ticketChangedQueue)
                .to(parkingEventsExchange)
                .with(ROUTING_KEY_TICKET_CHANGED);
    }

    @Bean
    public Queue vehicleChangedQueue() {
        return QueueBuilder.durable(VEHICLE_CHANGED_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_VEHICLE_CHANGED)
                .build();
    }

    @Bean
    public Binding bindingVehicleChanged(Queue vehicleChangedQueue, TopicExchange parkingEventsExchange) {
        return BindingBuilder.bind(vehicleChangedQueue)
                .to(parkingEventsExchange)
                .with(ROUTING_KEY_VEHICLE_CHANGED);
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
    public Queue ticketChangedDLQ() {
        return QueueBuilder.durable(TICKET_CHANGED_DLQ).build();
    }

    @Bean
    public Binding bindingTicketChangedDLQ(Queue ticketChangedDLQ, TopicExchange stayServiceEventsDLX) {
        return BindingBuilder.bind(ticketChangedDLQ)
                .to(stayServiceEventsDLX)
                .with(ROUTING_KEY_TICKET_CHANGED);
    }

    @Bean
    public Queue vehicleChangedDLQ() {
        return QueueBuilder.durable(VEHICLE_CHANGED_DLQ).build();
    }

    @Bean
    public Binding bindingVehicleChangedDLQ(Queue vehicleChangedDLQ, TopicExchange stayServiceEventsDLX) {
        return BindingBuilder.bind(vehicleChangedDLQ)
                .to(stayServiceEventsDLX)
                .with(ROUTING_KEY_VEHICLE_CHANGED);
    }

    @Bean
    public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, DLX_EXCHANGE)
                .errorRoutingKeyPrefix("");
    }

    @Bean // Traductor automático que transforma nuestro objeto Java a formato JSON al enviar
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Los listeners de SSE-06 (tariff-changed, spot-status-changed) solo hacen
    // un broadcast() en memoria: el paralelismo no compra throughput, y con
    // concurrency>1 dos eventos de la misma plaza/tarifa pueden procesarse a
    // la vez y llegar al SSE en orden invertido. Un factory dedicado con
    // concurrency=1 evita eso sin tocar spring.rabbitmq.listener.simple.* (que
    // seguiria aplicando a cualquier otro listener que se anada despues).
    @Bean
    public SimpleRabbitListenerContainerFactory sseListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        return factory;
    }
}
