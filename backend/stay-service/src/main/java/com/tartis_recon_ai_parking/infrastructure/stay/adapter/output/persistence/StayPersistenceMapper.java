package com.tartis_recon_ai_parking.infrastructure.stay.adapter.output.persistence;

import com.tartis_recon_ai_parking.domain.stay.Stay;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Traduccion entre el agregado de dominio y la fila de la tabla.
 *
 * <p><b>Por que {@code unmappedTargetPolicy = ERROR}.</b> Desde que existe el
 * control de concurrencia optimista, este mapper tiene una responsabilidad de la
 * que depende la correccion: el token de {@code version} tiene que viajar en los
 * dos sentidos sin perderse. Si {@code toEntity} lo dejara a null, cada guardado
 * de una estancia ya existente se interpretaria como un alta y acabaria en un
 * INSERT contra una clave primaria que ya existe: <b>todos los check-out
 * romperian</b>.
 *
 * <p>Por defecto, una propiedad de destino sin mapear en MapStruct genera solo
 * un <em>warning</em>. Es decir: ese fallo habria pasado la compilacion en verde
 * y habria aparecido en ejecucion. Con la politica en {@code ERROR}, si alguien
 * anade un campo a {@link StayEntity} y no lo mapea, la build no compila. Es la
 * diferencia entre enterarse en CI y enterarse en la barrera de salida.
 *
 * <p>{@code version} se mapea sola, por coincidencia de nombre entre
 * {@code Stay.getVersion()} y {@code StayEntity.setVersion(...)}. No lleva
 * {@code @Mapping} explicito porque no hace falta, pero la politica de arriba
 * garantiza que si algun dia dejara de resolverse nos enterariamos.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StayPersistenceMapper {

    @Mapping(source = "id", target = "uniqueId")
    StayEntity toEntity(Stay stay);

    /**
     * Escrito a mano y no generado: la reconstruccion pasa por la factoria del
     * dominio, que valida los invariantes de la estancia, en vez de rellenar
     * campos con setters saltandose esas comprobaciones.
     */
    default Stay toDomain(StayEntity entity) {
        if (entity == null) {
            return null;
        }
        return Stay.restore(
                entity.getUniqueId(),
                entity.getVehicleId(),
                entity.getVehicleType(),
                entity.getSpotId(),
                entity.getTariffId(),
                entity.getCheckIn(),
                entity.getCheckOut(),
                entity.getTotalAmount(),
                entity.getStatus(),
                // Sin esto, la version se quedaria en la fila y no llegaria al
                // agregado: el siguiente UPDATE iria sin WHERE version = ? y
                // pisaria cualquier cambio concurrente.
                entity.getVersion()
        );
    }
}
