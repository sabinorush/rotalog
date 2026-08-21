package com.rotalog.repository;

import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para os métodos de query novos de {@link VeiculoRepository}
 * ({@code findByAnoFabricacao}, {@code findByStatusAndQuilometragemGreaterThan} e
 * {@code findByStatus} paginado).
 *
 * Usa H2 em memória (modo de compatibilidade PostgreSQL) via {@code @DataJpaTest}.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VeiculoRepositoryTest {

    @Autowired
    private VeiculoRepository veiculoRepository;

    private Veiculo criarVeiculo(String placa, Integer anoFabricacao, Long km, StatusVeiculo status) {
        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(placa);
        veiculo.setModelo("Fiat Uno");
        veiculo.setAnoFabricacao(anoFabricacao);
        veiculo.setQuilometragem(km);
        veiculo.setStatus(status);
        veiculo.setDataCadastro(LocalDateTime.now());
        veiculo.setDataAtualizacao(LocalDateTime.now());
        return veiculo;
    }

    @Nested
    @DisplayName("findByAnoFabricacao")
    class FindByAnoFabricacao {

        @Test
        @DisplayName("deve retornar apenas veículos do ano de fabricação informado")
        void deveRetornarApenasVeiculosDoAnoInformado() {
            veiculoRepository.saveAndFlush(criarVeiculo("AAA1111", 2018, 10000L, StatusVeiculo.ATIVO));
            veiculoRepository.saveAndFlush(criarVeiculo("BBB2222", 2020, 20000L, StatusVeiculo.ATIVO));
            veiculoRepository.saveAndFlush(criarVeiculo("CCC3333", 2020, 30000L, StatusVeiculo.INATIVO));

            List<Veiculo> resultado = veiculoRepository.findByAnoFabricacao(2020);

            assertThat(resultado).hasSize(2);
            assertThat(resultado).extracting(Veiculo::getPlaca).containsExactlyInAnyOrder("BBB2222", "CCC3333");
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não houver veículos do ano informado")
        void deveRetornarListaVaziaQuandoNaoHouverVeiculosDoAno() {
            veiculoRepository.saveAndFlush(criarVeiculo("AAA1111", 2018, 10000L, StatusVeiculo.ATIVO));

            List<Veiculo> resultado = veiculoRepository.findByAnoFabricacao(1999);

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByStatusAndQuilometragemGreaterThan")
    class FindByStatusAndQuilometragemGreaterThan {

        @Test
        @DisplayName("deve retornar apenas veículos com o status informado e quilometragem acima do limite")
        void deveRetornarVeiculosComStatusEQuilometragemAcimaDoLimite() {
            veiculoRepository.saveAndFlush(criarVeiculo("AAA1111", 2018, 60000L, StatusVeiculo.ATIVO));
            veiculoRepository.saveAndFlush(criarVeiculo("BBB2222", 2019, 10000L, StatusVeiculo.ATIVO));
            veiculoRepository.saveAndFlush(criarVeiculo("CCC3333", 2020, 70000L, StatusVeiculo.INATIVO));
            veiculoRepository.saveAndFlush(criarVeiculo("DDD4444", 2021, 50000L, StatusVeiculo.ATIVO));

            List<Veiculo> resultado = veiculoRepository.findByStatusAndQuilometragemGreaterThan(StatusVeiculo.ATIVO, 50000L);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getPlaca()).isEqualTo("AAA1111");
        }

        @Test
        @DisplayName("não deve retornar veículo com quilometragem exatamente igual ao limite (estritamente maior)")
        void naoDeveRetornarVeiculoComQuilometragemIgualAoLimite() {
            veiculoRepository.saveAndFlush(criarVeiculo("AAA1111", 2018, 50000L, StatusVeiculo.ATIVO));

            List<Veiculo> resultado = veiculoRepository.findByStatusAndQuilometragemGreaterThan(StatusVeiculo.ATIVO, 50000L);

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByStatus (paginado)")
    class FindByStatusPaginado {

        @Test
        @DisplayName("deve paginar os veículos com o status informado respeitando tamanho de página")
        void devePaginarVeiculosComStatusInformado() {
            veiculoRepository.saveAndFlush(criarVeiculo("AAA1111", 2018, 10000L, StatusVeiculo.ATIVO));
            veiculoRepository.saveAndFlush(criarVeiculo("BBB2222", 2019, 20000L, StatusVeiculo.ATIVO));
            veiculoRepository.saveAndFlush(criarVeiculo("CCC3333", 2020, 30000L, StatusVeiculo.ATIVO));
            veiculoRepository.saveAndFlush(criarVeiculo("DDD4444", 2021, 40000L, StatusVeiculo.INATIVO));

            Page<Veiculo> primeiraPagina = veiculoRepository.findByStatus(StatusVeiculo.ATIVO, PageRequest.of(0, 2));

            assertThat(primeiraPagina.getTotalElements()).isEqualTo(3);
            assertThat(primeiraPagina.getTotalPages()).isEqualTo(2);
            assertThat(primeiraPagina.getContent()).hasSize(2);
            assertThat(primeiraPagina.getContent()).allMatch(veiculo -> veiculo.getStatus() == StatusVeiculo.ATIVO);
        }

        @Test
        @DisplayName("deve retornar página vazia quando não houver veículos com o status informado")
        void deveRetornarPaginaVaziaQuandoNaoHouverVeiculosComStatus() {
            veiculoRepository.saveAndFlush(criarVeiculo("AAA1111", 2018, 10000L, StatusVeiculo.ATIVO));

            Page<Veiculo> resultado = veiculoRepository.findByStatus(StatusVeiculo.MANUTENCAO, PageRequest.of(0, 10));

            assertThat(resultado.getTotalElements()).isZero();
            assertThat(resultado.getContent()).isEmpty();
        }
    }
}
