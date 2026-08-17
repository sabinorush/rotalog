package com.rotalog.service;

import com.rotalog.dto.NotificacaoEnvioResponseDTO;
import com.rotalog.dto.ResultadoNotificacaoAlerta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * AlertaNotificacaoClient - HTTP client para envio de alertas de manutenção
 * preventiva para api-notificacoes.
 *
 * FIXME: URL hardcoded - mesma dívida técnica de NotificacaoClient/EntregaClient
 */
@Slf4j
@Component
public class AlertaNotificacaoClient {

    // FIXME: URL hardcoded - deveria estar em application.properties
    private static final String NOTIFICACAO_API_URL = "http://localhost:5000/api/notificacoes";

    private static final String STATUS_ENVIADO = "ENVIADO";

    private final RestTemplate restTemplate;

    public AlertaNotificacaoClient(RestTemplate notificacaoRestTemplate) {
        this.restTemplate = notificacaoRestTemplate;
    }

    /**
     * Envia um alerta de manutenção preventiva para api-notificacoes e traduz
     * o resultado (sucesso/falha/indisponibilidade) em {@link ResultadoNotificacaoAlerta}.
     */
    public ResultadoNotificacaoAlerta enviarAlerta(String destinatario, String mensagem) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("tipo", "ALERTA_MANUTENCAO");
            body.put("destinatario", destinatario);
            body.put("mensagem", mensagem);
            body.put("canal", "email");

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            log.info("Enviando alerta de manutenção para {}: destinatario={}", NOTIFICACAO_API_URL, destinatario);

            ResponseEntity<NotificacaoEnvioResponseDTO> response =
                    restTemplate.postForEntity(NOTIFICACAO_API_URL, request, NotificacaoEnvioResponseDTO.class);

            NotificacaoEnvioResponseDTO corpo = response.getBody();
            if (corpo != null && STATUS_ENVIADO.equals(corpo.getStatus())) {
                log.info("Alerta de manutenção enviado com sucesso: destinatario={}", destinatario);
                return ResultadoNotificacaoAlerta.enviado();
            }

            String erro = corpo != null ? corpo.getErroMensagem() : null;
            log.warn("api-notificacoes reportou falha no envio do alerta: destinatario={}, erro={}", destinatario, erro);
            return ResultadoNotificacaoAlerta.falha(erro);
        } catch (RestClientException e) {
            log.error("Falha de comunicação ao enviar alerta de manutenção: {}", e.getMessage());
            return ResultadoNotificacaoAlerta.pendente(e.getMessage());
        }
    }
}
