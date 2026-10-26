package ues.edu.sv.education.model.dto.auth;

/**
 * La regla de contrasena nueva (HU-03 criterio 2), en un solo sitio.
 *
 * Existe como constante y no repetida en cada DTO porque son cinco los
 * cuerpos que reciben una contrasena nueva -restablecer, registro, alta y
 * asignacion por un administrador, alta de enfermeria-, y una regla copiada
 * cinco veces acaba distinta en una de ellas. Es constante de compilacion
 * porque las anotaciones de validacion no admiten otra cosa.
 *
 * Minimo 8 caracteres, al menos una mayuscula y al menos un numero. Se evalua
 * con \p{Lu} y \d para que una mayuscula acentuada tambien cuente.
 *
 * El login NO usa esta regla: validar la contrasena al entrar impediria
 * iniciar sesion a las cuentas creadas antes de que existiera.
 */
public final class ReglaDeContrasena {

    public static final String PATRON = "^(?=.*\\p{Lu})(?=.*\\d).{8,}$";

    public static final String MENSAJE =
            "La contrasena debe tener al menos 8 caracteres, una mayuscula y un numero";

    private ReglaDeContrasena() {
    }
}
