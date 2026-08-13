package com.rotalog.exception;

/**
 * Lançada quando o ano de fabricação informado é nulo ou está fora do intervalo aceito.
 */
public class AnoFabricacaoInvalidoException extends VeiculoException {

    public AnoFabricacaoInvalidoException(String message) {
        super(message);
    }
}
