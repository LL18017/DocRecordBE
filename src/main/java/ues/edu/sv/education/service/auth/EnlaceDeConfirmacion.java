package ues.edu.sv.education.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * El enlace del correo de confirmacion: GET /auth/confirm?token=..., que
 * habilita la cuenta y redirige a la pantalla /confirmar del frontend.
 *
 * ── Por que no basta con la direccion de la peticion ───────────────────────
 * Antes se armaba con ServletUriComponentsBuilder.fromCurrentContextPath(): la
 * misma direccion por la que entro la peticion que creo la cuenta. En
 * produccion la aplicacion llama a la API por el MISMO dominio, bajo /api, y
 * Caddy quita ese /api antes de pasar la peticion al backend. El backend nunca
 * se enteraba de que existia, y escribia en el correo
 * https://docrecordsv.duckdns.org/auth/confirm -sin /api-: una ruta del
 * frontend que no existe, y la persona veia un 404 al confirmar su cuenta.
 *
 * app.api.url es la direccion publica de la API tal como la ve el navegador.
 * docker-compose.prod.yml la arma desde PUBLIC_URL. Vacia -desarrollo, donde
 * el backend se llama directo en localhost:8080- se vuelve a la direccion de
 * la peticion, que ahi si es la correcta.
 */
@Component
public class EnlaceDeConfirmacion {

    private final String urlPublicaDeLaApi;

    public EnlaceDeConfirmacion(@Value("${app.api.url:}") String urlPublicaDeLaApi) {
        this.urlPublicaDeLaApi = urlPublicaDeLaApi;
    }

    public String para(String token) {
        UriComponentsBuilder base = urlPublicaDeLaApi.isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath()
                : UriComponentsBuilder.fromUriString(urlPublicaDeLaApi);
        return base.path("/auth/confirm")
                .queryParam("token", token)
                .toUriString();
    }
}
