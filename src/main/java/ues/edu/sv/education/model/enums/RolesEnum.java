package ues.edu.sv.education.model.enums;

import lombok.Getter;

@Getter
public enum RolesEnum {
    ADMIN(1, "ADMIN"),
    DIRECTOR(2, "DIRECTOR"),
    PROFESOR(3, "PROFESOR"),
    ESTUDIANTE(4, "ESTUDIANTE");

    RolesEnum(int id, String name) {
        this.id = id;
        this.name = name;
    }

    private final int id;
    private final String name;

    // Buscar ID por nombre
    public static Integer getIdByName(String name) {
        for (RolesEnum role : values()) {
            if (role.name.equalsIgnoreCase(name)) {
                return role.id;
            }
        }
        return null; // o lanzar excepción
    }

    // Buscar enum por nombre
    public static RolesEnum fromName(String name) {
        for (RolesEnum role : values()) {
            if (role.name.equalsIgnoreCase(name)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Rol no válido: " + name);
    }

    // Authority para Spring Security
    public String getAuthority() {
        return "ROLE_" + this.name;
    }
}
