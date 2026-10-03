package ues.edu.sv.education;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetAddress;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La IP del evento de inicio de sesion se llena, y se llena con la del USUARIO.
 *
 * Cada login guarda un evento que despues dispara un correo de aviso. El campo
 * ip_address existia en la tabla desde V1 y la plantilla del correo ya sabia
 * pintarlo, pero NADIE lo escribia nunca: el aviso decia que alguien entro a la
 * cuenta y no desde donde, que es justamente lo unico que permite reconocer un
 * acceso ajeno.
 *
 * La segunda prueba es la que importa de cara al despliegue. En el VPS la
 * aplicacion no habla con el usuario sino con el proxy inverso que tiene
 * delante, asi que getRemoteAddr() devolveria la IP DEL PROXY -- la misma para
 * todos los usuarios y todas las sesiones, o sea un dato inutil. Quien lo
 * arregla es server.forward-headers-strategy=native, que pone la RemoteIpValve
 * de Tomcat delante de la aplicacion.
 *
 * Por eso estas pruebas levantan un Tomcat real: la valve vive en el contenedor
 * y MockMvc no lo usa (ver PruebaConServidorReal).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IpDelAvisoDeInicioDeSesionIT extends PruebaConServidorReal {

    @Test
    @DisplayName("el evento guardado lleva la IP de quien inicio sesion")
    void elEventoGuardaLaIpDeLaPeticion() throws Exception {
        String correo = crearMedicoHabilitado();

        HttpResponse<String> respuesta = iniciarSesion(correo);
        assertEquals(200, respuesta.statusCode());

        String ip = ipDelEventoDeLogin(correo);

        assertNotNull(ip, "El evento se guardo con ip_address vacia: el aviso de seguridad "
                + "volveria a no decir desde donde se entro, que es para lo unico que sirve.");
        // Sin proxy de por medio, la IP tiene que ser la del cliente que se
        // conecto de verdad -- aqui la maquina que corre la prueba. No se
        // compara con la cadena "127.0.0.1" porque `localhost` puede resolverse
        // a la direccion de bucle IPv6 segun la maquina.
        assertTrue(InetAddress.getByName(ip).isLoopbackAddress(),
                "Se esperaba la direccion real del cliente y llego: " + ip);
    }

    @Test
    @DisplayName("detras del proxy se guarda la IP del usuario, no la del salto intermedio")
    void detrasDelProxySeGuardaLaDelUsuarioYNoLaDelSalto() throws Exception {
        String correo = crearMedicoHabilitado();

        // Asi llega la cabecera cuando nginx usa $proxy_add_x_forwarded_for: la
        // izquierda es el cliente y a la derecha se van anadiendo los saltos.
        // 10.20.30.40 es una direccion privada, o sea un proxy de la lista de
        // confianza de Tomcat, y por eso la valve la salta.
        HttpResponse<String> respuesta = iniciarSesion(correo,
                "X-Forwarded-For", "190.86.20.4, 10.20.30.40");
        assertEquals(200, respuesta.statusCode());

        String ip = ipDelEventoDeLogin(correo);

        assertEquals("190.86.20.4", ip,
                "Debe quedar la IP del usuario. Si llega 10.20.30.40 se esta registrando el "
                        + "salto intermedio; si llega una direccion de bucle, la valve no esta "
                        + "puesta y en el VPS todos los avisos dirian la IP del proxy.");
    }
}
