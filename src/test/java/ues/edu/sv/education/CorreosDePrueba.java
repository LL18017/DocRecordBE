package ues.edu.sv.education;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;

/**
 * Utilidades para mirar por dentro los correos que se entregaron al emisor
 * simulado.
 *
 * Un MimeMessage multipart no es una cadena: es un arbol de partes
 * (mixed > related > alternative > text/plain + text/html). Estas funciones lo
 * recorren para que cada prueba pueda preguntar directamente "dame el HTML" o
 * "dame el texto plano" en vez de repetir el recorrido.
 */
final class CorreosDePrueba {

    private CorreosDePrueba() {
    }

    static String destinatario(MimeMessage mensaje) {
        try {
            return mensaje.getAllRecipients()[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el destinatario del correo", e);
        }
    }

    static String asunto(MimeMessage mensaje) {
        try {
            return mensaje.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el asunto del correo", e);
        }
    }

    /** El cuerpo HTML, o null si el correo no lleva parte HTML. */
    static String parteHtml(MimeMessage mensaje) {
        return parte(mensaje, "text/html");
    }

    /** El cuerpo de texto plano, o null si el correo no lleva parte de texto. */
    static String parteDeTexto(MimeMessage mensaje) {
        return parte(mensaje, "text/plain");
    }

    private static String parte(MimeMessage mensaje, String tipoMime) {
        try {
            // saveChanges() escribe las cabeceras Content-Type de todas las
            // partes. Sin el, jakarta.mail no las tiene todavia escritas y cada
            // parte se declara como "text/plain" por defecto, que es su valor
            // cuando falta la cabecera: el recorrido de abajo confundiria un
            // multipart con un texto. Es exactamente lo que hace el emisor real
            // (JavaMailSenderImpl) justo antes de mandar, asi que lo que se
            // inspecciona aqui es el mensaje tal como saldria.
            mensaje.saveChanges();
            return buscar(mensaje.getContent(), tipoMime);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el contenido del correo", e);
        }
    }

    private static String buscar(Object contenido, String tipoMime) throws Exception {
        if (!(contenido instanceof Multipart partes)) {
            return null;
        }
        for (int i = 0; i < partes.getCount(); i++) {
            BodyPart parte = partes.getBodyPart(i);
            if (parte.isMimeType(tipoMime)) {
                return (String) parte.getContent();
            }
            String anidado = buscar(parte.getContent(), tipoMime);
            if (anidado != null) {
                return anidado;
            }
        }
        return null;
    }
}
