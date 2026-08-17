package com.rotalog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Testes unitários para {@link SistemaExternoClient} (retry e circuit breaker).
 *
 * Usa {@link MockRestServiceServer} vinculado a um {@link RestTemplate} real,
 * mesmo padrão de {@link AlertaNotificacaoClientTest}.
 */
class SistemaExternoClientTest {

    private static final String URL = "http://localhost:9090/api/sistema-externo/sincronizar";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private SistemaExternoClient sistemaExternoClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplateBuilder().build();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        sistemaExternoClient = new SistemaExternoClient(restTemplate);
    }

    @Test
    @DisplayName("deve retornar true quando a primeira tentativa tiver sucesso")
    void deveRetornarTrueQuandoPrimeiraTentativaTemSucesso() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(POST))
                .andRespond(withSuccess());

        boolean resultado = sistemaExternoClient.sincronizar();

        assertThat(resultado).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("deve tentar novamente após falha e retornar true quando uma tentativa seguinte tiver sucesso")
    void deveTentarNovamenteERetornarTrueAposSucessoEmTentativaPosterior() {
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(withServerError());
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(withSuccess());

        boolean resultado = sistemaExternoClient.sincronizar();

        assertThat(resultado).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("deve retornar false após esgotar todas as tentativas com falha")
    void deveRetornarFalseAposEsgotarTentativas() {
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(withServerError());
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(withServerError());
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(withServerError());

        boolean resultado = sistemaExternoClient.sincronizar();

        assertThat(resultado).isFalse();
        mockServer.verify();
    }

    @Test
    @DisplayName("deve retornar false quando a chamada HTTP falhar por erro de conexão")
    void deveRetornarFalseQuandoChamadaHttpFalhar() {
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(request -> {
            throw new IOException("Connection refused");
        });
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(request -> {
            throw new IOException("Connection refused");
        });
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(request -> {
            throw new IOException("Connection refused");
        });

        boolean resultado = sistemaExternoClient.sincronizar();

        assertThat(resultado).isFalse();
        mockServer.verify();
    }

    @Test
    @DisplayName("deve abrir o circuito após esgotar as tentativas e pular a chamada HTTP na próxima sincronização")
    void deveAbrirCircuitoAposEsgotarTentativasEPularProximaChamada() {
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(withServerError());
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(withServerError());
        mockServer.expect(requestTo(URL)).andExpect(method(POST)).andRespond(withServerError());

        boolean primeiraChamada = sistemaExternoClient.sincronizar();
        assertThat(primeiraChamada).isFalse();

        // Circuito deve estar aberto agora - nenhuma nova requisição HTTP deve ser feita.
        // Se o client tentasse chamar o servidor mesmo assim, mockServer.verify() falharia
        // por não haver mais expectativas registradas.
        boolean segundaChamada = sistemaExternoClient.sincronizar();

        assertThat(segundaChamada).isFalse();
        mockServer.verify();
    }
}
