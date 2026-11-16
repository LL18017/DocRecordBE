package ues.edu.sv.education.model.dto.User;

import java.time.LocalDate;

/**
 * Lo que acompaña a un rol cuando darlo crea algo más que el rol.
 *
 * Con PACIENTE, si la persona aún no tiene expediente: fecha de nacimiento,
 * sexo y DUI para abrirlo. Con MEDICO, si la persona aún no tiene ficha:
 * especialidad, número de junta y teléfono para registrarla. En los dos
 * casos, nombres y apellidos confirman o corrigen el nombre de la persona,
 * que es la misma de la cuenta: así el expediente o la ficha no heredan un
 * nombre puesto de relleno, como el «Administrador Del Sistema» del primer
 * administrador. Con los demás roles, o si ya había expediente o ficha, todo
 * se ignora.
 */
public record DatosDelRolDto(
        LocalDate fechaNacimiento,
        String sexo,
        String dui,
        String nombres,
        String apellidos,
        Long especialidadId,
        String registroJunta,
        String telefono
) {
    public static final DatosDelRolDto NINGUNO =
            new DatosDelRolDto(null, null, null, null, null, null, null, null);
}
