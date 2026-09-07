package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.userType.UserTypeRequestDto;
import ues.edu.sv.education.model.dto.userType.UserTypeResponseDto;
import ues.edu.sv.education.model.entity.UserType;

public class UserTypeMapper {
    public static UserTypeResponseDto toDto(UserType userType) {
        return new UserTypeResponseDto(
               userType.getUserTypeID(),
                userType.getName()
        );
    }
    public static UserType toEntity(UserTypeRequestDto dto) {
        UserType userType = new UserType();
        userType.setName(dto.name());

        return userType;
    }


}
