package com.rotalog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * SistemaExternoClient - HTTP client para o sistema externo de sincronização de frota
 * usado por {@link VeiculoService#sincronizarComSistemaExterno()}.
 *
 * FIXME: URL hardcoded - mesma dívida técnica de NotificacaoClient/EntregaClient
 * FIXME: Retry e circuit breaker implementados manualmente em vez de Resilience4j -
 * decisão deliberada para não introduzir novas dependências no pom.xml (curso)
 * FIXME: Estado do circuit breaker em campos de instância, sem sincronização -
 * não é thread-safe sob concorrência real
 */
@Slf4j
@Component
public class SistemaExternoClient {

    // FIXME: URL hardcoded - deveria estar em application.properties
    private static final String SISTEMA_EXTERNO_URL = "http://localhost:9090/api/sistema-externo/sincronizar";

    private static final int MAX_TENTATIVAS = 3;
    private static final long DELAY_ENTRE_TENTATIVAS_MS = 50;

    private static final int LIMITE_FALHAS_CONSECUTIVAS = 3;
    private static final long JANELA_CIRCUITO_ABERTO_MS = 30_000;

    private final RestTemplate restTemplate;

    private int falhasConsecutivas = 0;
    private Instant circuitoAbertoDesde;

    public SistemaExternoClient(RestTemplate notificacaoRestTemplate) {
        this.restTemplate = notificacaoRestTemplate;
    }

    /**
     * Sincroniza a frota com o sistema externo, com retry (até {@value #MAX_TENTATIVAS}
     * tentativas) e circuit breaker (abre após {@value #LIMITE_FALHAS_CONSECUTIVAS} falhas
     * consecutivas, permanecendo aberto por {@value #JANELA_CIRCUITO_ABERTO_MS}ms).
     *
     * @return {@code true} se a sincronização foi concluída com sucesso, {@code false} caso
     * contrário (falha após todas as tentativas ou circuito aberto). Nunca lança exceção -
     * mesma política de fallback dos demais clients de integração deste serviço.
     */
    public boolean sincronizar() {
        if (circuitoEstaAberto()) {
            log.warn("Circuito aberto - sincronização com sistema externo pulada (fail fast)");
            return false;
        }

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                restTemplate.postForEntity(SISTEMA_EXTERNO_URL, null, String.class);
                registrarSucesso();
                log.info("Sincronização com sistema externo concluída (tentativa {}/{})", tentativa, MAX_TENTATIVAS);
                return true;
            } catch (RestClientException e) {
                log.warn("Falha na tentativa {}/{} de sincronização com sistema externo: {}",
                        tentativa, MAX_TENTATIVAS, e.getMessage());
                registrarFalha();
                if (tentativa < MAX_TENTATIVAS) {
                    aguardar(DELAY_ENTRE_TENTATIVAS_MS * tentativa);
                }
            }
        }

        log.error("Sincronização com sistema externo falhou após {} tentativas", MAX_TENTATIVAS);
        return false;
    }

    private boolean circuitoEstaAberto() {
        if (circuitoAbertoDesde == null) {
            return false;
        }
        if (Instant.now().isAfter(circuitoAbertoDesde.plusMillis(JANELA_CIRCUITO_ABERTO_MS))) {
            // Janela expirou - permite uma nova tentativa (half-open simplificado)
            circuitoAbertoDesde = null;
            falhasConsecutivas = 0;
            return false;
        }
        return true;
    }

    private void registrarSucesso() {
        falhasConsecutivas = 0;
        circuitoAbertoDesde = null;
    }

    private void registrarFalha() {
        falhasConsecutivas++;
        if (falhasConsecutivas >= LIMITE_FALHAS_CONSECUTIVAS && circuitoAbertoDesde == null) {
            circuitoAbertoDesde = Instant.now();
            log.error("Circuito aberto após {} falhas consecutivas na sincronização com sistema externo", falhasConsecutivas);
        }
    }

    private void aguardar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
