package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest;

import com.tartis_recon_ai_parking.application.stay.dto.CheckInResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.CheckOutResultDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayDTO;
import com.tartis_recon_ai_parking.application.stay.dto.StayPageDTO;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckInUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.CheckOutUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.GetStayUseCase;
import com.tartis_recon_ai_parking.application.stay.usecase.ListStaysUseCase;
import com.tartis_recon_ai_parking.domain.stay.StayStatus;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request.StayCheckOutRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request.StayRequest;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.CheckInResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.CheckOutResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.StayPageResponse;
import com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.response.StayResponse;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adaptador de entrada REST de las estancias.
 *
 * <p>No contiene logica de negocio: valida el formato de la peticion, delega en el
 * caso de uso y traduce el resultado. Los errores los traduce a HTTP el
 * {@code CustomizedExceptionAdapter} (IN-36).
 */
@RestController
@RequestMapping("/v1/stays")
public class StayRestAdapter {

    /**
     * Tope maximo del parametro {@code size} del listado (escenarios de ruptura
     * BD). {@code size=1000000} dispararia una consulta enorme y, encima, el
     * N+1 contra vehicle-service que {@code ListStaysUseCase} documenta como
     * aceptable solo porque "size" esta acotado. Valores por encima se recortan;
     * valores invalidos (0 o negativos) siguen llegando a {@code PageRequest.of},
     * que los rechaza con 400 via {@code CustomizedExceptionAdapter}.
     */
    static final int MAX_LIST_SIZE = 100;

    private final CheckInUseCase checkInUseCase;
    private final CheckOutUseCase checkOutUseCase;
    private final GetStayUseCase getStayUseCase;
    private final ListStaysUseCase listStaysUseCase;
    private final StayRestMapper mapper;

    public StayRestAdapter(CheckInUseCase checkInUseCase,
                           CheckOutUseCase checkOutUseCase,
                           GetStayUseCase getStayUseCase,
                           ListStaysUseCase listStaysUseCase,
                           StayRestMapper mapper) {
        this.checkInUseCase = checkInUseCase;
        this.checkOutUseCase = checkOutUseCase;
        this.getStayUseCase = getStayUseCase;
        this.listStaysUseCase = listStaysUseCase;
        this.mapper = mapper;
    }

    /**
     * Entrada de vehiculo (HU-01).
     *
     * <p>Un 201 es acceso autorizado: plaza asignada y barrera abierta (CA3).
     * Cualquier error es acceso denegado con la barrera cerrada: 409 parking
     * completo (RN-01, CA-01) o vehiculo ya dentro (CB-05), 422 vehiculo dado de
     * baja (RN-11).
     */
    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERARIO')")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody StayRequest request) {
        CheckInResultDTO result = checkInUseCase.execute(mapper.toCreateDTO(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toCheckInResponse(result, request.plate));
    }

    @PostMapping("/check-out")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERARIO')")
    public ResponseEntity<CheckOutResponse> checkOut(@Valid @RequestBody StayCheckOutRequest request) {
        CheckOutResultDTO result = checkOutUseCase.execute(mapper.toCheckOutDTO(request));
        return ResponseEntity.ok(mapper.toCheckOutResponse(result, request.plate));
    }

    /** Detalle de una estancia (HU-08). 404 si no existe.
     *
     * NOTA DE SEGURIDAD: Se utiliza @PostAuthorize porque necesitamos el resultado del
     * caso de uso para obtener la matricula asociada y validar si pertenece al usuario (IDOR).
     * Como es un endpoint GET de solo lectura, es seguro realizar la consulta a la base de datos
     * antes de evaluar la autorizacion. NO debe replicarse este patron en endpoints de escritura,
     * ya que la transaccion/modificacion se ejecutaria antes de comprobar el acceso.
     */
    @GetMapping("/{stayId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OPERARIO')")
    @PostAuthorize("hasAnyRole('ADMIN', 'OPERARIO') or (hasRole('USER') and returnObject.body.plate == authentication.token.claims['plate'])")
    public ResponseEntity<StayResponse> getStay(@PathVariable UUID stayId) {
        StayDTO stay = getStayUseCase.execute(stayId);
        return ResponseEntity.ok(mapper.toStayResponse(stay));
    }

    /**
     * Listado paginado de estancias (HU-08, admin). Filtro opcional por
     * {@code status}. El filtro {@code plate} del contrato aun no es funcional:
     * el dominio no guarda la matricula (queda diferido).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERARIO')")
    public ResponseEntity<StayPageResponse> listStays(
            @RequestParam(required = false) StayStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, MAX_LIST_SIZE);
        StayPageDTO result = listStaysUseCase.execute(status, page, cappedSize);
        return ResponseEntity.ok(mapper.toStayPageResponse(result));
    }
}
