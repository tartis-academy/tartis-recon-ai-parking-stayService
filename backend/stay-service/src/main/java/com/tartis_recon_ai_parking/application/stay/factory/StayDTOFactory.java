package com.tartis_recon_ai_parking.application.stay.factory;

import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.domain.stay.Stay;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StayDTOFactory {

	public StayDTO create(final Stay stay) {
		return new StayDTO(
				stay.getId(),
				stay.getVehicleId(),
				stay.getVehicleType(),
				stay.getSpotId(),
				stay.getTariffId(),
				stay.getCheckIn(),
				stay.getCheckOut(),
				stay.getTotalAmount(),
				stay.getStatus());
	}

	/** Con la matricula ya resuelta por quien llama (el dominio no la guarda). */
	public StayDTO create(final Stay stay, final String plate) {
		return new StayDTO(
				stay.getId(),
				stay.getVehicleId(),
				stay.getVehicleType(),
				stay.getSpotId(),
				stay.getTariffId(),
				stay.getCheckIn(),
				stay.getCheckOut(),
				stay.getTotalAmount(),
				stay.getStatus(),
				plate);
	}

	public List<StayDTO> create(final List<Stay> stays) {
		return stays.stream().map(this::create).toList();
	}

}
