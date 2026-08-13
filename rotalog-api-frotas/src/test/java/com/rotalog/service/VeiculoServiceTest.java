package com.rotalog.service;

import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.FrotaEstatisticasDTO;
import com.rotalog.exception.AnoFabricacaoInvalidoException;
import com.rotalog.exception.ModeloInvalidoException;
import com.rotalog.exception.PlacaDuplicadaException;
import com.rotalog.exception.PlacaInvalidaException;
import com.rotalog.exception.QuilometragemInvalidaException;
import com.rotalog.exception.StatusInvalidoException;
import com.rotalog.exception.VeiculoNaoEncontradoException;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes unitários para {@link VeiculoService}.
 *
 * Observação: a classe testada é dívida técnica intencional do curso
 * (System.out.println removido, mistura de responsabilidades, etc).
 * Estes testes cobrem o comportamento como está, sem corrigir a dívida técnica
 * que estiver fora do escopo do ADR-0001.
 */
@ExtendWith(MockitoExtension.class)
class VeiculoServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private NotificacaoFallbackAdapter notificacaoFallbackAdapter;

    @Mock
    private ManutencaoService manutencaoService;

    @InjectMocks
    private VeiculoService veiculoService;

    private Veiculo criarVeiculo(Long id, String placa, String modelo, Integer ano, Long km, StatusVeiculo status) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(id);
        veiculo.setPlaca(placa);
        veiculo.setModelo(modelo);
        veiculo.setAnoFabricacao(ano);
        veiculo.setQuilometragem(km);
        veiculo.setStatus(status);
        return veiculo;
    }

    @Nested
    @DisplayName("listarTodos")
    class ListarTodos {

        @Test
        @DisplayName("deve retornar todos os veículos do repositório")
        void deveListarTodosOsVeiculos() {
            List<Veiculo> veiculos = Arrays.asList(
                    criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO),
                    criarVeiculo(2L, "XYZ5678", "VW Gol", 2019, 2000L, StatusVeiculo.ATIVO)
            );
            when(veiculoRepository.findAll()).thenReturn(veiculos);

            List<Veiculo> resultado = veiculoService.listarTodos();

            assertThat(resultado).hasSize(2).containsExactlyElementsOf(veiculos);
            verify(veiculoRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não houver veículos")
        void deveRetornarListaVaziaQuandoNaoHouverVeiculos() {
            when(veiculoRepository.findAll()).thenReturn(Collections.emptyList());

            List<Veiculo> resultado = veiculoService.listarTodos();

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("buscarPorId")
    class BuscarPorId {

        @Test
        @DisplayName("deve retornar o veículo quando encontrado")
        void deveRetornarVeiculoQuandoEncontrado() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

            Veiculo resultado = veiculoService.buscarPorId(1L);

            assertThat(resultado).isEqualTo(veiculo);
        }

        @Test
        @DisplayName("deve lançar VeiculoNaoEncontradoException quando não encontrado")
        void deveLancarExcecaoQuandoNaoEncontrado() {
            when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> veiculoService.buscarPorId(99L))
                    .isInstanceOf(VeiculoNaoEncontradoException.class)
                    .hasMessageContaining("Veículo não encontrado: 99");
        }
    }

    @Nested
    @DisplayName("buscarPorPlaca")
    class BuscarPorPlaca {

        @Test
        @DisplayName("deve retornar o veículo quando encontrado pela placa")
        void deveRetornarVeiculoQuandoEncontradoPelaPlaca() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.of(veiculo));

            Veiculo resultado = veiculoService.buscarPorPlaca("ABC1234");

            assertThat(resultado).isEqualTo(veiculo);
        }

        @Test
        @DisplayName("deve lançar VeiculoNaoEncontradoException quando placa não encontrada")
        void deveLancarExcecaoQuandoPlacaNaoEncontrada() {
            when(veiculoRepository.findByPlaca("NAOEXISTE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> veiculoService.buscarPorPlaca("NAOEXISTE"))
                    .isInstanceOf(VeiculoNaoEncontradoException.class)
                    .hasMessageContaining("Veículo não encontrado com placa: NAOEXISTE");
        }
    }

    @Nested
    @DisplayName("registrarVeiculo")
    class RegistrarVeiculo {

        @Test
        @DisplayName("deve registrar veículo com sucesso e enviar notificação")
        void deveRegistrarVeiculoComSucesso() {
            when(veiculoRepository.findByPlaca("abc1234")).thenReturn(Optional.empty());
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.registrarVeiculo("abc1234", "Fiat Uno", 2020);

            assertThat(resultado.getPlaca()).isEqualTo("ABC1234");
            assertThat(resultado.getModelo()).isEqualTo("Fiat Uno");
            assertThat(resultado.getAnoFabricacao()).isEqualTo(2020);
            assertThat(resultado.getStatus()).isEqualTo(StatusVeiculo.ATIVO);
            assertThat(resultado.getQuilometragem()).isZero();
            assertThat(resultado.getDataCadastro()).isNotNull();
            assertThat(resultado.getDataAtualizacao()).isNotNull();

            verify(veiculoRepository, times(1)).save(any(Veiculo.class));
            verify(notificacaoFallbackAdapter, times(1)).notificarGestor(eq("NOVO_VEICULO"), anyString());
        }

        @Test
        @DisplayName("deve lançar exceção quando placa for nula")
        void deveLancarExcecaoQuandoPlacaNula() {
            assertThatThrownBy(() -> veiculoService.registrarVeiculo(null, "Fiat Uno", 2020))
                    .isInstanceOf(PlacaInvalidaException.class)
                    .hasMessageContaining("Placa é obrigatória");
        }

        @Test
        @DisplayName("deve lançar exceção quando placa for vazia")
        void deveLancarExcecaoQuandoPlacaVazia() {
            assertThatThrownBy(() -> veiculoService.registrarVeiculo("", "Fiat Uno", 2020))
                    .isInstanceOf(PlacaInvalidaException.class)
                    .hasMessageContaining("Placa é obrigatória");
        }

        @Test
        @DisplayName("deve lançar exceção quando placa tiver tamanho diferente de 7")
        void deveLancarExcecaoQuandoPlacaComTamanhoInvalido() {
            assertThatThrownBy(() -> veiculoService.registrarVeiculo("ABC123", "Fiat Uno", 2020))
                    .isInstanceOf(PlacaInvalidaException.class)
                    .hasMessageContaining("Placa deve ter 7 caracteres");
        }

        @Test
        @DisplayName("deve lançar exceção quando placa já existir")
        void deveLancarExcecaoQuandoPlacaDuplicada() {
            Veiculo existente = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.of(existente));

            assertThatThrownBy(() -> veiculoService.registrarVeiculo("ABC1234", "Fiat Uno", 2020))
                    .isInstanceOf(PlacaDuplicadaException.class)
                    .hasMessageContaining("Veículo com placa ABC1234 já existe");
        }

        @Test
        @DisplayName("deve lançar exceção quando modelo for nulo")
        void deveLancarExcecaoQuandoModeloNulo() {
            when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> veiculoService.registrarVeiculo("ABC1234", null, 2020))
                    .isInstanceOf(ModeloInvalidoException.class)
                    .hasMessageContaining("Modelo é obrigatório");
        }

        @Test
        @DisplayName("deve lançar exceção quando modelo for vazio")
        void deveLancarExcecaoQuandoModeloVazio() {
            when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> veiculoService.registrarVeiculo("ABC1234", "", 2020))
                    .isInstanceOf(ModeloInvalidoException.class)
                    .hasMessageContaining("Modelo é obrigatório");
        }

        @Test
        @DisplayName("deve lançar exceção quando ano de fabricação for nulo")
        void deveLancarExcecaoQuandoAnoNulo() {
            when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> veiculoService.registrarVeiculo("ABC1234", "Fiat Uno", null))
                    .isInstanceOf(AnoFabricacaoInvalidoException.class)
                    .hasMessageContaining("Ano de fabricação inválido");
        }

        @Test
        @DisplayName("deve lançar exceção quando ano de fabricação for menor que 1900")
        void deveLancarExcecaoQuandoAnoMenorQue1900() {
            when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> veiculoService.registrarVeiculo("ABC1234", "Fiat Uno", 1899))
                    .isInstanceOf(AnoFabricacaoInvalidoException.class)
                    .hasMessageContaining("Ano de fabricação inválido");
        }

        @Test
        @DisplayName("deve lançar exceção quando ano de fabricação for maior que 2100")
        void deveLancarExcecaoQuandoAnoMaiorQue2100() {
            when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> veiculoService.registrarVeiculo("ABC1234", "Fiat Uno", 2101))
                    .isInstanceOf(AnoFabricacaoInvalidoException.class)
                    .hasMessageContaining("Ano de fabricação inválido");
        }
    }

    @Nested
    @DisplayName("atualizarVeiculo")
    class AtualizarVeiculo {

        @Test
        @DisplayName("deve atualizar apenas o modelo quando somente ele for informado")
        void deveAtualizarApenasModelo() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.atualizarVeiculo(1L, "Fiat Uno Mille", null, null);

            assertThat(resultado.getModelo()).isEqualTo("Fiat Uno Mille");
            assertThat(resultado.getAnoFabricacao()).isEqualTo(2020);
            assertThat(resultado.getQuilometragem()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("deve atualizar apenas o ano quando somente ele for informado")
        void deveAtualizarApenasAno() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.atualizarVeiculo(1L, null, 2021, null);

            assertThat(resultado.getModelo()).isEqualTo("Fiat Uno");
            assertThat(resultado.getAnoFabricacao()).isEqualTo(2021);
            assertThat(resultado.getQuilometragem()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("deve atualizar apenas a quilometragem quando somente ela for informada")
        void deveAtualizarApenasQuilometragem() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.atualizarVeiculo(1L, null, null, 1500L);

            assertThat(resultado.getModelo()).isEqualTo("Fiat Uno");
            assertThat(resultado.getAnoFabricacao()).isEqualTo(2020);
            assertThat(resultado.getQuilometragem()).isEqualTo(1500L);
        }

        @Test
        @DisplayName("deve atualizar quilometragem mesmo quando reduzida, apenas logando aviso")
        void deveAtualizarQuandoQuilometragemReduzida() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 5000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.atualizarVeiculo(1L, null, null, 1000L);

            assertThat(resultado.getQuilometragem()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("não deve alterar modelo quando informado vazio")
        void naoDeveAlterarModeloQuandoVazio() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.atualizarVeiculo(1L, "", null, null);

            assertThat(resultado.getModelo()).isEqualTo("Fiat Uno");
        }

        @Test
        @DisplayName("não deve alterar nenhum campo quando todos forem nulos/vazios")
        void naoDeveAlterarNadaQuandoTudoNulo() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.atualizarVeiculo(1L, null, null, null);

            assertThat(resultado.getModelo()).isEqualTo("Fiat Uno");
            assertThat(resultado.getAnoFabricacao()).isEqualTo(2020);
            assertThat(resultado.getQuilometragem()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("deve lançar exceção quando veículo não for encontrado")
        void deveLancarExcecaoQuandoVeiculoNaoEncontrado() {
            when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> veiculoService.atualizarVeiculo(99L, "Modelo", 2020, 1000L))
                    .isInstanceOf(VeiculoNaoEncontradoException.class);
        }
    }

    @Nested
    @DisplayName("atualizarQuilometragem")
    class AtualizarQuilometragem {

        @Test
        @DisplayName("deve atualizar quilometragem e delegar checagem de manutenção ao ManutencaoService")
        void deveAtualizarQuilometragemComSucesso() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.atualizarQuilometragem(1L, 2000L);

            assertThat(resultado.getQuilometragem()).isEqualTo(2000L);
            verify(manutencaoService, times(1)).verificarNecessidadeManutencao(1L, 2000L);
        }

        @Test
        @DisplayName("deve lançar exceção quando nova quilometragem for negativa")
        void deveLancarExcecaoQuandoQuilometragemNegativa() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

            assertThatThrownBy(() -> veiculoService.atualizarQuilometragem(1L, -10L))
                    .isInstanceOf(QuilometragemInvalidaException.class)
                    .hasMessageContaining("Quilometragem não pode ser negativa");

            verify(veiculoRepository, never()).save(any(Veiculo.class));
            verifyNoInteractions(manutencaoService);
        }

        @Test
        @DisplayName("deve atualizar mesmo quando a nova quilometragem for menor que a atual (apenas loga aviso)")
        void deveAtualizarQuandoQuilometragemMenorQueAtual() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 5000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.atualizarQuilometragem(1L, 1000L);

            assertThat(resultado.getQuilometragem()).isEqualTo(1000L);
        }
    }

    @Nested
    @DisplayName("obterVeiculosPorStatus")
    class ObterVeiculosPorStatus {

        @Test
        @DisplayName("deve retornar veículos com status ATIVO")
        void deveRetornarVeiculosAtivos() {
            List<Veiculo> ativos = Collections.singletonList(
                    criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO));
            when(veiculoRepository.findByStatus(StatusVeiculo.ATIVO)).thenReturn(ativos);

            List<Veiculo> resultado = veiculoService.obterVeiculosPorStatus("ATIVO");

            assertThat(resultado).isEqualTo(ativos);
        }

        @Test
        @DisplayName("deve retornar veículos com status INATIVO")
        void deveRetornarVeiculosInativos() {
            List<Veiculo> inativos = Collections.singletonList(
                    criarVeiculo(2L, "XYZ5678", "VW Gol", 2019, 2000L, StatusVeiculo.INATIVO));
            when(veiculoRepository.findByStatus(StatusVeiculo.INATIVO)).thenReturn(inativos);

            List<Veiculo> resultado = veiculoService.obterVeiculosPorStatus("INATIVO");

            assertThat(resultado).isEqualTo(inativos);
        }

        @Test
        @DisplayName("deve retornar veículos com status MANUTENCAO")
        void deveRetornarVeiculosEmManutencao() {
            List<Veiculo> emManutencao = Collections.singletonList(
                    criarVeiculo(3L, "MNT1234", "Ford Ka", 2018, 3000L, StatusVeiculo.MANUTENCAO));
            when(veiculoRepository.findByStatus(StatusVeiculo.MANUTENCAO)).thenReturn(emManutencao);

            List<Veiculo> resultado = veiculoService.obterVeiculosPorStatus("MANUTENCAO");

            assertThat(resultado).isEqualTo(emManutencao);
        }

        @Test
        @DisplayName("deve lançar exceção quando status for inválido")
        void deveLancarExcecaoQuandoStatusInvalido() {
            assertThatThrownBy(() -> veiculoService.obterVeiculosPorStatus("DESCONHECIDO"))
                    .isInstanceOf(StatusInvalidoException.class)
                    .hasMessageContaining("Status inválido: DESCONHECIDO");
        }
    }

    @Nested
    @DisplayName("desativarVeiculo")
    class DesativarVeiculo {

        @Test
        @DisplayName("deve desativar veículo e enviar notificação")
        void deveDesativarVeiculoComSucesso() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.ATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.desativarVeiculo(1L);

            assertThat(resultado.getStatus()).isEqualTo(StatusVeiculo.INATIVO);
            verify(notificacaoFallbackAdapter, times(1)).notificarGestor(eq("VEICULO_DESATIVADO"), anyString());
        }
    }

    @Nested
    @DisplayName("reativarVeiculo")
    class ReativarVeiculo {

        @Test
        @DisplayName("deve reativar veículo com sucesso")
        void deveReativarVeiculoComSucesso() {
            Veiculo veiculo = criarVeiculo(1L, "ABC1234", "Fiat Uno", 2020, 1000L, StatusVeiculo.INATIVO);
            when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
            when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Veiculo resultado = veiculoService.reativarVeiculo(1L);

            assertThat(resultado.getStatus()).isEqualTo(StatusVeiculo.ATIVO);
        }
    }

    @Nested
    @DisplayName("obterEstatisticasFrota")
    class ObterEstatisticasFrota {

        @Test
        @DisplayName("deve retornar DTO com as estatísticas da frota")
        void deveRetornarEstatisticasNoFormatoEsperado() {
            when(veiculoRepository.count()).thenReturn(10L);
            when(veiculoRepository.countByStatus(StatusVeiculo.ATIVO)).thenReturn(2L);
            when(veiculoRepository.countByStatus(StatusVeiculo.INATIVO)).thenReturn(1L);
            when(veiculoRepository.countByStatus(StatusVeiculo.MANUTENCAO)).thenReturn(0L);

            FrotaEstatisticasDTO resultado = veiculoService.obterEstatisticasFrota();

            assertThat(resultado.getTotal()).isEqualTo(10L);
            assertThat(resultado.getAtivos()).isEqualTo(2L);
            assertThat(resultado.getInativos()).isEqualTo(1L);
            assertThat(resultado.getEmManutencao()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("sincronizarComSistemaExterno")
    class SincronizarComSistemaExterno {

        @Test
        @DisplayName("deve executar sem lançar exceção")
        void deveExecutarSemLancarExcecao() {
            veiculoService.sincronizarComSistemaExterno();
        }
    }
}
