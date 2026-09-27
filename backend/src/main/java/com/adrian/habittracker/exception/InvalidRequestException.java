package com.adrian.habittracker.exception;

/** Peticion con datos que pasan @Valid pero no tienen sentido (zona horaria inexistente, endpoint no permitido...). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}