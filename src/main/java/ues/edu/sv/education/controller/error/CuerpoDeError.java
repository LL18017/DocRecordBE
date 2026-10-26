package ues.edu.sv.education.controller.error;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Arma el cuerpo de TODO error de la API (TT-01, criterio 1).
 *
 * Existe aparte del GlobalExceptionHandler porque lo usan dos sitios que no se
 * pueden ayudar entre si: el manejador global y el JwtFilter, que corre antes
 * del DispatcherServlet y al que ningun @ExceptionHandler alcanza. Con una sola
 * fabrica los dos responden exactamente la misma forma.
 *
 * El cuerpo es aditivo sobre el contrato original `{error, message}`:
 *
 *   error      categoria del fallo ("Conflicto")           -- ya existia
 *   message    motivo para una persona                      -- ya existia; la
 *                                                              interfaz lo muestra
 *   codigo     codigo de estado HTTP (404, 409, ...)
 *   timestamp  instante del error, ISO-8601 en UTC
 *   ruta       URI de la peticion que fallo
 *
 * Se conservan `error` y `message` con su significado de siempre: el frontend
 * (`mensajeDirecto` en DocRecordFE/src/lib/api.ts) lee `message` primero, y
 * quitarlo o renombrarlo dejaria al usuario con el texto generico por codigo.
 */
public final class CuerpoDeError {

    private CuerpoDeError() {
    }

    public static Map<String, Object> de(HttpStatus estado, String error, String message, String ruta) {
        return de(estado.value(), error, message, ruta);
    }

    public static Map<String, Object> de(int codigo, String error, String message, String ruta) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", error);
        cuerpo.put("message", message);
        completar(cuerpo, codigo, ruta);
        return cuerpo;
    }

    /**
     * Agrega al final los metadatos comunes. Va al FINAL y con `put` a
     * proposito: si una validacion tuviera un campo llamado `codigo` o `ruta`,
     * gana el contrato; al reves el cuerpo dejaria de cumplirlo sin avisar.
     */
    static void completar(Map<String, Object> cuerpo, int codigo, String ruta) {
        cuerpo.put("codigo", codigo);
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("ruta", ruta);
    }
}
