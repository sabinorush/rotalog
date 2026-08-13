package com.rotalog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encapsula o envio de notificações para o gestor da frota com fallback
 * "engolir e logar" em caso de falha de comunicação com api-notificacoes.
 */
@Slf4j
@Component
public class NotificacaoFallbackAdapter {

    private static final String DESTINATARIO_GESTOR = "gestor@rotalog.com";

    @Autowired // FIXME: deveria usar injeção por construtor
    private NotificacaoClient notificacaoClient;

    public void notificarGestor(String tipo, String mensagem) {
        try {
            notificacaoClient.enviarNotificacao(tipo, DESTINATARIO_GESTOR, mensagem);
        } catch (Exception e) {
            log.error("Falha ao enviar notificação (tipo={}): {}", tipo, e.getMessage());
        }
    }
}
