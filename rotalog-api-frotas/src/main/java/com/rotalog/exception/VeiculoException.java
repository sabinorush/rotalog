package com.rotalog.exception;

/**
 * Exceção base de domínio para erros relacionados a veículos.
 */
public class VeiculoException extends RuntimeException {

    public VeiculoException(String message) {
        super(message);
    }
}
