package ues.edu.sv.education.service;

import lombok.Getter;

/**
 * Los correos que sabe mandar DocRecord Sv.
 *
 * Cada uno es un nombre base que corresponde a DOS archivos bajo
 * src/main/resources/templates/correo:
 *
 *   html/<nombreBase>.html    version con formato
 *   texto/<nombreBase>.txt    version de texto plano
 *
 * El asunto va aqui y no en quien manda el correo por la misma razon que el
 * cuerpo esta en las plantillas: es texto de cara al usuario. Antes el asunto
 * del aviso de sesion salia de event_types.description, que dice "Inicio de
 * sesión" -una etiqueta de catalogo, no un asunto de correo-, y el de
 * confirmacion estaba escrito a mano dentro de AuthService.
 */
@Getter
public enum PlantillaDeCorreo {

    CONFIRMACION_DE_REGISTRO("confirmacion-registro", "Confirma tu cuenta de DocRecord Sv"),

    AVISO_DE_INICIO_DE_SESION("aviso-inicio-sesion", "Nuevo inicio de sesión en DocRecord Sv"),

    TOKEN_EXPIRADO("token-expirado", "Tu enlace de confirmación venció");

    private final String nombreBase;
    private final String asunto;

    PlantillaDeCorreo(String nombreBase, String asunto) {
        this.nombreBase = nombreBase;
        this.asunto = asunto;
    }
}
