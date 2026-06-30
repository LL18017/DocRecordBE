package ues.edu.sv.education.controller.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
public class NoResourceFoundException extends  RuntimeException {

    private final String errorCode;

    public NoResourceFoundException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

