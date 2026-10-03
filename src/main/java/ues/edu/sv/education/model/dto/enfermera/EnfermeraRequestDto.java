package ues.edu.sv.education.model.dto.enfermera;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ues.edu.sv.education.model.dto.persona.PersonaRequestDto;

/**
 * Alta de personal de enfermeria por un administrador.
 *
 * ── Por que existe este endpoint ───────────────────────────────────────────
 * El rol ENFERMERA vivia solo en las anotaciones @PreAuthorize y en la tabla
 * `role`: no habia NINGUNA forma de crear una enfermera -ni endpoint, ni
 * data.sql, ni migracion-, asi que era un rol al que nadie podia llegar. Se
 * descubrio al construir signos vitales, que es la primera funcion que de
 * verdad necesita una.
 *
 * ── Por que el alta la hace un ADMIN y no es publica ───────────────────────
 * /auth/register es publico y por eso solo crea cuentas MEDICO sin privilegio
 * real hasta que alguien las habilita. Enfermeria escribe en el expediente
 * clinico -las constantes que el medico lee para diagnosticar-, asi que quien
 * entra al sistema como enfermera tiene que haber sido admitido por alguien
 * que responde por ello, no haberse dado de alta a si mismo.
 *
 * ── Por que trae la cuenta de acceso ───────────────────────────────────────
 * Una enfermera sin con que entrar no sirve de nada, y repartir el alta en
 * tres llamadas (POST /user, POST /user/{id}/role/3 y el alta clinica) deja
 * estados a medias en cuanto una falla: un usuario sin fila en `enfermeras`
 * pasa el hasRole('ENFERMERA') del controlador y despues recibe 403 del
 * servicio, sin que nadie entienda por que.
 */
@Schema(description = "Datos para dar de alta a una enfermera y su cuenta de acceso")
public record EnfermeraRequestDto(

        @Schema(description = "Persona detras de la enfermera. Con personaId se completa una existente; sin el, se crea nueva")
        @NotNull(message = "La persona es obligatoria")
        @Valid
        PersonaRequestDto persona,

        @Schema(
                description = "Numero de registro de la Junta de Vigilancia. Opcional: "
                        + "hay personal en formacion que aun no lo tiene",
                example = "JVPM-ENF-4471",
                nullable = true
        )
        @Size(max = 20, message = "El registro de la junta no puede superar los 20 caracteres")
        String registroJunta,

        @Schema(
                description = "Correo con el que la enfermera INICIA SESION. Distinto de "
                        + "persona.email, que es su correo de contacto",
                example = "marta.guevara@ues.edu.sv"
        )
        @NotBlank(message = "El correo de acceso es obligatorio")
        @Email(message = "El correo de acceso no tiene un formato valido")
        @Size(max = 100, message = "El correo de acceso debe poseer menos de 100 caracteres")
        String emailDeAcceso,

        @Schema(description = "Contrasena inicial. Se guarda cifrada, nunca en claro")
        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
        String password

) {
}
