package com.tartis_recon_ai_parking.infrastructure.stay.adapter.input.rest.dto.request;

import java.util.UUID;

/**
 * Cuerpo de la peticion de check-out (POST /v1/stays/check-out).
 * Corresponde al schema {@code CheckOutRequest} del openapi.yml.
 *
 * <p>Debe llegar {@code plate} O {@code entryTicketId} (al menos uno). La regla
 * "al menos uno" no se puede expresar con una sola anotacion de Bean Validation,
 * asi que la valida el caso de uso / adaptador REST.
 */
public class StayCheckOutRequest {

    public String plate;

    public UUID entryTicketId;
}
