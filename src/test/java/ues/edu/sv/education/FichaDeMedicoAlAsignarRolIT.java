package ues.edu.sv.education;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.MedicoRepository;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Defecto de HU-05 (DRS-78): dar el rol MEDICO desde Usuarios y Roles no dejaba
 * la cuenta lista para atender.
 *
 * El rol pasaba el @PreAuthorize, pero consultas, recetas y antecedentes exigen
 * ademas la fila en `medicos` (MedicoAutenticado). La cuenta veia los botones y
 * recibia 403 al guardar.
 *
 * Desde HU-02 la ficha se registra en Medicos (FichaDeMedicoIT) y Usuarios y
 * Roles ya no la crea: dar MEDICO a quien no la tiene responde 409.
 */
class FichaDeMedicoAlAsignarRolIT extends PruebaClinica {

    @Autowired private MedicoRepository medicos;

    private String admin;
    private long especialidadId;
    private int sede;

    @BeforeEach
    void prepararAdministradorYCatalogo() throws Exception {
        admin = tokenDeAdministrador();
        String cuerpo = mockMvc.perform(get("/especialidades"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        especialidadId = json.readTree(cuerpo).get(0).get("especialidadId").asLong();
        sede = crearSede(admin);
    }

    private record Cuenta(User user, String correo) {}

    /** Una cuenta habilitada, con los roles que se indiquen y sin ficha de medico. */
    private Cuenta cuentaSinFicha(RolesEnum... rolesIniciales) {
        String correo = correoUnico("ficha.medico");
        Persona persona = personas.saveAndFlush(Persona.builder()
                .nombres("Cuenta").apellidos("Sin Ficha")
                .build());
        Set<ues.edu.sv.education.model.entity.Role> rolesDeLaCuenta = new HashSet<>();
        for (RolesEnum rol : rolesIniciales) {
            rolesDeLaCuenta.add(roles.getReferenceById(rol.getId()));
        }
        User user = usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correo)
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(rolesDeLaCuenta)
                .build());
        return new Cuenta(user, correo);
    }

    private String rutaDeRol(User user, RolesEnum rol) {
        return "/user/" + user.getUserID() + "/role/" + rol.getId();
    }

    /** Registra la ficha de medico de esa cuenta, como se hace en Medicos. */
    private void registrarFicha(Cuenta cuenta) throws Exception {
        mockMvc.perform(post("/medicos")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Cuenta","apellidos":"Con Ficha","especialidadId":%d,
                                 "registroJunta":"%s","userId":%d}
                                """.formatted(especialidadId, juntaUnica(), cuenta.user().getUserID())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-02: dar MEDICO a quien no tiene ficha da 409 y no deja el rol a medias")
    void sinFichaNoSeDaElRol() throws Exception {
        Cuenta cuenta = cuentaSinFicha();

        String cuerpo = mockMvc.perform(post(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .param("clinicaId", String.valueOf(sede))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(cuerpo.contains("Médicos"), "debe decir donde se registra la ficha: " + cuerpo);

        User releido = usuarios.findById(cuenta.user().getUserID()).orElseThrow();
        assertTrue(releido.getRoles().isEmpty());
        assertTrue(medicos.findById(cuenta.user().getPersona().getPersonaId()).isEmpty(),
                "Usuarios y Roles ya no crea fichas de medico");
    }

    @Test
    @DisplayName("con la ficha registrada, dar MEDICO deja la cuenta lista para registrar un acto clinico")
    void conFichaElRolDejaLaCuentaListaParaAtender() throws Exception {
        Cuenta cuenta = cuentaSinFicha(RolesEnum.ADMIN);
        registrarFicha(cuenta);

        String cuerpo = mockMvc.perform(post(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .param("clinicaId", String.valueOf(sede))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(json.readTree(cuerpo).get("especialidad").isNull());

        String token = iniciarSesion(cuenta.correo());
        long paciente = crearPaciente(token);
        mockMvc.perform(post("/antecedentes-patologicos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"tipo":"CIRUGIA","descripcion":"Hernioplastia","fecha":"2020-05-05","estado":"RESUELTO"}
                                """.formatted(paciente)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("quitar MEDICO marca la ficha inactiva, no la borra; volver a darlo la reactiva")
    void quitarYVolverADar() throws Exception {
        Cuenta cuenta = cuentaSinFicha();
        Long personaId = cuenta.user().getPersona().getPersonaId();
        registrarFicha(cuenta);

        mockMvc.perform(post(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .param("clinicaId", String.valueOf(sede))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        assertFalse(medicos.findById(personaId).orElseThrow().isActivo());

        mockMvc.perform(post(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        assertTrue(medicos.findById(personaId).orElseThrow().isActivo());
        assertEquals(especialidadId,
                medicos.findById(personaId).orElseThrow().getEspecialidad().getEspecialidadId());
    }
}
