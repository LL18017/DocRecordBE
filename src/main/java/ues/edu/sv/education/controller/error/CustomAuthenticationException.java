package ues.edu.sv.education.controller.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
public class CustomAuthenticationException extends  RuntimeException {

    private final int errorCode;

    public CustomAuthenticationException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}

