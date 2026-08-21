package com.rotalog.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO com os campos de interesse da resposta de POST /api/notificacoes
 * (rotalog-api-notificacoes, .NET Core / System.Text.Json com camelCase padrão).
 */
@Getter
@Setter
public class NotificacaoEnvioResponseDTO {
    private String status;
    private String erroMensagem;
}
