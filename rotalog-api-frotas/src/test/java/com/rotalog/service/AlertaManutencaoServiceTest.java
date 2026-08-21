package com.rotalog.service;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.ResultadoNotificacaoAlerta;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários para {@link AlertaManutencaoService}.
 *
 * Mockito puro: mocka {@link ManutencaoService}, {@link AlertaNotificacaoClient},
 * {@link AlertaManutencaoRepository} e {@link VeiculoRepository}.
 */
@ExtendWith(MockitoExtension.class)
class AlertaManutencaoServiceTest {

    @Mock
    private ManutencaoService manutencaoService;

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private AlertaNotificacaoClient alertaNotificacaoClient;

    @Mock
    private AlertaManutencaoRepository alertaManutencaoRepository;

    @InjectMocks
    private AlertaManutencaoService alertaManutencaoService;

    private Veiculo criarVeiculo(Long id, String placa, Long km, StatusVeiculo status) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(id);
        veiculo.setPlaca(placa);
        veiculo.setModelo("Fiat Uno");
        veiculo.setAnoFabricacao(2020);
        veiculo.setQuilometragem(km);
        veiculo.setStatus(status);
        return veiculo;
    }

    private void mockSalvarAlertaEcoando() {
        when(alertaManutencaoRepository.save(any(AlertaManutencao.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("verificarEEmitirAlertas")
    class VerificarEEmitirAlertas {

        @Test
        @DisplayName("deve retornar lista vazia e não interagir com o client quando não houver veículos elegíveis")
        void deveRetornarListaVaziaQuandoNenhumElegivel() {
            when(manutencaoService.obterVeiculosElegiveisParaAlerta()).thenReturn(Collections.emptyList());

            List<AlertaManutencao> resultado = alertaManutencaoService.verificarEEmitirAlertas();

            assertThat(resultado).isEmpty();
            verify(alertaNotificacaoClient, never()).enviarAlerta(anyString(), anyString(), anyString());
            verify(alertaManutencaoRepository, never()).save(any(AlertaManutencao.class));
        }

        @Test
        @DisplayName("deve persistir alerta com status ENVIADA quando o client confirmar o envio")
        void devePersistirComStatusEnviada() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 60000L, StatusVeiculo.ATIVO);
            when(manutencaoService.obterVeiculosElegiveisParaAlerta()).thenReturn(Collections.singletonList(veiculo));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoService.motivoAlerta(1L)).thenReturn("KM_EXCEDIDO");
            when(alertaNotificacaoClient.enviarAlerta(eq("gestor@rotalog.com"), anyString(), eq("veiculo-1")))
                    .thenReturn(ResultadoNotificacaoAlerta.enviado());
            mockSalvarAlertaEcoando();

            List<AlertaManutencao> resultado = alertaManutencaoService.verificarEEmitirAlertas();

            assertThat(resultado).hasSize(1);
            AlertaManutencao alerta = resultado.get(0);
            assertThat(alerta.getVeiculoId()).isEqualTo(1L);
            assertThat(alerta.getMotivo()).isEqualTo("KM_EXCEDIDO");
            assertThat(alerta.getStatus()).isEqualTo(StatusAlerta.ENVIADA);
            assertThat(alerta.getMensagemErro()).isNull();
            assertThat(alerta.getDataCriacao()).isNotNull();
            assertThat(alerta.getDataAtualizacao()).isNotNull();
            verify(alertaNotificacaoClient).enviarAlerta(eq("gestor@rotalog.com"), anyString(), eq("veiculo-1"));
        }

        @Test
        @DisplayName("deve persistir alerta com status FALHA e mensagem de erro quando o client reportar falha")
        void devePersistirComStatusFalha() {
            Veiculo veiculo = criarVeiculo(2L, "DEF5678", 1000L, StatusVeiculo.ATIVO);
            when(manutencaoService.obterVeiculosElegiveisParaAlerta()).thenReturn(Collections.singletonList(veiculo));
            when(veiculoRepository.findById(2L)).thenReturn(Optional.of(veiculo));
            when(manutencaoService.motivoAlerta(2L)).thenReturn("TEMPO_EXCEDIDO");
            when(alertaNotificacaoClient.enviarAlerta(eq("gestor@rotalog.com"), anyString(), eq("veiculo-2")))
                    .thenReturn(ResultadoNotificacaoAlerta.falha("destinatario invalido"));
            mockSalvarAlertaEcoando();

            List<AlertaManutencao> resultado = alertaManutencaoService.verificarEEmitirAlertas();

            assertThat(resultado).hasSize(1);
            AlertaManutencao alerta = resultado.get(0);
            assertThat(alerta.getStatus()).isEqualTo(StatusAlerta.FALHA);
            assertThat(alerta.getMensagemErro()).isEqualTo("destinatario invalido");
        }

        @Test
        @DisplayName("deve persistir alerta com status PENDENTE quando a chamada HTTP falhar")
        void devePersistirComStatusPendente() {
            Veiculo veiculo = criarVeiculo(3L, "GHI9012", 60000L, StatusVeiculo.ATIVO);
            when(manutencaoService.obterVeiculosElegiveisParaAlerta()).thenReturn(Collections.singletonList(veiculo));
            when(veiculoRepository.findById(3L)).thenReturn(Optional.of(veiculo));
            when(manutencaoService.motivoAlerta(3L)).thenReturn("KM_E_TEMPO_EXCEDIDOS");
            when(alertaNotificacaoClient.enviarAlerta(eq("gestor@rotalog.com"), anyString(), eq("veiculo-3")))
                    .thenReturn(ResultadoNotificacaoAlerta.pendente("Connection refused"));
            mockSalvarAlertaEcoando();

            List<AlertaManutencao> resultado = alertaManutencaoService.verificarEEmitirAlertas();

            assertThat(resultado).hasSize(1);
            AlertaManutencao alerta = resultado.get(0);
            assertThat(alerta.getStatus()).isEqualTo(StatusAlerta.PENDENTE);
            assertThat(alerta.getMensagemErro()).isEqualTo("Connection refused");
        }

        @Test
        @DisplayName("deve usar o veículo já elegível como fallback quando VeiculoRepository não encontrar o id")
        void deveUsarFallbackQuandoVeiculoRepositoryNaoEncontrar() {
            Veiculo veiculo = criarVeiculo(4L, "JKL3456", 60000L, StatusVeiculo.ATIVO);
            when(manutencaoService.obterVeiculosElegiveisParaAlerta()).thenReturn(Collections.singletonList(veiculo));
            when(veiculoRepository.findById(4L)).thenReturn(Optional.empty());
            when(manutencaoService.motivoAlerta(4L)).thenReturn("KM_EXCEDIDO");
            when(alertaNotificacaoClient.enviarAlerta(eq("gestor@rotalog.com"), anyString(), eq("veiculo-4")))
                    .thenReturn(ResultadoNotificacaoAlerta.enviado());
            mockSalvarAlertaEcoando();

            List<AlertaManutencao> resultado = alertaManutencaoService.verificarEEmitirAlertas();

            assertThat(resultado).hasSize(1);
            ArgumentCaptor<String> mensagemCaptor = ArgumentCaptor.forClass(String.class);
            verify(alertaNotificacaoClient).enviarAlerta(eq("gestor@rotalog.com"), mensagemCaptor.capture(), eq("veiculo-4"));
            assertThat(mensagemCaptor.getValue()).contains("JKL3456");
        }

        @Test
        @DisplayName("deve criar um alerta para cada veículo elegível, na ordem retornada")
        void deveCriarUmAlertaPorVeiculoElegivel() {
            Veiculo veiculo1 = criarVeiculo(1L, "AAA1111", 60000L, StatusVeiculo.ATIVO);
            Veiculo veiculo2 = criarVeiculo(2L, "BBB2222", 1000L, StatusVeiculo.ATIVO);
            when(manutencaoService.obterVeiculosElegiveisParaAlerta()).thenReturn(Arrays.asList(veiculo1, veiculo2));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo1));
            when(veiculoRepository.findById(2L)).thenReturn(Optional.of(veiculo2));
            when(manutencaoService.motivoAlerta(1L)).thenReturn("KM_EXCEDIDO");
            when(manutencaoService.motivoAlerta(2L)).thenReturn("TEMPO_EXCEDIDO");
            when(alertaNotificacaoClient.enviarAlerta(eq("gestor@rotalog.com"), anyString(), anyString()))
                    .thenReturn(ResultadoNotificacaoAlerta.enviado())
                    .thenReturn(ResultadoNotificacaoAlerta.falha("erro qualquer"));
            mockSalvarAlertaEcoando();

            List<AlertaManutencao> resultado = alertaManutencaoService.verificarEEmitirAlertas();

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).getVeiculoId()).isEqualTo(1L);
            assertThat(resultado.get(0).getStatus()).isEqualTo(StatusAlerta.ENVIADA);
            assertThat(resultado.get(1).getVeiculoId()).isEqualTo(2L);
            assertThat(resultado.get(1).getStatus()).isEqualTo(StatusAlerta.FALHA);
            verify(alertaManutencaoRepository, times(2)).save(any(AlertaManutencao.class));
            verify(alertaNotificacaoClient).enviarAlerta(eq("gestor@rotalog.com"), anyString(), eq("veiculo-1"));
            verify(alertaNotificacaoClient).enviarAlerta(eq("gestor@rotalog.com"), anyString(), eq("veiculo-2"));
        }
    }
}
