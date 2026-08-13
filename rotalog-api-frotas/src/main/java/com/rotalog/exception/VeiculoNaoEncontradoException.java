package com.rotalog.exception;

/**
 * Lançada quando um veículo não é encontrado pelo identificador ou placa informados.
 */
public class VeiculoNaoEncontradoException extends VeiculoException {

    public VeiculoNaoEncontradoException(String message) {
        super(message);
    }
}
