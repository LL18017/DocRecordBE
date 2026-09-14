package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.clinicas.ClinicasResponseDto;
import ues.edu.sv.education.model.entity.Clinicas;

/**
 * El unico sitio que arma un ClinicasResponseDto.
 *
 * Existe porque lo armaban dos: ClinicaService y UserService.clinicasAsignadas,
 * cada uno con su propia llamada al constructor posicional del record. Al
 * anadirle cinco campos a la clinica (V16), el primero se actualizo y el
 * segundo dejo de compilar.
 *
 * Es la segunda vez que pasa lo mismo en este proyecto -- la primera fue
 * UserMapper con el constructor de User -- y el patron es el mismo: un
 * constructor posicional duplicado convierte cada campo nuevo en una rotura
 * para todos sus llamadores. Con un solo mapper, anadir un campo se toca aqui
 * y en ningun otro lado.
 */
public final class ClinicaMapper {

    private ClinicaMapper() {
    }

    public static ClinicasResponseDto toDto(Clinicas clinica) {
        return new ClinicasResponseDto(
                clinica.getClinicaId(),
                clinica.getName(),
                clinica.getLatitud(),
                clinica.getLongitud(),
                clinica.getDepartamento(),
                clinica.getMunicipio(),
                clinica.getDireccion(),
                clinica.getTelefono(),
                clinica.getHorario(),
                clinica.getEstado()
        );
    }
}
