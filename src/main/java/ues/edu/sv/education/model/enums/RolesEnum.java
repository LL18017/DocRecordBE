package ues.edu.sv.education.model.enums;

import lombok.Getter;

@Getter
public enum RolesEnum {
    ADMIN(1, "ADMIN"),
    MEDICO(2, "MEDICO"),
    ENFERMERA(3, "ENFERMERA"),
    PACIENTE(4, "PACIENTE");

    RolesEnum(int id, String name) {
        this.id = id;
        this.name = name;
    }

    private final int id;
    private final String name;

    /**
     * Id del rol que se llame asi. Lanza si no existe ninguno.
     *
     * Aqui habia un {@code return null} con el comentario "o lanzar excepcion",
     * es decir una decision a medias, y esa indecision se pagaba lejos de este
     * archivo: RoleMapper.toDto llamaba a este metodo sin comprobar el
     * resultado, el {@code id: null} salia en la respuesta de /roles/all y de
     * /auth/login, y el frontend termino blindandose contra el.
     *
     * Se toma la decision contraria por dos razones.
     *
     * La primera es que NINGUN llamador queria el null. Los tres que hay estan
     * en RoleMapper y los tres lo meten tal cual en un RoleDto o en un Role.
     * Un valor de retorno que nadie interpreta no comunica nada: solo aplaza el
     * fallo hasta un sitio donde ya no se sabe de donde vino.
     *
     * La segunda es que, quitado el null, este metodo era literalmente
     * {@code fromName(name).getId()}. Mantener dos busquedas identicas que solo
     * se diferencian en como fallan es lo que permitia elegir la comoda sin
     * pensarlo; ahora hay una sola semantica y esta escrita una sola vez.
     *
     * Que esto lance no puede romper una peticion legitima: desde la migracion
     * V7 la tabla `role` tiene un CHECK que la limita a los nombres de este
     * enum, asi que un nombre desconocido ya no puede llegar desde la base. Si
     * alguna vez se lanza sera por un defecto de programacion -- alguien
     * pasando una cadena que no es un rol -- y eso debe verse, no colarse como
     * un null.
     *
     * @throws IllegalArgumentException si el nombre no es de este catalogo.
     */
    public static Integer getIdByName(String name) {
        return fromName(name).getId();
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
