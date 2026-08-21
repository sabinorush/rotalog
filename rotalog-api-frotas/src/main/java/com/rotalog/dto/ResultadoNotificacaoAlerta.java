package com.rotalog.dto;

import com.rotalog.domain.StatusAlerta;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Resultado do envio de um alerta de manutenção preventiva para api-notificacoes.
 */
@Getter
@Setter
@AllArgsConstructor
public class ResultadoNotificacaoAlerta {

    private StatusAlerta status;
    private String mensagemErro;

    public static ResultadoNotificacaoAlerta enviado() {
        return new ResultadoNotificacaoAlerta(StatusAlerta.ENVIADA, null);
    }

    public static ResultadoNotificacaoAlerta falha(String mensagemErro) {
        return new ResultadoNotificacaoAlerta(StatusAlerta.FALHA, mensagemErro);
    }

    public static ResultadoNotificacaoAlerta pendente(String mensagemErro) {
        return new ResultadoNotificacaoAlerta(StatusAlerta.PENDENTE, mensagemErro);
    }
}
