package ues.edu.sv.education.model.enums;

import lombok.Getter;

@Getter
public enum EventStatusEnums {
    PENDING(1, "pendiente"),
    PROCESSING(2, "procesando"),
    PROCESSED(3, "procesado"),
    FAILED(4, "fallido");

    private final int id;
    private final String descripcion;

    EventStatusEnums(int id, String descripcion) {
        this.id = id;
        this.descripcion = descripcion;
    }
}
