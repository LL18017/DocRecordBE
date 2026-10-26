package ues.edu.sv.education.service.Cita;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que el aviso de reprogramacion necesita saber de ANTES del cambio: la
 * fecha y el medico que tenia la cita (HU-32, criterio 4).
 *
 * Viaja en events.description porque el evento no tiene mas columnas libres y
 * la cita ya guarda solo el estado vigente. Se escribe y se lee aqui, en un
 * solo sitio, para que el formato no dependa de quien lo use.
 */
public record DetalleDeReprogramacion(LocalDateTime fechaAnterior, Integer medicoAnteriorId) {

    private static final Pattern FORMATO =
            Pattern.compile("Cita reprogramada\\. Antes: fecha=(\\S+) medicoId=(\\d+)");

    public String codificar() {
        return "Cita reprogramada. Antes: fecha=" + fechaAnterior + " medicoId=" + medicoAnteriorId;
    }

    public static Optional<DetalleDeReprogramacion> leer(String descripcion) {
        if (descripcion == null) {
            return Optional.empty();
        }
        Matcher m = FORMATO.matcher(descripcion);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DetalleDeReprogramacion(
                    LocalDateTime.parse(m.group(1)), Integer.valueOf(m.group(2))));
        } catch (DateTimeParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
