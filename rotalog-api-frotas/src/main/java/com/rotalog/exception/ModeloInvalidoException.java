package com.rotalog.exception;

/**
 * Lançada quando o modelo informado é nulo ou vazio.
 */
public class ModeloInvalidoException extends VeiculoException {

    public ModeloInvalidoException(String message) {
        super(message);
    }
}
