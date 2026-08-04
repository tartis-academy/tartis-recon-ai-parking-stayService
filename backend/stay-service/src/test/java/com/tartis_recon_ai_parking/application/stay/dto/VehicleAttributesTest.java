package com.tartis_recon_ai_parking.application.stay.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleAttributesTest {

    @Test
    @DisplayName("conserva los valores rellenos y les quita los espacios sobrantes")
    void keepsFilledValuesTrimmed() {
        VehicleAttributes attributes = new VehicleAttributes("  Seat ", "Ibiza", " Rojo");

        assertEquals("Seat", attributes.brand());
        assertEquals("Ibiza", attributes.model());
        assertEquals("Rojo", attributes.color());
        assertFalse(attributes.isEmpty());
    }

    @Test
    @DisplayName("el blanco se normaliza a null para que vehicle-service aplique su valor por defecto")
    void normalizesBlankToNull() {
        VehicleAttributes attributes = new VehicleAttributes("", "   ", null);

        assertNull(attributes.brand());
        assertNull(attributes.model());
        assertNull(attributes.color());
        assertTrue(attributes.isEmpty());
    }

    @Test
    @DisplayName("EMPTY equivale a los tres campos ausentes")
    void emptyEqualsAllAbsent() {
        assertEquals(VehicleAttributes.EMPTY, new VehicleAttributes(null, null, null));
        assertEquals(VehicleAttributes.EMPTY, new VehicleAttributes("", "  ", ""));
        assertTrue(VehicleAttributes.EMPTY.isEmpty());
    }

    @Test
    @DisplayName("un solo campo relleno ya no cuenta como vacio")
    void partiallyFilledIsNotEmpty() {
        assertFalse(new VehicleAttributes(null, null, "Rojo").isEmpty());
    }
}
