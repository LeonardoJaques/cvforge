package com.cvforge.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validationError(MethodArgumentNotValidException exception) {
        List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage() == null ? "Valor inválido." : error.getDefaultMessage()))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Revise os campos informados.");
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> persistenceError(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "O servidor não conseguiu gravar o currículo. Seus dados continuam no formulário."));
    }
}
