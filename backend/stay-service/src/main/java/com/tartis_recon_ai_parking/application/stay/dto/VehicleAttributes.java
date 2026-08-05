package com.tartis_recon_ai_parking.application.stay.dto;

// Blanco se normaliza a null: vehicle-service rechaza con 400 un valor en blanco
// pero sustituye los nulos por "Desconocido". Un opcional vacio es un campo sin
// rellenar, no un dato invalido.
public record VehicleAttributes(String brand, String model, String color) {

    public static final VehicleAttributes EMPTY = new VehicleAttributes(null, null, null);

    public VehicleAttributes {
        brand = blankToNull(brand);
        model = blankToNull(model);
        color = blankToNull(color);
    }

    public boolean isEmpty() {
        return brand == null && model == null && color == null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
