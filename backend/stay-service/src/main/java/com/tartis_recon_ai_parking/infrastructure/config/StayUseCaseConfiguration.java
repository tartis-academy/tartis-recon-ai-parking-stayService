package com.tartis_recon_ai_parking.infrastructure.config;

import com.tartis_recon_ai_parking.application.stay.factory.StayDTOFactory;
import com.tartis_recon_ai_parking.application.stay.port.output.StayPersistence;
import com.tartis_recon_ai_parking.application.stay.port.output.StaySpotPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTariffPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayTicketPort;
import com.tartis_recon_ai_parking.application.stay.port.output.StayVehiclePort;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckInUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.GetActiveStayUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckOutUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.GetStayUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.ListStaysUseCase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Ensambla los casos de uso de stay con sus puertos.
 *
 * <p>Los casos de uso son POJOs sin anotaciones de Spring (IN-35): es aqui, en
 * infrastructure, donde se elige que implementacion concreta recibe cada puerto,
 * materializando la inversion de dependencias (IN-33).
 *
 * <p>Separada de {@code BeanConfiguration} (que define el {@code RestClient.Builder})
 * para no colisionar con esa clase de la PR de puertos.
 */
@Configuration
public class StayUseCaseConfiguration {

    @Bean
    CheckInUseCase checkInUseCase(StayPersistence stayPersistence,
                                  StayVehiclePort vehiclePort,
                                  StaySpotPort spotPort,
                                  StayTariffPort tariffPort,
                                  StayDTOFactory stayDTOFactory,
                                  Clock clock) {
        return new CheckInUseCase(stayPersistence, vehiclePort, spotPort, tariffPort, stayDTOFactory, clock);
    }

    @Bean
    GetActiveStayUseCase getActiveStayUseCase(StayPersistence stayPersistence,
                                               StayDTOFactory stayDTOFactory) {
        return new GetActiveStayUseCase(stayPersistence, stayDTOFactory);
    }

    @Bean
    CheckOutUseCase checkOutUseCase(StayPersistence stayPersistence,
                                    StayVehiclePort vehiclePort,
                                    StayTariffPort tariffPort,
                                    StaySpotPort spotPort,
                                    StayTicketPort ticketPort,
                                    StayDTOFactory stayDTOFactory,
                                    Clock clock) {
        return new CheckOutUseCase(stayPersistence, vehiclePort, tariffPort, spotPort, ticketPort, stayDTOFactory, clock);
    }

    /**
     * Reloj inyectado en vez de {@code Instant.now()} dentro del caso de uso: los
     * tests fijan la hora de entrada y comprueban los invariantes sin depender del
     * reloj de la maquina.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    GetStayUseCase getStayUseCase(StayPersistence stayPersistence, StayDTOFactory stayDTOFactory) {
        return new GetStayUseCase(stayPersistence, stayDTOFactory);
    }

    @Bean
    ListStaysUseCase listStaysUseCase(StayPersistence stayPersistence, StayDTOFactory stayDTOFactory) {
        return new ListStaysUseCase(stayPersistence, stayDTOFactory);
    }
}
