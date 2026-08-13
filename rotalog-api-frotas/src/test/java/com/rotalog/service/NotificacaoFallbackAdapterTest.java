package com.rotalog.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Testes unitários para {@link NotificacaoFallbackAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class NotificacaoFallbackAdapterTest {

    @Mock
    private NotificacaoClient notificacaoClient;

    @InjectMocks
    private NotificacaoFallbackAdapter notificacaoFallbackAdapter;

    @Test
    @DisplayName("deve enviar notificação para o gestor com o destinatário padrão")
    void deveNotificarGestor() {
        notificacaoFallbackAdapter.notificarGestor("NOVO_VEICULO", "mensagem qualquer");

        verify(notificacaoClient, times(1)).enviarNotificacao(
                eq("NOVO_VEICULO"), eq("gestor@rotalog.com"), eq("mensagem qualquer"));
    }

    @Test
    @DisplayName("deve engolir exceção quando o envio da notificação falhar")
    void deveEngolirExcecaoQuandoFalhar() {
        doThrow(new RuntimeException("Falha de comunicação"))
                .when(notificacaoClient).enviarNotificacao(anyString(), anyString(), anyString());

        notificacaoFallbackAdapter.notificarGestor("NOVO_VEICULO", "mensagem qualquer");

        verify(notificacaoClient, times(1)).enviarNotificacao(anyString(), anyString(), anyString());
    }
}
