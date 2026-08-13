package com.rotalog.exception;

/**
 * Lançada quando a quilometragem informada é inválida (ex.: negativa).
 */
public class QuilometragemInvalidaException extends VeiculoException {

    public QuilometragemInvalidaException(String message) {
        super(message);
    }
}
