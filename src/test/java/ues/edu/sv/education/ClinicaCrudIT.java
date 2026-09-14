package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.ClinicaRepository;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD de clinicas a traves de la API real.
 *
 * Nace de una incoherencia concreta: las columnas clinicas.latitud y
 * clinicas.longitud admiten NULL, pero ClinicasRequestDto las exigia como
 * obligatorias. Se podia LEER una clinica sin ubicacion y no se podia GUARDAR
 * una.
 * En la practica eso obligaba a inventar coordenadas para dar de alta la
 * clinica, y un punto falso en un mapa es peor que un punto ausente.
 *
 * Se prueba por HTTP y no llamando al servicio, por lo mismo que PacienteCrudIT:
 * lo que interesa es el contrato tal como lo ve el frontend, codigos de estado
 * incluidos. Que el servicio acepte null no sirve de nada si la validacion del
 * DTO devuelve 400 antes de llegar a el.
 */
@AutoConfigureMockMvc
class ClinicaCrudIT extends PruebaDeIntegracion {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository usuarios;
    @Autowired private ClinicaRepository clinicas;
    @Autowired private PersonaRepository personas;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder encoder;

    private final ObjectMapper json = new ObjectMapper();

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CLAVE = "Docrecord2026!";

    private String token;

    @BeforeEach
    void autenticarUnMedico() throws Exception {
        token = registrarMedicoYObtenerToken();
    }

    /**
     * Registra un medico nuevo, lo habilita e inicia sesion. Devuelve su token.
     *
     * Cada llamada usa un correo distinto porque el registro COMMITEA: reutilizar
     * uno fijo haria que la segunda llamada chocara con 409.
     */
    private String registrarMedicoYObtenerToken() throws Exception {
        String correo = "crud.clinica." + CONTADOR.incrementAndGet() + "@ues.edu.sv";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Medico","apellidos":"De Clinicas",
                                 "email":"%s","password":"%s","especialidadId":1}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful());

        User usuario = usuarios.findByEmailIgnoreCase(correo)
                .orElseThrow(() -> new AssertionError("el registro no creo el usuario"));
        usuario.setEnabled(true);
        usuarios.saveAndFlush(usuario);

        String cuerpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(cuerpo).get("token").asText();
    }

    /**
     * Completa un cuerpo de clinica con lo que HU-26 volvio obligatorio.
     *
     * Estas pruebas se escribieron cuando una clinica era un nombre y unas
     * coordenadas. El criterio 1 de HU-26 anadio departamento, municipio,
     * direccion, telefono y horario. En vez de reescribir veinte literales
     * -- y desde luego en vez de aflojar el DTO, que seria borrar el criterio
     * -- se inyecta aqui lo que falte.
     *
     * El municipio es DISTINTO en cada llamada a proposito: el criterio 4
     * prohibe repetir nombre dentro de un mismo municipio, y varias de estas
     * pruebas reutilizan el mismo nombre de clinica. Con un municipio fijo
     * chocarian contra esa unicidad por una razon que no es la que prueban.
     */
    private String completar(String cuerpoJson) {
        if (cuerpoJson.contains("\"municipio\"")) return cuerpoJson;

        String extra = ",\"departamento\":\"Santa Ana\""
                + ",\"municipio\":\"Municipio " + CONTADOR.incrementAndGet() + "\""
                + ",\"direccion\":\"Calle Principal\""
                + ",\"telefono\":\"2440-0000\""
                + ",\"horario\":\"Lunes a viernes, 8:00 a 16:00\"";

        return cuerpoJson.trim().replaceFirst("\\}\\s*$", extra + "}");
    }

    private JsonNode crearClinica(String tokenDelDuenio, String cuerpoJson) throws Exception {
        String respuesta = mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + tokenDelDuenio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar(cuerpoJson)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta);
    }

    /** Busca una clinica dentro de GET /clinics/mias, para leerla ya persistida. */
    private JsonNode leerDeMisClinicas(String tokenDelDuenio, int clinicaId) throws Exception {
        String cuerpo = mockMvc.perform(get("/clinics/mias")
                        .header("Authorization", "Bearer " + tokenDelDuenio))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (JsonNode clinica : json.readTree(cuerpo)) {
            if (clinica.get("clinicaId").asInt() == clinicaId) return clinica;
        }
        throw new AssertionError("la clinica " + clinicaId + " no aparece en /clinics/mias");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Coordenadas opcionales
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("crear una clinica sin coordenadas responde 201 y las devuelve null")
    void crearSinCoordenadasDevuelveNull() throws Exception {
        // El caso que estaba roto: la columna admite NULL, el DTO lo prohibia.
        JsonNode creada = crearClinica(token, "{\"name\":\"Clinica Sin GPS\"}");

        assertTrue(creada.get("latitud").isNull(), "la latitud debio quedar nula");
        assertTrue(creada.get("longitud").isNull(), "la longitud debio quedar nula");

        // Y no solo en la respuesta: tambien al releerla ya guardada. La forma de
        // la respuesta no cambia (el frontend la tipa como number | null), asi
        // que las claves deben venir presentes con valor null, no ausentes.
        JsonNode guardada = leerDeMisClinicas(token, creada.get("clinicaId").asInt());
        assertTrue(guardada.has("latitud") && guardada.get("latitud").isNull());
        assertTrue(guardada.has("longitud") && guardada.get("longitud").isNull());
        assertEquals("Clinica Sin GPS", guardada.get("name").asText());
    }

    @Test
    @DisplayName("crear una clinica con coordenadas responde 201 y las guarda")
    void crearConCoordenadasLasGuarda() throws Exception {
        // Contrapeso del anterior: hacer opcional la ubicacion no puede acabar
        // ignorandola. Sin esta prueba, un servicio que nunca guardara las
        // coordenadas seguiria pasando la de arriba.
        JsonNode creada = crearClinica(token,
                "{\"name\":\"Clinica Con GPS\",\"latitud\":13.9942,\"longitud\":-89.5597}");

        assertEquals(13.9942, creada.get("latitud").asDouble(), 1e-9);
        assertEquals(-89.5597, creada.get("longitud").asDouble(), 1e-9);

        JsonNode guardada = leerDeMisClinicas(token, creada.get("clinicaId").asInt());
        assertEquals(13.9942, guardada.get("latitud").asDouble(), 1e-9);
        assertEquals(-89.5597, guardada.get("longitud").asDouble(), 1e-9);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Actualizar: completar nunca destruye
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("actualizar solo el nombre no borra las coordenadas existentes")
    void actualizarSoloElNombreNoBorraLasCoordenadas() throws Exception {
        // Misma regla que PacienteService.actualizar. Un PUT que solo corrige el
        // nombre no puede tirar una ubicacion que costo salir a tomar.
        JsonNode creada = crearClinica(token,
                "{\"name\":\"Clinica Mal Escrita\",\"latitud\":13.7,\"longitud\":-89.2}");
        int clinicaId = creada.get("clinicaId").asInt();

        String cuerpo = mockMvc.perform(put("/clinics/{id}", clinicaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar("{\"name\":\"Clinica Bien Escrita\"}")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode actualizada = json.readTree(cuerpo);
        assertEquals("Clinica Bien Escrita", actualizada.get("name").asText(), "el nombre debio cambiar");
        assertEquals(13.7, actualizada.get("latitud").asDouble(), 1e-9,
                "la latitud no venia en la peticion y no debio borrarse");
        assertEquals(-89.2, actualizada.get("longitud").asDouble(), 1e-9,
                "la longitud no venia en la peticion y no debio borrarse");

        JsonNode guardada = leerDeMisClinicas(token, clinicaId);
        assertEquals(13.7, guardada.get("latitud").asDouble(), 1e-9);
        assertEquals(-89.2, guardada.get("longitud").asDouble(), 1e-9);
    }

    @Test
    @DisplayName("actualizar con coordenadas nuevas si las cambia")
    void actualizarConCoordenadasNuevasSiLasCambia() throws Exception {
        // Contrapeso de la anterior: "no borrar" no puede degenerar en "no dejar
        // corregir nunca". Un servicio que ignorara siempre las coordenadas del
        // PUT pasaria la prueba de arriba y fallaria esta.
        JsonNode creada = crearClinica(token,
                "{\"name\":\"Clinica Que Se Mide\",\"latitud\":13.0,\"longitud\":-89.0}");
        int clinicaId = creada.get("clinicaId").asInt();

        String cuerpo = mockMvc.perform(put("/clinics/{id}", clinicaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar("{\"name\":\"Clinica Que Se Mide\",\"latitud\":14.5,\"longitud\":-88.1}")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode actualizada = json.readTree(cuerpo);
        assertEquals(14.5, actualizada.get("latitud").asDouble(), 1e-9);
        assertEquals(-88.1, actualizada.get("longitud").asDouble(), 1e-9);
    }

    @Test
    @DisplayName("una clinica sin ubicacion puede completarse despues")
    void unaClinicaSinUbicacionPuedeCompletarseDespues() throws Exception {
        // Es el flujo que motiva todo el cambio: se registra la clinica hoy y
        // alguien va a tomarle el GPS la semana que viene.
        JsonNode creada = crearClinica(token, "{\"name\":\"Clinica Por Ubicar\"}");
        int clinicaId = creada.get("clinicaId").asInt();

        String cuerpo = mockMvc.perform(put("/clinics/{id}", clinicaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar("{\"name\":\"Clinica Por Ubicar\",\"latitud\":13.4,\"longitud\":-88.9}")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode actualizada = json.readTree(cuerpo);
        assertEquals(13.4, actualizada.get("latitud").asDouble(), 1e-9);
        assertEquals(-88.9, actualizada.get("longitud").asDouble(), 1e-9);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Opcional no es "cualquier cosa": el rango sigue vigente
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una coordenada fuera de rango se rechaza con 400")
    void unaCoordenadaFueraDeRangoSeRechaza() throws Exception {
        // Quitar el @NotNull no puede quitar tambien el control de rango: un
        // punto en la latitud 91 no existe en la Tierra, y guardarlo produce un
        // mapa que miente.
        rechazaAlCrear("{\"name\":\"Clinica Imposible\",\"latitud\":91.0,\"longitud\":-89.0}");
        rechazaAlCrear("{\"name\":\"Clinica Imposible\",\"latitud\":-90.5,\"longitud\":-89.0}");
        rechazaAlCrear("{\"name\":\"Clinica Imposible\",\"latitud\":13.7,\"longitud\":180.5}");
        rechazaAlCrear("{\"name\":\"Clinica Imposible\",\"latitud\":13.7,\"longitud\":-181.0}");

        // Desde HU-26 el rango ya no es el planeta entero sino El Salvador, asi
        // que una coordenada polar -- perfectamente valida en la Tierra, y a
        // 8.000 km de la red nacional -- tambien se rechaza. El caso que motivo
        // el cambio esta en ClinicaGeorreferenciadaIT: latitud y longitud
        // invertidas, que con el rango viejo pasaban sin que nadie lo notara.
        rechazaAlCrear("{\"name\":\"Clinica Polar\",\"latitud\":-90.0,\"longitud\":180.0}");

        // Los limites exactos DEL TERRITORIO si son validos.
        crearClinica(token, "{\"name\":\"Clinica Limite Sur\",\"latitud\":13.0,\"longitud\":-90.2}");
        crearClinica(token, "{\"name\":\"Clinica Limite Norte\",\"latitud\":14.5,\"longitud\":-87.6}");

        // Y la regla tambien vale al editar, no solo al crear.
        JsonNode creada = crearClinica(token, "{\"name\":\"Clinica Valida\",\"latitud\":13.7,\"longitud\":-89.2}");
        mockMvc.perform(put("/clinics/{id}", creada.get("clinicaId").asInt())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar("{\"name\":\"Clinica Valida\",\"latitud\":999.0,\"longitud\":-89.2}")))
                .andExpect(status().isBadRequest());
    }

    private void rechazaAlCrear(String cuerpoJson) throws Exception {
        // Se completa igual que al crear: si el cuerpo llegara incompleto, el
        // 400 saldria por los campos que faltan y la prueba pasaria sin haber
        // comprobado nada sobre el rango de la coordenada.
        mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar(cuerpoJson)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el nombre sigue siendo obligatorio")
    void elNombreSigueSiendoObligatorio() throws Exception {
        // Aflojar la validacion de las coordenadas no debe aflojar la del nombre:
        // la columna clinicas.name es NOT NULL y una clinica sin nombre no se
        // puede ni buscar ni mostrar.
        mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitud\":13.7,\"longitud\":-89.2}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar("{\"name\":\"   \"}")))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Autorizacion
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("sin token no se puede crear, editar ni borrar una clinica")
    void sinTokenNoSePuedeOperarClinicas() throws Exception {
        JsonNode creada = crearClinica(token, "{\"name\":\"Clinica Protegida\"}");
        int clinicaId = creada.get("clinicaId").asInt();

        mockMvc.perform(post("/clinics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar("{\"name\":\"Clinica Intrusa\"}")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/clinics/{id}", clinicaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar("{\"name\":\"Renombrada Sin Permiso\"}")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/clinics/{id}", clinicaId))
                .andExpect(status().isUnauthorized());

        // El 401 tiene que haber cortado antes de tocar nada.
        assertTrue(clinicas.findById(clinicaId).isPresent(), "la clinica no debio borrarse");
        assertEquals("Clinica Protegida", clinicas.findById(clinicaId).get().getName());
    }

    @Test
    @DisplayName("un medico no puede editar ni borrar la clinica de otro")
    void unMedicoNoTocaLaClinicaDeOtro() throws Exception {
        // La identidad del dueño sale del JWT, no del cuerpo: sin esta regla
        // cualquier medico autenticado administraria las clinicas de todos con
        // solo cambiar el id de la URL.
        JsonNode ajena = crearClinica(token,
                "{\"name\":\"Clinica Del Otro\",\"latitud\":13.7,\"longitud\":-89.2}");
        int clinicaId = ajena.get("clinicaId").asInt();

        String tokenIntruso = registrarMedicoYObtenerToken();

        mockMvc.perform(put("/clinics/{id}", clinicaId)
                        .header("Authorization", "Bearer " + tokenIntruso)
                        .contentType(MediaType.APPLICATION_JSON)
                        // Coordenadas validas a proposito. Con 0.0/0.0 -- el golfo de
                        // Guinea -- la validacion de HU-26 responde 400 antes de que
                        // el servicio llegue a comprobar de quien es la clinica, y la
                        // prueba pasaria sin haber ejercido la regla que le importa.
                        .content(completar("{\"name\":\"Secuestrada\",\"latitud\":13.7,\"longitud\":-89.2}")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/clinics/{id}", clinicaId)
                        .header("Authorization", "Bearer " + tokenIntruso))
                .andExpect(status().isForbidden());

        // El 403 no basta: hay que comprobar que ademas no cambio nada.
        var enBase = clinicas.findById(clinicaId)
                .orElseThrow(() -> new AssertionError("la clinica ajena no debio borrarse"));
        assertEquals("Clinica Del Otro", enBase.getName(), "el nombre ajeno no debio cambiar");
        assertEquals(13.7, enBase.getLatitud(), 1e-9, "la latitud ajena no debio cambiar");

        // Y tampoco debe verla en su listado.
        String suyas = mockMvc.perform(get("/clinics/mias")
                        .header("Authorization", "Bearer " + tokenIntruso))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode clinica : json.readTree(suyas)) {
            assertFalse(clinica.get("clinicaId").asInt() == clinicaId,
                    "la clinica de otro medico no debe aparecer en /clinics/mias");
        }
    }

    @Test
    @DisplayName("un medico si puede editar y borrar las suyas")
    void unMedicoSiAdministraLasSuyas() throws Exception {
        // Contrapeso de la anterior: sin esta prueba, responder 403 a todo el
        // mundo tambien pasaria. Aqui se comprueba que la regla distingue.
        JsonNode propia = crearClinica(token,
                "{\"name\":\"Clinica Propia\",\"latitud\":13.7,\"longitud\":-89.2}");
        int clinicaId = propia.get("clinicaId").asInt();

        mockMvc.perform(put("/clinics/{id}", clinicaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar("{\"name\":\"Clinica Propia Renombrada\"}")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/clinics/{id}", clinicaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertTrue(clinicas.findById(clinicaId).isEmpty(), "la clinica propia si debio borrarse");
    }

    @Test
    @DisplayName("editar o borrar una clinica inexistente responde 404")
    void unaClinicaInexistenteResponde404() throws Exception {
        int inexistente = 9_999_999;

        mockMvc.perform(put("/clinics/{id}", inexistente)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completar("{\"name\":\"Fantasma\"}")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/clinics/{id}", inexistente)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Alcance del selector de clinica
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Crea un ADMIN sin clinicas propias ni asignadas, y devuelve su token.
     *
     * Se fabrica por repositorio porque /auth/register solo da de alta medicos:
     * no hay ningun endpoint que cree un administrador. Y se le deja a
     * proposito sin una sola clinica, que es justo la situacion que esta prueba
     * examina.
     */
    private String tokenDeAdminSinClinicas() throws Exception {
        String correo = "crud.clinica.admin." + CONTADOR.incrementAndGet() + "@ues.edu.sv";

        Persona persona = personas.saveAndFlush(Persona.builder()
                .nombres("Admin").apellidos("De Sedes")
                .build());

        usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correo)
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new HashSet<>(Set.of(roles.getReferenceById(RolesEnum.ADMIN.getId()))))
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

    private boolean apareceEnMisClinicas(String tokenDeQuienMira, int clinicaId) throws Exception {
        String cuerpo = mockMvc.perform(get("/clinics/mias")
                        .header("Authorization", "Bearer " + tokenDeQuienMira))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (JsonNode clinica : json.readTree(cuerpo)) {
            if (clinica.get("clinicaId").asInt() == clinicaId) return true;
        }
        return false;
    }

    @Test
    @DisplayName("un admin ve en /clinics/mias las clinicas que no registro ni tiene asignadas")
    void unAdminVeTodasLasClinicas() throws Exception {
        // La sede la registra un medico; el admin no la toca ni se la asigna
        // nadie. Con la regla general -dueño o personal asignado- su lista
        // saldria vacia y el selector lo dejaria encallado en "todavia no
        // tienes clinicas registradas", sin forma de entrar al sistema que
        // administra.
        JsonNode ajena = crearClinica(token, "{\"name\":\"Clinica De Otro Medico\"}");
        int clinicaId = ajena.get("clinicaId").asInt();

        String tokenAdmin = tokenDeAdminSinClinicas();

        assertTrue(apareceEnMisClinicas(tokenAdmin, clinicaId),
                "un administrador debe ver todas las sedes, tambien las que no registro");
    }

    @Test
    @DisplayName("un medico NO ve en /clinics/mias la clinica de otro medico")
    void unMedicoNoVeLaClinicaDeOtro() throws Exception {
        // El contraste de la prueba anterior. Sin esto, "el admin las ve todas"
        // se cumpliria igual si el endpoint devolviera el catalogo completo a
        // cualquiera, que es exactamente lo que no debe pasar.
        JsonNode ajena = crearClinica(token, "{\"name\":\"Clinica Reservada\"}");
        int clinicaId = ajena.get("clinicaId").asInt();

        String tokenDeOtroMedico = registrarMedicoYObtenerToken();

        assertFalse(apareceEnMisClinicas(tokenDeOtroMedico, clinicaId),
                "un medico solo debe ver las sedes donde opera");
    }
}
