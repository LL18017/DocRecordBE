package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;

import java.util.HashSet;

public class UserMapper {
    public static UserResponseDto toDto(User user) {
        Persona persona = user.getPersona();
        return new UserResponseDto(
                user.getUserID(),
                user.getEmail(),
                persona.getNombres() + " " + persona.getApellidos(),
                user.getRoles().stream().map(RoleMapper::toDto).toList()
        );
    }

    /**
     * Arma el User a partir del alta, con la contrasena YA CIFRADA.
     *
     * El hash se recibe como parametro en vez de leerse de request.password()
     * a proposito. Antes este metodo copiaba la contrasena en claro del DTO a
     * la entidad, y como UserService.createUser guardaba lo que este metodo
     * devolvia, POST /user dejaba la contrasena LEGIBLE en users.password: se
     * comprobo en la base leyendo "Docrecord2026!" tal cual. Un solo SELECT
     * sobre la tabla -o un volcado, o una copia de seguridad extraviada-
     * entregaba las credenciales de todo el personal, y como la gente reusa
     * contrasenas el dano no se queda en este sistema.
     *
     * Al exigir el hash en la firma el error deja de ser posible por olvido:
     * quien llame a toEntity tiene que haber pasado la contrasena por el
     * PasswordEncoder antes, porque el DTO por si solo ya no basta para
     * construir la entidad. Ver UserService.createUser y, para el mismo
     * cifrado en el registro publico, AuthService.registrarMedico.
     *
     * La persona ya debe existir (guardada) antes de llamar esto: User solo
     * guarda la referencia, no crea su propia identidad.
     *
     * @param passwordCifrada el resultado de PasswordEncoder.encode, nunca la
     *                        contrasena en claro.
     */
    public static User toEntity(UserRequestDto request, Persona persona, String passwordCifrada) {
        return new User(
                null,
                persona,
                request.email(),
                passwordCifrada,
                false,
                new HashSet<>(),
                null
        );
    }
}
