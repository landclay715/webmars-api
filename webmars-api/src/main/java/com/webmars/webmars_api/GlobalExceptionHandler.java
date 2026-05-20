package com.webmars.webmars_api;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleVaLidationErrors(MethodArgumentNotValidException ex){
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()){
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDatabaseConflict(DataIntegrityViolationException ex){
        return Map.of("Error", "A record with that value already exists.");
    }
    @ExceptionHandler(ResponseStatusException.class)
    public Map<String, String> handleResponseStatus(ResponseStatusException ex, jakarta.servlet.http.HttpServletResponse response){
        response.setStatus(ex.getStatusCode().value());
        return Map.of("Error", ex.getReason() != null ? ex.getReason() : "An error occurred.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGeneric(Exception ex){
        return Map.of("Error", "An unexpected error occurred. Please try again.");
    }
}
