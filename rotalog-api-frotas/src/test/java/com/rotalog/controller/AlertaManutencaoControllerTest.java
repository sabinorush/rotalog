package com.rotalog.controller;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.service.AlertaManutencaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração (slice) do {@link AlertaManutencaoController} com
 * {@code @WebMvcTest}: {@link AlertaManutencaoService} e
 * {@link AlertaManutencaoRepository} são mockados via {@code @MockBean} e as
 * chamadas HTTP são exercitadas com {@link MockMvc}.
 */
@WebMvcTest(AlertaManutencaoController.class)
class AlertaManutencaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertaManutencaoService alertaManutencaoService;

    @MockBean
    private AlertaManutencaoRepository alertaManutencaoRepository;

    private AlertaManutencao criarAlerta(Long id, Long veiculoId, String motivo, StatusAlerta status) {
        AlertaManutencao alerta = new AlertaManutencao();
        alerta.setId(id);
        alerta.setVeiculoId(veiculoId);
        alerta.setMotivo(motivo);
        alerta.setStatus(status);
        alerta.setDataCriacao(LocalDateTime.now());
        alerta.setDataAtualizacao(LocalDateTime.now());
        return alerta;
    }

    @Nested
    @DisplayName("POST /alertas-manutencao/verificar")
    class Verificar {

        @Test
        @DisplayName("deve retornar 200 com a lista de alertas criados quando o service não lançar exceção")
        void deveRetornar200ComListaDeAlertas() throws Exception {
            AlertaManutencao alerta = criarAlerta(1L, 10L, "KM_EXCEDIDO", StatusAlerta.ENVIADA);
            when(alertaManutencaoService.verificarEEmitirAlertas()).thenReturn(Collections.singletonList(alerta));

            mockMvc.perform(post("/alertas-manutencao/verificar"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                    .andExpect(jsonPath("$[0].veiculoId").value(10))
                    .andExpect(jsonPath("$[0].motivo").value("KM_EXCEDIDO"))
                    .andExpect(jsonPath("$[0].status").value("ENVIADA"));
        }

        @Test
        @DisplayName("deve retornar 200 com lista vazia quando não houver veículos elegíveis")
        void deveRetornar200ComListaVazia() throws Exception {
            when(alertaManutencaoService.verificarEEmitirAlertas()).thenReturn(Collections.emptyList());

            mockMvc.perform(post("/alertas-manutencao/verificar"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        }

        @Test
        @DisplayName("deve retornar 400 com o corpo de erro quando o service lançar RuntimeException")
        void deveRetornar400QuandoServiceLancarRuntimeException() throws Exception {
            when(alertaManutencaoService.verificarEEmitirAlertas())
                    .thenThrow(new RuntimeException("falha ao verificar alertas"));

            mockMvc.perform(post("/alertas-manutencao/verificar"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro").value("falha ao verificar alertas"));
        }
    }

    @Nested
    @DisplayName("GET /alertas-manutencao")
    class Listar {

        @Test
        @DisplayName("deve retornar 200 com todos os alertas quando nenhum status for informado")
        void deveRetornarTodosAlertasQuandoSemFiltro() throws Exception {
            AlertaManutencao alerta1 = criarAlerta(1L, 10L, "KM_EXCEDIDO", StatusAlerta.ENVIADA);
            AlertaManutencao alerta2 = criarAlerta(2L, 20L, "TEMPO_EXCEDIDO", StatusAlerta.FALHA);
            when(alertaManutencaoRepository.findAll()).thenReturn(Arrays.asList(alerta1, alerta2));

            mockMvc.perform(get("/alertas-manutencao"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

            verify(alertaManutencaoRepository).findAll();
            verify(alertaManutencaoRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("deve retornar 200 filtrando por status quando o parâmetro status for informado")
        void deveFiltrarPorStatusQuandoParametroInformado() throws Exception {
            AlertaManutencao alerta = criarAlerta(1L, 10L, "KM_EXCEDIDO", StatusAlerta.PENDENTE);
            List<AlertaManutencao> pendentes = Collections.singletonList(alerta);
            when(alertaManutencaoRepository.findByStatus(StatusAlerta.PENDENTE)).thenReturn(pendentes);

            mockMvc.perform(get("/alertas-manutencao").param("status", "PENDENTE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                    .andExpect(jsonPath("$[0].status").value("PENDENTE"));

            verify(alertaManutencaoRepository).findByStatus(StatusAlerta.PENDENTE);
            verify(alertaManutencaoRepository, never()).findAll();
        }

        @Test
        @DisplayName("deve retornar 400 com o corpo de erro quando o repository lançar RuntimeException")
        void deveRetornar400QuandoRepositoryLancarRuntimeException() throws Exception {
            when(alertaManutencaoRepository.findAll()).thenThrow(new RuntimeException("erro de acesso ao banco"));

            mockMvc.perform(get("/alertas-manutencao"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro").value("erro de acesso ao banco"));
        }
    }
}
