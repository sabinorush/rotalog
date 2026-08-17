package com.rotalog.service;

import com.rotalog.domain.StatusAlerta;
import com.rotalog.dto.ResultadoNotificacaoAlerta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * Testes unitários para {@link AlertaNotificacaoClient}.
 *
 * Usa {@link MockRestServiceServer} vinculado a um {@link RestTemplate} real,
 * já que a testabilidade da classe foi desenhada especificamente para isso
 * (RestTemplate injetado via construtor, não instanciado inline).
 */
class AlertaNotificacaoClientTest {

    private static final String URL = "http://localhost:5000/api/notificacoes";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private AlertaNotificacaoClient alertaNotificacaoClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplateBuilder().build();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        alertaNotificacaoClient = new AlertaNotificacaoClient(restTemplate);
    }

    @Test
    @DisplayName("deve retornar resultado ENVIADA quando api-notificacoes confirmar o envio")
    void deveRetornarEnviadaQuandoStatusEnviado() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"status\":\"ENVIADO\",\"erroMensagem\":null}", MediaType.APPLICATION_JSON));

        ResultadoNotificacaoAlerta resultado = alertaNotificacaoClient.enviarAlerta("gestor@rotalog.com", "mensagem qualquer");

        assertThat(resultado.getStatus()).isEqualTo(StatusAlerta.ENVIADA);
        assertThat(resultado.getMensagemErro()).isNull();
        mockServer.verify();
    }

    @Test
    @DisplayName("deve retornar resultado FALHA quando api-notificacoes reportar falha no envio")
    void deveRetornarFalhaQuandoStatusNaoEnviado() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"status\":\"FALHA\",\"erroMensagem\":\"destinatario invalido\"}", MediaType.APPLICATION_JSON));

        ResultadoNotificacaoAlerta resultado = alertaNotificacaoClient.enviarAlerta("gestor@rotalog.com", "mensagem qualquer");

        assertThat(resultado.getStatus()).isEqualTo(StatusAlerta.FALHA);
        assertThat(resultado.getMensagemErro()).isEqualTo("destinatario invalido");
        mockServer.verify();
    }

    @Test
    @DisplayName("deve retornar resultado PENDENTE quando a chamada HTTP falhar (conexão recusada/timeout)")
    void deveRetornarPendenteQuandoChamadaHttpFalhar() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(POST))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        ResultadoNotificacaoAlerta resultado = alertaNotificacaoClient.enviarAlerta("gestor@rotalog.com", "mensagem qualquer");

        assertThat(resultado.getStatus()).isEqualTo(StatusAlerta.PENDENTE);
        assertThat(resultado.getMensagemErro()).isNotNull();
        mockServer.verify();
    }

    @Test
    @DisplayName("deve retornar resultado PENDENTE quando api-notificacoes responder com erro de servidor")
    void deveRetornarPendenteQuandoServidorResponderComErro() {
        mockServer.expect(requestTo(URL))
                .andExpect(method(POST))
                .andRespond(withServerError());

        ResultadoNotificacaoAlerta resultado = alertaNotificacaoClient.enviarAlerta("gestor@rotalog.com", "mensagem qualquer");

        assertThat(resultado.getStatus()).isEqualTo(StatusAlerta.PENDENTE);
        assertThat(resultado.getMensagemErro()).isNotNull();
        mockServer.verify();
    }
}
