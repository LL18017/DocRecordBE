package ues.edu.sv.education;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-04: recuperación de contraseña por correo.
 *
 * Una prueba por criterio de aceptación, mas las dos formas de fallar que el
 * criterio 3 nombra por separado:
 *
 *   1. correo registrado -> token de 1 hora y enlace enviado a ese correo
 *      ......................... criterio1_seEmiteUnTokenDeUnaHoraYSeEnviaElEnlace
 *   2. token vigente y sin usar -> cambia la contraseña y el token queda usado
 *      ......................... criterio2_conUnEnlaceVigenteLaContrasenaCambiaYElTokenQuedaUsado
 *   3. token ya usado ........... criterio3_unEnlaceYaUsadoDejaDeServir
 *      token vencido ............ criterio3_unEnlaceVencidoDejaDeServir
 *      y los dos, mas uno inexistente, con el mismo rechazo
 *      ......................... criterio3_losTresRechazosDicenExactamenteLoMismo
 *   4. correo no registrado -> misma respuesta que el caso exitoso
 *      ......................... criterio4_laRespuestaNoDelataSiLaCuentaExiste
 *
 * Hay una séptima prueba que no corresponde a ningún criterio y sí a una
 * decisión que se tomó al implementarlos: restablecer la contraseña NO
 * reactiva una cuenta deshabilitada
 * (restablecerNoReactivaUnaCuentaDeshabilitada). Está aquí para que nadie la
 * revierta "por comodidad" sin leer el porqué, que está en
 * RecuperacionDeContrasenaService.restablecer.
 *
 * ── Por que las cuentas se crean por repositorio y no por /auth/register ──
 * Registrarse manda un correo de confirmación, y aquí se cuentan y se
 * inspeccionan los correos que salen: ese mensaje de más obligaría a filtrar
 * en cada verify() cuál de los dos se está mirando. Crear la fila directamente
 * deja el emisor simulado limpio para el único correo que interesa.
 *
 * ── Por que hay esperas con timeout y no aserciones inmediatas ────────────
 * El envío ocurre a proposito FUERA del hilo de la petición (ver
 * RecuperacionDeContrasenaService: es lo que impide que el tiempo de respuesta
 * delate si la cuenta existe). Cuando MockMvc devuelve, el correo todavía
 * puede no haber salido, asi que se espera a que salga con
 * verify(..., timeout(...)) en vez de comprobarlo en seco. Cada prueba que
 * dispara un envío espera a que termine antes de acabar, para no dejar trabajo
 * en vuelo que contamine el emisor simulado de la prueba siguiente.
 */
class RecuperacionDeContrasenaIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    /**
     * La misma base con la que el servicio arma el enlace. Se lee de la
     * configuración en vez de escribir "http://localhost:3000" a mano: si
     * alguien corre la suite con FRONTEND_URL definida, la prueba tiene que
     * seguir comprobando el enlace de verdad, no fallar por el entorno.
     */
    @Value("${app.frontend.url:http://localhost:3000}")
    private String urlDelFrontend;

    private static final String CLAVE_NUEVA = "RecuperadaClave2026!";

    /**
     * Lo que tarda el emisor simulado en la prueba del criterio 4.
     *
     * En producción este retraso no hay que fabricarlo: el envío real tiene 5
     * segundos de timeout configurados (application.properties), asi que la
     * diferencia que se mide aquí es una versión benigna de la que existe.
     */
    private static final long RETRASO_DEL_SMTP_MS = 400;

    /** Vueltas de medición del criterio 4. Se compara el mínimo de cada lado. */
    private static final int VUELTAS = 5;

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    /** Una cuenta habilitada, con nombre reconocible para buscarlo en el correo. */
    private User crearCuenta(String etiqueta) {
        return crearCuenta(etiqueta, true);
    }

    private User crearCuenta(String etiqueta, boolean habilitada) {
        Persona persona = personas.saveAndFlush(Persona.builder()
                .nombres("Noemi").apellidos("Del Valle")
                .build());

        return usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correoUnico(etiqueta))
                .password(encoder.encode(CLAVE))
                .enabled(habilitada)
                .roles(new HashSet<>(Set.of(roles.getReferenceById(RolesEnum.MEDICO.getId()))))
                .build());
    }

    private ResultActions solicitar(String correo) throws Exception {
        return mockMvc.perform(post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """.formatted(correo)));
    }

    private ResultActions restablecer(String token, String clave) throws Exception {
        return mockMvc.perform(post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","password":"%s"}
                        """.formatted(token, clave)));
    }

    private ResultActions iniciarSesion(String correo, String clave) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(correo, clave)));
    }

    /** Espera a que el envío en segundo plano termine y devuelve el mensaje. */
    private MimeMessage esperarElCorreo() {
        ArgumentCaptor<MimeMessage> mensajes = ArgumentCaptor.forClass(MimeMessage.class);
        verify(correoDePruebas, timeout(10_000)).send(mensajes.capture());
        return mensajes.getValue();
    }

    private Map<String, Object> filaDelToken(String correo) {
        return jdbc.queryForMap(
                "select t.token, t.vence_en, t.usado from token_restablecimiento t "
                        + "join users u on u.user_id = t.user_id "
                        + "where lower(u.email) = lower(?)", correo);
    }

    /** Mete un token a mano, para fabricar los estados que el criterio 3 exige. */
    private String tokenFabricado(User cuenta, LocalDateTime venceEn, boolean usado) {
        String token = UUID.randomUUID().toString();
        jdbc.update("insert into token_restablecimiento (token, user_id, vence_en, usado) "
                        + "values (?, ?, ?, ?)",
                token, cuenta.getUserID(), Timestamp.valueOf(venceEn), usado);
        return token;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 1
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("criterio 1: un correo registrado recibe el enlace y su token vence en 1 hora")
    void criterio1_seEmiteUnTokenDeUnaHoraYSeEnviaElEnlace() throws Exception {
        User cuenta = crearCuenta("recuperacion.emite");

        LocalDateTime antesDePedirlo = LocalDateTime.now();
        solicitar(cuenta.getEmail()).andExpect(status().isAccepted());

        MimeMessage mensaje = esperarElCorreo();
        Map<String, Object> fila = filaDelToken(cuenta.getEmail());

        assertEquals(cuenta.getEmail(), CorreosDePrueba.destinatario(mensaje),
                "el enlace tiene que ir al correo de la cuenta, no a otro");
        assertNotNull(CorreosDePrueba.asunto(mensaje),
                "un correo sin asunto acaba en la carpeta de spam");
        assertFalse((Boolean) fila.get("usado"),
                "un token recien emitido no puede nacer marcado como usado");

        // Una hora exacta: no cinco minutos -lo que dura el token de
        // confirmacion, que es de donde se copia el patron- ni un dia.
        LocalDateTime venceEn = ((Timestamp) fila.get("vence_en")).toLocalDateTime();
        assertTrue(venceEn.isAfter(antesDePedirlo.plusMinutes(59)),
                "el token vence antes de la hora prometida: " + venceEn);
        assertTrue(venceEn.isBefore(antesDePedirlo.plusMinutes(61)),
                "el token dura mas de la hora prometida: " + venceEn);

        String enlaceEsperado = urlDelFrontend + "/restablecer?token=" + fila.get("token");

        String html = CorreosDePrueba.parteHtml(mensaje);
        assertNotNull(html, "el correo de recuperacion debe llevar parte HTML");
        assertTrue(html.contains("href=\"" + enlaceEsperado + "\""),
                "el HTML debe llevar EL enlace de esta cuenta en un href.\n" + html);

        // Ademas escrito a la vista, fuera del atributo: hay clientes que
        // bloquean estilos y dejan el boton inservible.
        String sinAtributos = html.replace("href=\"" + enlaceEsperado + "\"", "");
        assertTrue(sinAtributos.contains(enlaceEsperado),
                "la direccion tambien debe ir escrita a la vista, no solo dentro del boton");

        // El saludo por nombre no es cosmetico aqui: la persona se carga en el
        // hilo de fondo, fuera de toda transaccion, y sin el join fetch de
        // UserRepository.buscarConPersonaPorCorreo esto reventaria por dentro.
        assertTrue(html.contains("Noemi"),
                "el correo debe saludar por nombre; si falta, la persona no se cargo");

        String texto = CorreosDePrueba.parteDeTexto(mensaje);
        assertNotNull(texto, "debe haber parte de texto plano; hay clientes que no ensenan HTML");
        assertTrue(texto.contains(enlaceEsperado),
                "en la version de texto el enlace debe ir completo y copiable.\n" + texto);
        assertTrue(texto.contains("1 hora"),
                "el texto debe decir en cuanto vence el enlace.\n" + texto);
        assertTrue(html.contains("1 hora"),
                "el HTML debe decir el mismo plazo que el texto");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 2
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("criterio 2: con un enlace vigente la contrasena cambia y el token queda usado")
    void criterio2_conUnEnlaceVigenteLaContrasenaCambiaYElTokenQuedaUsado() throws Exception {
        User cuenta = crearCuenta("recuperacion.canje");

        solicitar(cuenta.getEmail()).andExpect(status().isAccepted());
        esperarElCorreo();
        String token = (String) filaDelToken(cuenta.getEmail()).get("token");

        restablecer(token, CLAVE_NUEVA).andExpect(status().isOk());

        assertTrue((Boolean) filaDelToken(cuenta.getEmail()).get("usado"),
                "el token tiene que quedar marcado como usado tras canjearlo");

        // Lo que de verdad importa: la contrasena cambio.
        iniciarSesion(cuenta.getEmail(), CLAVE_NUEVA).andExpect(status().is2xxSuccessful());
        iniciarSesion(cuenta.getEmail(), CLAVE).andExpect(status().is4xxClientError());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 3
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("criterio 3: un enlace ya usado deja de servir y no vuelve a cambiar la contrasena")
    void criterio3_unEnlaceYaUsadoDejaDeServir() throws Exception {
        User cuenta = crearCuenta("recuperacion.reusado");

        solicitar(cuenta.getEmail()).andExpect(status().isAccepted());
        esperarElCorreo();
        String token = (String) filaDelToken(cuenta.getEmail()).get("token");

        restablecer(token, CLAVE_NUEVA).andExpect(status().isOk());

        // Segundo intento con el MISMO enlace.
        String tercera = "TerceraClave2026!";
        restablecer(token, tercera).andExpect(status().isBadRequest());

        // Rechazar con 400 y cambiar la contrasena igual seria peor que no
        // rechazar: por eso se comprueba el efecto, no solo el codigo.
        iniciarSesion(cuenta.getEmail(), tercera).andExpect(status().is4xxClientError());
        iniciarSesion(cuenta.getEmail(), CLAVE_NUEVA).andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("criterio 3: un enlace vencido deja de servir aunque nunca se haya usado")
    void criterio3_unEnlaceVencidoDejaDeServir() throws Exception {
        User cuenta = crearCuenta("recuperacion.vencido");

        // Vencido por un minuto y sin usar: el unico motivo de rechazo posible
        // es la fecha.
        String token = tokenFabricado(cuenta, LocalDateTime.now().minusMinutes(1), false);

        restablecer(token, CLAVE_NUEVA).andExpect(status().isBadRequest());

        iniciarSesion(cuenta.getEmail(), CLAVE_NUEVA).andExpect(status().is4xxClientError());
        iniciarSesion(cuenta.getEmail(), CLAVE).andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("criterio 3: inexistente, usado y vencido se rechazan exactamente igual")
    void criterio3_losTresRechazosDicenExactamenteLoMismo() throws Exception {
        User cuenta = crearCuenta("recuperacion.rechazos");

        String inexistente = UUID.randomUUID().toString();
        String usado = tokenFabricado(cuenta, LocalDateTime.now().plusHours(1), true);
        String vencido = tokenFabricado(cuenta, LocalDateTime.now().minusMinutes(1), false);

        MockHttpServletResponse porInexistente =
                restablecer(inexistente, CLAVE_NUEVA).andReturn().getResponse();
        MockHttpServletResponse porUsado =
                restablecer(usado, CLAVE_NUEVA).andReturn().getResponse();
        MockHttpServletResponse porVencido =
                restablecer(vencido, CLAVE_NUEVA).andReturn().getResponse();

        // "Ese token ya fue usado" confirmaria que el token existio y que
        // pertenece a una cuenta real, que es justo lo que se le esta
        // preguntando al sistema cuando alguien prueba tokens a ciegas.
        assertEquals(porInexistente.getStatus(), porUsado.getStatus(),
                "un token usado no puede responder distinto que uno que no existe");
        assertEquals(porInexistente.getStatus(), porVencido.getStatus(),
                "un token vencido no puede responder distinto que uno que no existe");
        assertEquals(porInexistente.getContentAsString(), porUsado.getContentAsString(),
                "el cuerpo delata que ese token existia");
        assertEquals(porInexistente.getContentAsString(), porVencido.getContentAsString(),
                "el cuerpo delata que ese token existia");
    }

    // ══════════════════════════════════════════════════════════════════════
    // La decision que NO esta en los criterios: restablecer no reactiva
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("restablecer la contrasena cambia la clave pero NO reactiva una cuenta deshabilitada")
    void restablecerNoReactivaUnaCuentaDeshabilitada() throws Exception {
        User cuenta = crearCuenta("recuperacion.deshabilitada", false);
        String token = tokenFabricado(cuenta, LocalDateTime.now().plusHours(1), false);

        restablecer(token, CLAVE_NUEVA).andExpect(status().isOk());

        User despues = usuarios.findByEmailIgnoreCase(cuenta.getEmail())
                .orElseThrow(() -> new AssertionError("se perdio la cuenta " + cuenta.getEmail()));

        assertTrue(encoder.matches(CLAVE_NUEVA, despues.getPassword()),
                "la contrasena si tiene que cambiar; lo que no cambia es el estado de la cuenta");

        // `users.enabled` lo usan dos cosas a la vez: "el correo esta
        // confirmado" y, desde HU-05, "la cuenta esta activa". Con un solo
        // booleano, habilitar aqui dejaria que cualquiera a quien acaban de
        // desactivar se reactive solo pidiendo recuperar su contrasena.
        assertFalse(despues.isEnabled(),
                "restablecer la contrasena no puede reactivar una cuenta deshabilitada");
        iniciarSesion(cuenta.getEmail(), CLAVE_NUEVA).andExpect(status().is4xxClientError());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 4
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("criterio 4: un correo no registrado responde igual que uno registrado, tambien en el tiempo")
    void criterio4_laRespuestaNoDelataSiLaCuentaExiste() throws Exception {
        User cuenta = crearCuenta("recuperacion.existe");
        String registrado = cuenta.getEmail();
        String inexistente = correoUnico("recuperacion.no.existe");

        // Un SMTP lento a proposito. Esto es lo que separa esta prueba de una
        // que solo compare cuerpos: con el envio dentro del hilo de la
        // peticion, la cuenta registrada tardaria RETRASO_DEL_SMTP_MS mas que
        // la inexistente y cualquiera con un cronometro sabria cuales existen.
        doAnswer(invocacion -> {
            Thread.sleep(RETRASO_DEL_SMTP_MS);
            return null;
        }).when(correoDePruebas).send(any(MimeMessage.class));

        // Calentamiento: la primera peticion de MockMvc paga inicializaciones
        // que no tienen nada que ver con lo que se mide. Se hace con
        // direcciones inexistentes para no emitir correos de mas.
        solicitar(correoUnico("recuperacion.calentamiento"));
        solicitar(correoUnico("recuperacion.calentamiento"));

        MockHttpServletResponse conCuenta = null;
        MockHttpServletResponse sinCuenta = null;
        long mejorConCuenta = Long.MAX_VALUE;
        long mejorSinCuenta = Long.MAX_VALUE;

        // Se toma el MINIMO de varias vueltas, no el promedio: el minimo es el
        // tiempo sin ruido de planificacion ni de recoleccion de basura, que es
        // justamente el que mediria quien intente distinguir las dos
        // respuestas.
        for (int vuelta = 0; vuelta < VUELTAS; vuelta++) {
            long inicio = System.nanoTime();
            conCuenta = solicitar(registrado).andReturn().getResponse();
            mejorConCuenta = Math.min(mejorConCuenta, (System.nanoTime() - inicio) / 1_000_000);

            inicio = System.nanoTime();
            sinCuenta = solicitar(inexistente).andReturn().getResponse();
            mejorSinCuenta = Math.min(mejorSinCuenta, (System.nanoTime() - inicio) / 1_000_000);
        }

        assertEquals(sinCuenta.getStatus(), conCuenta.getStatus(),
                "el codigo HTTP no puede depender de si el correo existe");
        assertEquals(sinCuenta.getContentType(), conCuenta.getContentType(),
                "hasta el tipo de contenido tiene que ser el mismo");
        assertEquals(sinCuenta.getContentAsString(), conCuenta.getContentAsString(),
                "el cuerpo tiene que ser identico palabra por palabra");

        long diferencia = Math.abs(mejorConCuenta - mejorSinCuenta);
        assertTrue(diferencia < RETRASO_DEL_SMTP_MS / 2,
                "la respuesta tarda distinto segun si la cuenta existe, y eso revela cuales "
                        + "existen: registrado " + mejorConCuenta + " ms, no registrado "
                        + mejorSinCuenta + " ms (el envio simulado tarda "
                        + RETRASO_DEL_SMTP_MS + " ms)");

        // Antes de terminar, se espera a que los envios en vuelo acaben: uno
        // por cada solicitud del correo REGISTRADO y ninguno por las demas.
        verify(correoDePruebas, timeout(30_000).times(VUELTAS)).send(any(MimeMessage.class));
    }
}
