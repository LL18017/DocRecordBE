package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ues.edu.sv.education.model.entity.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-05, criterios 1 (el listado muestra especialidad y estado) y 3 (activar y
 * desactivar una cuenta).
 *
 * El criterio 3 no se da por cumplido comprobando que el campo cambio en la
 * respuesta: lo que pide es que la cuenta "deje de poder iniciar sesion", asi
 * que se comprueba intentando entrar de verdad.
 */
class EstadoDeUsuarioIT extends PruebaClinica {

    /**
     * Todos los usuarios, recorriendo las paginas hasta agotarlas.
     *
     * Pedir una sola pagina no sirve: la suite comparte la base y no la limpia
     * entre clases -- las altas COMMITEAN, porque MockMvc tiene que ver al
     * usuario guardado para poder autenticarlo --, asi que al llegar aqui hay
     * cientos de filas. La cuenta recien creada aparecia en la primera pagina
     * cuando la prueba corria sola y se caia fuera al correr la suite entera,
     * que es la peor forma de fallar: parece intermitente y no lo es.
     */
    private JsonNode listarUsuarios(String tokenAdmin) throws Exception {
        com.fasterxml.jackson.databind.node.ArrayNode todos = json.createArrayNode();
        final int TAMANO = 200;

        for (int pagina = 0; pagina < 50; pagina++) {
            String cuerpo = mockMvc.perform(get("/user/all")
                            .param("inicio", String.valueOf(pagina))
                            .param("fin", String.valueOf(TAMANO))
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode lote = json.readTree(cuerpo);
            todos.addAll((com.fasterxml.jackson.databind.node.ArrayNode) lote);
            if (lote.size() < TAMANO) break;
        }
        return todos;
    }

    private JsonNode buscarEnListado(JsonNode listado, String correo) {
        for (JsonNode u : listado) {
            if (correo.equalsIgnoreCase(u.get("email").asText())) return u;
        }
        throw new AssertionError("el usuario " + correo + " no aparece en /user/all");
    }

    private int intentarIniciarSesion(String correo) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andReturn().getResponse().getStatus();
    }

    /** Registra un medico por la API publica, lo habilita y devuelve su correo. */
    private String crearMedicoHabilitado() throws Exception {
        String correo = correoUnico("estado.medico");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Medico","apellidos":"Con Especialidad",
                                 "email":"%s","password":"%s","especialidadId":1}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful());

        User usuario = usuarios.findByEmailIgnoreCase(correo)
                .orElseThrow(() -> new AssertionError("el registro no creo el usuario"));
        usuario.setEnabled(true);
        usuarios.saveAndFlush(usuario);
        return correo;
    }

    private int idDe(String correo) {
        return usuarios.findByEmailIgnoreCase(correo)
                .orElseThrow(() -> new AssertionError("no existe " + correo))
                .getUserID();
    }

    /**
     * Un administrador con un correo que la prueba ELIGE, y su token.
     *
     * PruebaClinica.tokenDeAdminQueNoEjerce genera el correo por dentro, asi
     * que quien lo llama no sabe a quien le pertenece el token. Para probar que
     * un admin no puede desactivarse a si mismo hace falta justamente eso:
     * conocer el correo para poder resolver su propio id.
     */
    private String tokenDeAdminConCorreo(String correo) throws Exception {
        ues.edu.sv.education.model.entity.Persona persona = personas.saveAndFlush(
                ues.edu.sv.education.model.entity.Persona.builder()
                        .nombres("Admin").apellidos("De Estados")
                        .build());

        usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correo)
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new java.util.HashSet<>(java.util.Set.of(
                        roles.getReferenceById(
                                ues.edu.sv.education.model.enums.RolesEnum.ADMIN.getId()))))
                .build());

        String cuerpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(cuerpo).get("token").asText();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 1 · el listado muestra especialidad y estado
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el listado trae la especialidad del medico y el estado de la cuenta")
    void elListadoTraeEspecialidadYEstado() throws Exception {
        String correoMedico = crearMedicoHabilitado();
        String tokenAdmin = tokenDeAdminQueNoEjerce();

        JsonNode fila = buscarEnListado(listarUsuarios(tokenAdmin), correoMedico);

        assertTrue(fila.has("especialidad"), "la clave debe venir, aunque fuera null");
        assertFalse(fila.get("especialidad").isNull(), "un medico si tiene especialidad");
        assertEquals("Medicina General", fila.get("especialidad").asText());

        assertTrue(fila.has("activo"));
        assertTrue(fila.get("activo").asBoolean(), "la cuenta se habilito antes de listar");
    }

    @Test
    @DisplayName("quien no ejerce la medicina sale sin especialidad, no con una inventada")
    void sinEspecialidadCuandoNoAplica() throws Exception {
        // No es un dato que falte: es que la pregunta no le corresponde. La
        // clave viene presente con valor null para que la pantalla pinte un
        // guion, y no ausente -- el frontend la tipa como string | null.
        String tokenAdmin = tokenDeAdminQueNoEjerce();
        String correoEnfermera = correoUnico("estado.enfermera.listado");

        mockMvc.perform(post("/user")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","userName":"Enfermera Del Listado","password":"%s"}
                                """.formatted(correoEnfermera, CLAVE)))
                .andExpect(status().isCreated());

        JsonNode fila = buscarEnListado(listarUsuarios(tokenAdmin), correoEnfermera);
        assertTrue(fila.has("especialidad"), "la clave debe estar presente");
        assertTrue(fila.get("especialidad").isNull(), "y valer null, no una cadena vacia");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 3 · activar y desactivar
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("desactivar una cuenta le impide iniciar sesion; reactivarla se lo devuelve")
    void desactivarImpideEntrar() throws Exception {
        String correoMedico = crearMedicoHabilitado();
        String tokenAdmin = tokenDeAdminQueNoEjerce();

        assertEquals(200, intentarIniciarSesion(correoMedico),
                "de entrada la cuenta debe poder entrar");

        String cuerpo = mockMvc.perform(patch("/user/{id}/estado", idDe(correoMedico))
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(json.readTree(cuerpo).get("activo").asBoolean());

        // Lo que el criterio pide de verdad: que no pueda entrar.
        assertTrue(intentarIniciarSesion(correoMedico) >= 400,
                "una cuenta desactivada no debe poder iniciar sesion");

        mockMvc.perform(patch("/user/{id}/estado", idDe(correoMedico))
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true}"))
                .andExpect(status().isOk());

        assertEquals(200, intentarIniciarSesion(correoMedico),
                "reactivarla debe devolverle el acceso");
    }

    @Test
    @DisplayName("desactivar no borra nada: el usuario sigue en el listado")
    void desactivarNoBorra() throws Exception {
        // "Deja de poder iniciar sesion, PERO sus registros clinicos anteriores
        // siguen existiendo". Si desactivar borrara la cuenta, las consultas
        // que firmo se quedarian sin firmante.
        String correoMedico = crearMedicoHabilitado();
        String tokenAdmin = tokenDeAdminQueNoEjerce();

        mockMvc.perform(patch("/user/{id}/estado", idDe(correoMedico))
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk());

        JsonNode fila = buscarEnListado(listarUsuarios(tokenAdmin), correoMedico);
        assertNotNull(fila, "el usuario debe seguir existiendo");
        assertFalse(fila.get("activo").asBoolean(), "solo cambio su estado");
        assertEquals("Medicina General", fila.get("especialidad").asText(),
                "y conserva el resto de sus datos");
    }

    @Test
    @DisplayName("un administrador no puede desactivarse a si mismo")
    void nadieSeApagaSolo() throws Exception {
        // Misma guarda que en quitarRole y por lo mismo: dejar el sistema sin
        // nadie que pueda administrarlo no tiene arreglo desde la aplicacion.
        String correoAdmin = correoUnico("estado.admin.solo");
        String tokenAdmin = tokenDeAdminConCorreo(correoAdmin);

        mockMvc.perform(patch("/user/{id}/estado", idDe(correoAdmin))
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isConflict());

        assertEquals(200, intentarIniciarSesion(correoAdmin),
                "el 409 tiene que haber cortado antes de tocar nada");
    }

    @Test
    @DisplayName("un medico no puede cambiar el estado de nadie")
    void soloElAdministradorCambiaEstados() throws Exception {
        String correoMedico = crearMedicoHabilitado();
        String tokenMedico = tokenDeMedico();

        mockMvc.perform(patch("/user/{id}/estado", idDe(correoMedico))
                        .header("Authorization", "Bearer " + tokenMedico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isForbidden());

        assertEquals(200, intentarIniciarSesion(correoMedico),
                "el 403 tiene que haber cortado antes de tocar nada");
    }
}
