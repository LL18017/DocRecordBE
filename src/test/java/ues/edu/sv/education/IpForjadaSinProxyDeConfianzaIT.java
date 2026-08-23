package ues.edu.sv.education;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetAddress;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Una cabecera de proxy que manda un cliente cualquiera NO se cree.
 *
 * Es la razon por la que se eligio server.forward-headers-strategy=native y no
 * `framework`. Las dos leen X-Forwarded-For y reescriben getRemoteAddr(), pero
 * el ForwardedHeaderFilter de Spring (`framework`) se la cree venga de donde
 * venga: bastaria un curl directo contra el puerto de la aplicacion con
 * `X-Forwarded-For: 1.2.3.4` para decidir que IP queda escrita en el aviso de
 * seguridad de otra persona. Una IP que el cliente elige no es un dato de
 * seguridad. La RemoteIpValve de Tomcat (`native`) solo hace caso a la cabecera
 * cuando la conexion viene de un proxy de su lista de confianza.
 *
 * Para probarlo hay que conseguir que el cliente de la prueba NO sea de
 * confianza, y el cliente se conecta por bucle local, que si lo es por defecto.
 * De ahi la propiedad de abajo: la lista de confianza se reduce a un rango
 * reservado para documentacion (TEST-NET-3, RFC 5737) que jamas coincidira con
 * la direccion de bucle. La peticion pasa entonces por lo que en el despliegue
 * seria un atacante hablando directo con la aplicacion.
 *
 * Si alguien cambia la estrategia a `framework`, esta prueba se pone en rojo.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.tomcat.remoteip.internal-proxies=203\\.0\\.113\\.\\d{1,3}")
class IpForjadaSinProxyDeConfianzaIT extends PruebaConServidorReal {

    @Test
    @DisplayName("se ignora la X-Forwarded-For de un cliente que no es proxy de confianza")
    void seIgnoraLaCabeceraDeUnClienteNoConfiable() throws Exception {
        String correo = crearMedicoHabilitado();

        HttpResponse<String> respuesta = iniciarSesion(correo,
                "X-Forwarded-For", "190.86.20.4");
        assertEquals(200, respuesta.statusCode());

        String ip = ipDelEventoDeLogin(correo);

        // Antes de nada: que haya IP. Sin esta linea la prueba pasaria tambien
        // con el campo sin llenar, porque InetAddress.getByName(null) devuelve
        // la direccion de bucle y la comprobacion de abajo se cumpliria sola.
        assertNotNull(ip, "El evento se guardo sin IP");

        assertNotEquals("190.86.20.4", ip,
                "Se creyo una cabecera que mando el propio cliente. Con eso, cualquiera "
                        + "podria elegir la IP que aparece en el aviso de acceso de otra persona.");
        assertTrue(InetAddress.getByName(ip).isLoopbackAddress(),
                "Debe quedar la direccion real del que se conecto, y llego: " + ip);
    }
}
