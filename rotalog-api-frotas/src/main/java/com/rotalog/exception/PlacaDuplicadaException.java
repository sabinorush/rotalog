package com.rotalog.exception;

/**
 * Lançada quando já existe um veículo cadastrado com a placa informada.
 */
public class PlacaDuplicadaException extends VeiculoException {

    public PlacaDuplicadaException(String message) {
        super(message);
    }
}
