package com.rotalog.service;

import com.rotalog.domain.Manutencao;
import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.exception.VeiculoNaoEncontradoException;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários para {@link ManutencaoService}.
 *
 * Cobre tanto o comportamento pré-existente da classe quanto os métodos
 * migrados de {@link VeiculoService} (ver docs/adr/0001-refatoracao-veiculoservice.md):
 * {@code calcularCustoManutencao}, {@code precisaDeManutencao},
 * {@code agendarManutencaoPreventiva} e o helper consolidado {@code buscarVeiculoOuFalhar}.
 */
@ExtendWith(MockitoExtension.class)
class ManutencaoServiceTest {

    @Mock
    private ManutencaoRepository manutencaoRepository;

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private NotificacaoClient notificacaoClient;

    @Mock
    private NotificacaoFallbackAdapter notificacaoFallbackAdapter;

    @InjectMocks
    private ManutencaoService manutencaoService;

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

    private Manutencao criarManutencao(Long id, Long veiculoId, String status) {
        Manutencao manutencao = new Manutencao();
        manutencao.setId(id);
        manutencao.setVeiculoId(veiculoId);
        manutencao.setTipoManutencao("PREVENTIVA");
        manutencao.setStatus(status);
        manutencao.setCusto(BigDecimal.TEN);
        return manutencao;
    }

    @Nested
    @DisplayName("listarTodas")
    class ListarTodas {

        @Test
        @DisplayName("deve retornar todas as manutenções do repositório")
        void deveListarTodas() {
            List<Manutencao> manutencoes = Arrays.asList(
                    criarManutencao(1L, 1L, "PENDENTE"),
                    criarManutencao(2L, 2L, "CONCLUIDA"));
            when(manutencaoRepository.findAll()).thenReturn(manutencoes);

            List<Manutencao> resultado = manutencaoService.listarTodas();

            assertThat(resultado).isEqualTo(manutencoes);
        }
    }

    @Nested
    @DisplayName("buscarPorId")
    class BuscarPorId {

        @Test
        @DisplayName("deve retornar a manutenção quando encontrada")
        void deveRetornarQuandoEncontrada() {
            Manutencao manutencao = criarManutencao(1L, 1L, "PENDENTE");
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));

            Manutencao resultado = manutencaoService.buscarPorId(1L);

            assertThat(resultado).isEqualTo(manutencao);
        }

        @Test
        @DisplayName("deve lançar exceção quando não encontrada")
        void deveLancarExcecaoQuandoNaoEncontrada() {
            when(manutencaoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> manutencaoService.buscarPorId(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Manutenção não encontrada: 99");
        }
    }

    @Nested
    @DisplayName("listarPorVeiculo")
    class ListarPorVeiculo {

        @Test
        @DisplayName("deve retornar manutenções do veículo informado")
        void deveListarPorVeiculo() {
            List<Manutencao> manutencoes = Collections.singletonList(criarManutencao(1L, 1L, "PENDENTE"));
            when(manutencaoRepository.findByVeiculoId(1L)).thenReturn(manutencoes);

            List<Manutencao> resultado = manutencaoService.listarPorVeiculo(1L);

            assertThat(resultado).isEqualTo(manutencoes);
        }
    }

    @Nested
    @DisplayName("agendarManutencao")
    class AgendarManutencao {

        @Test
        @DisplayName("deve agendar manutenção e notificar a oficina")
        void deveAgendarComSucesso() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Manutencao resultado = manutencaoService.agendarManutencao(1L, "PREVENTIVA", "Revisão geral", BigDecimal.valueOf(300));

            assertThat(resultado.getVeiculoId()).isEqualTo(1L);
            assertThat(resultado.getTipoManutencao()).isEqualTo("PREVENTIVA");
            assertThat(resultado.getStatus()).isEqualTo("PENDENTE");
            assertThat(resultado.getQuilometragemManutencao()).isEqualTo(1000L);
            verify(notificacaoClient, times(1)).enviarNotificacao(
                    eq("MANUTENCAO_AGENDADA"), eq("oficina@rotalog.com"), anyString());
        }

        @Test
        @DisplayName("deve lançar VeiculoNaoEncontradoException quando veículo não existir")
        void deveLancarExcecaoQuandoVeiculoNaoExiste() {
            when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> manutencaoService.agendarManutencao(99L, "PREVENTIVA", "desc", BigDecimal.TEN))
                    .isInstanceOf(VeiculoNaoEncontradoException.class)
                    .hasMessageContaining("Veículo não encontrado: 99");
        }

        @Test
        @DisplayName("deve lançar exceção quando tipo de manutenção for nulo")
        void deveLancarExcecaoQuandoTipoNulo() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

            assertThatThrownBy(() -> manutencaoService.agendarManutencao(1L, null, "desc", BigDecimal.TEN))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Tipo de manutenção é obrigatório");
        }

        @Test
        @DisplayName("deve lançar exceção quando tipo de manutenção for vazio")
        void deveLancarExcecaoQuandoTipoVazio() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

            assertThatThrownBy(() -> manutencaoService.agendarManutencao(1L, "   ", "desc", BigDecimal.TEN))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Tipo de manutenção é obrigatório");
        }

        @Test
        @DisplayName("deve engolir exceção quando notificação para oficina falhar")
        void deveEngolirExcecaoQuandoNotificacaoFalhar() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));
            doThrow(new RuntimeException("Falha de comunicação"))
                    .when(notificacaoClient).enviarNotificacao(anyString(), anyString(), anyString());

            Manutencao resultado = manutencaoService.agendarManutencao(1L, "PREVENTIVA", "desc", BigDecimal.TEN);

            assertThat(resultado).isNotNull();
        }
    }

    @Nested
    @DisplayName("iniciarManutencao")
    class IniciarManutencao {

        @Test
        @DisplayName("deve iniciar manutenção PENDENTE e colocar veículo em MANUTENCAO")
        void deveIniciarManutencaoPendente() {
            Manutencao manutencao = criarManutencao(1L, 1L, "PENDENTE");
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Manutencao resultado = manutencaoService.iniciarManutencao(1L);

            assertThat(resultado.getStatus()).isEqualTo("EM_ANDAMENTO");
            assertThat(resultado.getDataManutencao()).isNotNull();
            assertThat(veiculo.getStatus()).isEqualTo(StatusVeiculo.MANUTENCAO);
            verify(veiculoRepository, times(1)).save(veiculo);
        }

        @Test
        @DisplayName("deve iniciar mesmo quando manutenção não está PENDENTE, apenas logando aviso")
        void deveIniciarQuandoNaoPendente() {
            Manutencao manutencao = criarManutencao(1L, 1L, "CONCLUIDA");
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Manutencao resultado = manutencaoService.iniciarManutencao(1L);

            assertThat(resultado.getStatus()).isEqualTo("EM_ANDAMENTO");
        }

        @Test
        @DisplayName("deve iniciar manutenção mesmo quando veículo associado não existe")
        void deveIniciarQuandoVeiculoNaoExiste() {
            Manutencao manutencao = criarManutencao(1L, 99L, "PENDENTE");
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));
            when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Manutencao resultado = manutencaoService.iniciarManutencao(1L);

            assertThat(resultado.getStatus()).isEqualTo("EM_ANDAMENTO");
            verify(veiculoRepository, never()).save(any(Veiculo.class));
        }
    }

    @Nested
    @DisplayName("concluirManutencao")
    class ConcluirManutencao {

        @Test
        @DisplayName("deve concluir manutenção, reativar veículo e notificar gestor")
        void deveConcluirComSucesso() {
            Manutencao manutencao = criarManutencao(1L, 1L, "EM_ANDAMENTO");
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.MANUTENCAO);
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Manutencao resultado = manutencaoService.concluirManutencao(1L, BigDecimal.valueOf(450));

            assertThat(resultado.getStatus()).isEqualTo("CONCLUIDA");
            assertThat(resultado.getCusto()).isEqualTo(BigDecimal.valueOf(450));
            assertThat(veiculo.getStatus()).isEqualTo(StatusVeiculo.ATIVO);
            verify(notificacaoClient, times(1)).enviarNotificacao(
                    eq("MANUTENCAO_CONCLUIDA"), eq("gestor@rotalog.com"), anyString());
        }

        @Test
        @DisplayName("não deve sobrescrever custo quando custoFinal for nulo")
        void naoDeveAlterarCustoQuandoNulo() {
            Manutencao manutencao = criarManutencao(1L, 1L, "EM_ANDAMENTO");
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.MANUTENCAO);
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Manutencao resultado = manutencaoService.concluirManutencao(1L, null);

            assertThat(resultado.getCusto()).isEqualTo(BigDecimal.TEN);
        }

        @Test
        @DisplayName("deve concluir mesmo quando veículo associado não existe")
        void deveConcluirQuandoVeiculoNaoExiste() {
            Manutencao manutencao = criarManutencao(1L, 99L, "EM_ANDAMENTO");
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));
            when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Manutencao resultado = manutencaoService.concluirManutencao(1L, BigDecimal.TEN);

            assertThat(resultado.getStatus()).isEqualTo("CONCLUIDA");
            verify(notificacaoClient, times(1)).enviarNotificacao(
                    eq("MANUTENCAO_CONCLUIDA"), eq("gestor@rotalog.com"), anyString());
        }

        @Test
        @DisplayName("deve engolir exceção quando notificação de conclusão falhar")
        void deveEngolirExcecaoQuandoNotificacaoFalhar() {
            Manutencao manutencao = criarManutencao(1L, 1L, "EM_ANDAMENTO");
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.MANUTENCAO);
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));
            doThrow(new RuntimeException("Falha de comunicação"))
                    .when(notificacaoClient).enviarNotificacao(anyString(), anyString(), anyString());

            Manutencao resultado = manutencaoService.concluirManutencao(1L, BigDecimal.TEN);

            assertThat(resultado.getStatus()).isEqualTo("CONCLUIDA");
        }
    }

    @Nested
    @DisplayName("cancelarManutencao")
    class CancelarManutencao {

        @Test
        @DisplayName("deve reativar veículo quando ele estava em MANUTENCAO")
        void deveReativarVeiculoEmManutencao() {
            Manutencao manutencao = criarManutencao(1L, 1L, "PENDENTE");
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.MANUTENCAO);
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Manutencao resultado = manutencaoService.cancelarManutencao(1L);

            assertThat(resultado.getStatus()).isEqualTo("CANCELADA");
            assertThat(veiculo.getStatus()).isEqualTo(StatusVeiculo.ATIVO);
            verify(veiculoRepository, times(1)).save(veiculo);
        }

        @Test
        @DisplayName("não deve alterar veículo quando ele não estava em MANUTENCAO")
        void naoDeveAlterarVeiculoQuandoNaoEstavaEmManutencao() {
            Manutencao manutencao = criarManutencao(1L, 1L, "PENDENTE");
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Manutencao resultado = manutencaoService.cancelarManutencao(1L);

            assertThat(resultado.getStatus()).isEqualTo("CANCELADA");
            assertThat(veiculo.getStatus()).isEqualTo(StatusVeiculo.ATIVO);
            verify(veiculoRepository, never()).save(any(Veiculo.class));
        }

        @Test
        @DisplayName("deve cancelar mesmo quando veículo associado não existe")
        void deveCancelarQuandoVeiculoNaoExiste() {
            Manutencao manutencao = criarManutencao(1L, 99L, "PENDENTE");
            when(manutencaoRepository.findById(1L)).thenReturn(Optional.of(manutencao));
            when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());
            when(manutencaoRepository.save(any(Manutencao.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Manutencao resultado = manutencaoService.cancelarManutencao(1L);

            assertThat(resultado.getStatus()).isEqualTo("CANCELADA");
        }
    }

    @Nested
    @DisplayName("listarPendentes")
    class ListarPendentes {

        @Test
        @DisplayName("deve retornar manutenções pendentes")
        void deveListarPendentes() {
            List<Manutencao> pendentes = Collections.singletonList(criarManutencao(1L, 1L, "PENDENTE"));
            when(manutencaoRepository.findByStatus("PENDENTE")).thenReturn(pendentes);

            List<Manutencao> resultado = manutencaoService.listarPendentes();

            assertThat(resultado).isEqualTo(pendentes);
        }
    }

    @Nested
    @DisplayName("obterUltimaManutencao")
    class ObterUltimaManutencao {

        @Test
        @DisplayName("deve retornar a última manutenção do veículo")
        void deveRetornarUltimaManutencao() {
            Manutencao manutencao = criarManutencao(1L, 1L, "CONCLUIDA");
            when(manutencaoRepository.findUltimaManutencao(1L)).thenReturn(manutencao);

            Manutencao resultado = manutencaoService.obterUltimaManutencao(1L);

            assertThat(resultado).isEqualTo(manutencao);
        }

        @Test
        @DisplayName("deve lançar exceção quando não houver manutenção para o veículo")
        void deveLancarExcecaoQuandoNaoHouverManutencao() {
            when(manutencaoRepository.findUltimaManutencao(99L)).thenReturn(null);

            assertThatThrownBy(() -> manutencaoService.obterUltimaManutencao(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Nenhuma manutenção encontrada para veículo: 99");
        }
    }

    @Nested
    @DisplayName("calcularCustoManutencao")
    class CalcularCustoManutencao {

        @Test
        @DisplayName("deve calcular o custo de manutenção corretamente")
        void deveCalcularCustoCorretamente() {
            Double resultado = manutencaoService.calcularCustoManutencao("Fiat Uno", 10000L);

            assertThat(resultado).isEqualTo(500.0 + (10000L * 0.05));
        }

        @Test
        @DisplayName("deve calcular custo base quando quilometragem for zero")
        void deveCalcularCustoBaseQuandoQuilometragemZero() {
            Double resultado = manutencaoService.calcularCustoManutencao("Fiat Uno", 0L);

            assertThat(resultado).isEqualTo(500.0);
        }
    }

    @Nested
    @DisplayName("precisaDeManutencao")
    class PrecisaDeManutencao {

        @Test
        @DisplayName("deve retornar true quando quilometragem for maior ou igual ao limite")
        void deveRetornarTrueQuandoAtingiuLimite() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 50000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

            Boolean resultado = manutencaoService.precisaDeManutencao(1L);

            assertThat(resultado).isTrue();
        }

        @Test
        @DisplayName("deve retornar false quando quilometragem for menor que o limite")
        void deveRetornarFalseQuandoNaoAtingiuLimite() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

            Boolean resultado = manutencaoService.precisaDeManutencao(1L);

            assertThat(resultado).isFalse();
        }

        @Test
        @DisplayName("deve retornar false quando quilometragem for nula")
        void deveRetornarFalseQuandoQuilometragemNula() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", null, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

            Boolean resultado = manutencaoService.precisaDeManutencao(1L);

            assertThat(resultado).isFalse();
        }

        @Test
        @DisplayName("deve lançar VeiculoNaoEncontradoException quando veículo não existir")
        void deveLancarExcecaoQuandoVeiculoNaoExiste() {
            when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> manutencaoService.precisaDeManutencao(99L))
                    .isInstanceOf(VeiculoNaoEncontradoException.class)
                    .hasMessageContaining("Veículo não encontrado: 99");
        }
    }

    @Nested
    @DisplayName("agendarManutencaoPreventiva")
    class AgendarManutencaoPreventiva {

        @Test
        @DisplayName("deve agendar manutenção preventiva e notificar o gestor")
        void deveAgendarManutencaoComSucesso() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

            manutencaoService.agendarManutencaoPreventiva(1L, 50000L);

            verify(notificacaoFallbackAdapter, times(1)).notificarGestor(
                    eq("MANUTENCAO_AGENDADA"), anyString());
        }

        @Test
        @DisplayName("deve lançar VeiculoNaoEncontradoException quando veículo não existir")
        void deveLancarExcecaoQuandoVeiculoNaoExiste() {
            when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> manutencaoService.agendarManutencaoPreventiva(99L, 50000L))
                    .isInstanceOf(VeiculoNaoEncontradoException.class)
                    .hasMessageContaining("Veículo não encontrado: 99");
        }
    }

    @Nested
    @DisplayName("verificarNecessidadeManutencao")
    class VerificarNecessidadeManutencao {

        @Test
        @DisplayName("deve notificar o gestor quando o veículo precisar de manutenção")
        void deveNotificarQuandoPrecisaDeManutencao() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 60000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

            manutencaoService.verificarNecessidadeManutencao(1L, 60000L);

            verify(notificacaoFallbackAdapter, times(1)).notificarGestor(
                    eq("ALERTA_MANUTENCAO"), anyString());
        }

        @Test
        @DisplayName("não deve notificar quando o veículo não precisar de manutenção")
        void naoDeveNotificarQuandoNaoPrecisaDeManutencao() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

            manutencaoService.verificarNecessidadeManutencao(1L, 1000L);

            verify(notificacaoFallbackAdapter, never()).notificarGestor(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("motivoAlerta")
    class MotivoAlerta {

        @Test
        @DisplayName("deve retornar KM_EXCEDIDO quando somente a quilometragem exceder o limite")
        void deveRetornarKmExcedido() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 60000L, StatusVeiculo.ATIVO);
            Manutencao ultima = criarManutencao(1L, 1L, "CONCLUIDA");
            ultima.setDataManutencao(LocalDateTime.now().minusMonths(1));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.findUltimaManutencao(1L)).thenReturn(ultima);

            String motivo = manutencaoService.motivoAlerta(1L);

            assertThat(motivo).isEqualTo("KM_EXCEDIDO");
        }

        @Test
        @DisplayName("deve retornar TEMPO_EXCEDIDO quando somente o tempo desde a última manutenção exceder o limite")
        void deveRetornarTempoExcedido() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            Manutencao ultima = criarManutencao(1L, 1L, "CONCLUIDA");
            ultima.setDataManutencao(LocalDateTime.now().minusMonths(7));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.findUltimaManutencao(1L)).thenReturn(ultima);

            String motivo = manutencaoService.motivoAlerta(1L);

            assertThat(motivo).isEqualTo("TEMPO_EXCEDIDO");
        }

        @Test
        @DisplayName("deve retornar KM_E_TEMPO_EXCEDIDOS quando ambos os critérios excederem o limite")
        void deveRetornarKmETempoExcedidos() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 60000L, StatusVeiculo.ATIVO);
            Manutencao ultima = criarManutencao(1L, 1L, "CONCLUIDA");
            ultima.setDataManutencao(LocalDateTime.now().minusMonths(8));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.findUltimaManutencao(1L)).thenReturn(ultima);

            String motivo = manutencaoService.motivoAlerta(1L);

            assertThat(motivo).isEqualTo("KM_E_TEMPO_EXCEDIDOS");
        }

        @Test
        @DisplayName("deve retornar null quando nenhum critério exceder o limite")
        void deveRetornarNullQuandoNenhumCriterioAtendido() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            Manutencao ultima = criarManutencao(1L, 1L, "CONCLUIDA");
            ultima.setDataManutencao(LocalDateTime.now().minusMonths(1));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.findUltimaManutencao(1L)).thenReturn(ultima);

            String motivo = manutencaoService.motivoAlerta(1L);

            assertThat(motivo).isNull();
        }

        @Test
        @DisplayName("deve usar a data de cadastro do veículo quando não há manutenção registrada")
        void deveUsarDataCadastroQuandoSemHistoricoDeManutencao() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            veiculo.setDataCadastro(LocalDateTime.now().minusMonths(7));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.findUltimaManutencao(1L)).thenReturn(null);

            String motivo = manutencaoService.motivoAlerta(1L);

            assertThat(motivo).isEqualTo("TEMPO_EXCEDIDO");
        }

        @Test
        @DisplayName("deve usar a data de cadastro do veículo quando a manutenção mais recente não tem data")
        void deveUsarDataCadastroQuandoUltimaManutencaoSemData() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            veiculo.setDataCadastro(LocalDateTime.now().minusMonths(7));
            Manutencao ultima = criarManutencao(1L, 1L, "PENDENTE");
            ultima.setDataManutencao(null);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.findUltimaManutencao(1L)).thenReturn(ultima);

            String motivo = manutencaoService.motivoAlerta(1L);

            assertThat(motivo).isEqualTo("TEMPO_EXCEDIDO");
        }

        @Test
        @DisplayName("deve retornar null quando não há manutenção nem data de cadastro")
        void deveRetornarNullQuandoSemDataDeReferencia() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", 1000L, StatusVeiculo.ATIVO);
            veiculo.setDataCadastro(null);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(manutencaoRepository.findUltimaManutencao(1L)).thenReturn(null);

            String motivo = manutencaoService.motivoAlerta(1L);

            assertThat(motivo).isNull();
        }
    }

    @Nested
    @DisplayName("obterVeiculosElegiveisParaAlerta")
    class ObterVeiculosElegiveisParaAlerta {

        @Test
        @DisplayName("deve retornar apenas veículos ATIVOS elegíveis, ignorando os demais")
        void deveRetornarApenasElegiveis() {
            Veiculo elegivelPorKm = criarVeiculo(1L, "AAA1111", 60000L, StatusVeiculo.ATIVO);
            Veiculo naoElegivel = criarVeiculo(2L, "BBB2222", 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findByStatus(StatusVeiculo.ATIVO))
                    .thenReturn(Arrays.asList(elegivelPorKm, naoElegivel));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(elegivelPorKm));
            when(veiculoRepository.findById(2L)).thenReturn(Optional.of(naoElegivel));

            Manutencao ultimaRecente = criarManutencao(10L, 1L, "CONCLUIDA");
            ultimaRecente.setDataManutencao(LocalDateTime.now().minusMonths(1));
            when(manutencaoRepository.findUltimaManutencao(1L)).thenReturn(ultimaRecente);
            when(manutencaoRepository.findUltimaManutencao(2L)).thenReturn(ultimaRecente);

            List<Veiculo> resultado = manutencaoService.obterVeiculosElegiveisParaAlerta();

            assertThat(resultado).containsExactly(elegivelPorKm);
        }

        @Test
        @DisplayName("deve retornar lista vazia quando nenhum veículo ATIVO for elegível")
        void deveRetornarListaVaziaQuandoNenhumElegivel() {
            Veiculo naoElegivel = criarVeiculo(1L, "AAA1111", 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findByStatus(StatusVeiculo.ATIVO)).thenReturn(Collections.singletonList(naoElegivel));
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(naoElegivel));

            Manutencao ultimaRecente = criarManutencao(10L, 1L, "CONCLUIDA");
            ultimaRecente.setDataManutencao(LocalDateTime.now().minusMonths(1));
            when(manutencaoRepository.findUltimaManutencao(1L)).thenReturn(ultimaRecente);

            List<Veiculo> resultado = manutencaoService.obterVeiculosElegiveisParaAlerta();

            assertThat(resultado).isEmpty();
        }
    }
}
