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
 * ademas la fila en `medicos` (MedicoAutenticado), y esa fila solo la creaba el
 * registro publico. La cuenta veia los botones y recibia 403 al guardar.
 */
class FichaDeMedicoAlAsignarRolIT extends PruebaClinica {

    @Autowired private MedicoRepository medicos;

    private String admin;
    private long especialidadId;

    @BeforeEach
    void prepararAdministradorYCatalogo() throws Exception {
        admin = tokenDeAdministrador();
        String cuerpo = mockMvc.perform(get("/especialidades"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        especialidadId = json.readTree(cuerpo).get(0).get("especialidadId").asLong();
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

    private String iniciarSesion(String correo) throws Exception {
        String cuerpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asText();
    }

    private String rutaDeRol(User user, RolesEnum rol) {
        return "/user/" + user.getUserID() + "/role/" + rol.getId();
    }

    @Test
    @DisplayName("dar MEDICO con especialidad crea la ficha, y la cuenta ya puede registrar un acto clinico")
    void darMedicoDejaLaCuentaListaParaAtender() throws Exception {
        Cuenta cuenta = cuentaSinFicha(RolesEnum.ADMIN);

        String cuerpo = mockMvc.perform(post(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .param("especialidadId", String.valueOf(especialidadId))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(json.readTree(cuerpo).get("especialidad").isNull());
        assertTrue(medicos.findById(cuenta.user().getPersona().getPersonaId()).isPresent());

        // La prueba de fondo: antes de esto, este POST respondia 403.
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
    @DisplayName("dar MEDICO sin especialidad a quien no tiene ficha da 400 y no deja el rol a medias")
    void sinEspecialidadNoSeDaElRol() throws Exception {
        Cuenta cuenta = cuentaSinFicha();

        mockMvc.perform(post(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());

        User releido = usuarios.findById(cuenta.user().getUserID()).orElseThrow();
        assertTrue(releido.getRoles().isEmpty());
    }

    @Test
    @DisplayName("una cuenta que ya tenia MEDICO sin ficha se repara volviendo a darlo con la especialidad")
    void unaCuentaRotaSeRepara() throws Exception {
        Cuenta cuenta = cuentaSinFicha(RolesEnum.MEDICO);

        // Sin especialidad sigue siendo lo de siempre: ya tiene el rol.
        mockMvc.perform(post(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());

        mockMvc.perform(post(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .param("especialidadId", String.valueOf(especialidadId))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        assertTrue(medicos.findById(cuenta.user().getPersona().getPersonaId()).isPresent());
    }

    @Test
    @DisplayName("quitar MEDICO marca la ficha inactiva, no la borra; volver a darlo la reactiva")
    void quitarYVolverADar() throws Exception {
        Cuenta cuenta = cuentaSinFicha();
        Long personaId = cuenta.user().getPersona().getPersonaId();

        mockMvc.perform(post(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .param("especialidadId", String.valueOf(especialidadId))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        assertFalse(medicos.findById(personaId).orElseThrow().isActivo());

        // Ya tiene ficha: no hace falta repetir la especialidad.
        mockMvc.perform(post(rutaDeRol(cuenta.user(), RolesEnum.MEDICO))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        assertTrue(medicos.findById(personaId).orElseThrow().isActivo());
        assertEquals(especialidadId,
                medicos.findById(personaId).orElseThrow().getEspecialidad().getEspecialidadId());
    }
}
