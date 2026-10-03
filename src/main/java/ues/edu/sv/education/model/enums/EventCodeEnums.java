package ues.edu.sv.education.model.enums;

import lombok.Getter;

@Getter
public enum EventCodeEnums {
    LOGIN(1, "Inicio de sesión"),
    PASSWORD_CHANGED(2, "Cambio de contraseña"),
    USER_REGISTERED(3, "Registro de usuario"),
    APPOINTMENT_CREATED(4, "Registro de cita"),
    APPOINTMENT_CANCELLED(5, "Cancelación de cita"),
    CONFIRM_ACOUNT(6, "confirmacion de cuentas");
    private final int id;
    private final String description;

    EventCodeEnums(int id, String description) {
        this.id = id;
        this.description = description;
    }

    public static EventCodeEnums fromId(int id) {
        for (EventCodeEnums e : values()) {
            if (e.id == id) return e;
        }
        throw new IllegalArgumentException("ID de evento inválido: " + id);
    }
}