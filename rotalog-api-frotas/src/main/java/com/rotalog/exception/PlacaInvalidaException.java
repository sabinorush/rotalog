package com.rotalog.exception;

/**
 * Lançada quando a placa informada é nula, vazia ou possui formato inválido.
 */
public class PlacaInvalidaException extends VeiculoException {

    public PlacaInvalidaException(String message) {
        super(message);
    }
}
