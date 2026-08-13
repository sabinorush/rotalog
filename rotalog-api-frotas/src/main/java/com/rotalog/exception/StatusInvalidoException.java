package com.rotalog.exception;

/**
 * Lançada quando o status informado não corresponde a um {@link com.rotalog.domain.StatusVeiculo} válido.
 */
public class StatusInvalidoException extends VeiculoException {

    public StatusInvalidoException(String message) {
        super(message);
    }
}
