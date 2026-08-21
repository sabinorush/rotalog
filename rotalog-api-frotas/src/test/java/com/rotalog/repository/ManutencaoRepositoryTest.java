package com.rotalog.repository;

import com.rotalog.domain.Manutencao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para os métodos de query novos de {@link ManutencaoRepository}
 * ({@code findByStatusAndDataManutencaoBetween} e {@code findByVeiculoId} paginado).
 *
 * Usa H2 em memória (modo de compatibilidade PostgreSQL) via {@code @DataJpaTest}.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ManutencaoRepositoryTest {

    @Autowired
    private ManutencaoRepository manutencaoRepository;

    private Manutencao criarManutencao(Long veiculoId, String status, LocalDateTime dataManutencao) {
        Manutencao manutencao = new Manutencao();
        manutencao.setVeiculoId(veiculoId);
        manutencao.setTipoManutencao("PREVENTIVA");
        manutencao.setDataManutencao(dataManutencao);
        manutencao.setQuilometragemManutencao(50000L);
        manutencao.setCusto(BigDecimal.TEN);
        manutencao.setStatus(status);
        manutencao.setDataCriacao(LocalDateTime.now());
        manutencao.setDataAtualizacao(LocalDateTime.now());
        return manutencao;
    }

    @Nested
    @DisplayName("findByStatusAndDataManutencaoBetween")
    class FindByStatusAndDataManutencaoBetween {

        @Test
        @DisplayName("deve retornar apenas manutenções com o status informado dentro do intervalo de datas")
        void deveRetornarManutencoesComStatusEDataDentroDoIntervalo() {
            LocalDateTime inicio = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDateTime fim = LocalDateTime.of(2026, 1, 31, 23, 59);

            manutencaoRepository.saveAndFlush(criarManutencao(1L, "CONCLUIDA", LocalDateTime.of(2026, 1, 15, 10, 0)));
            manutencaoRepository.saveAndFlush(criarManutencao(2L, "CONCLUIDA", LocalDateTime.of(2026, 2, 15, 10, 0)));
            manutencaoRepository.saveAndFlush(criarManutencao(3L, "PENDENTE", LocalDateTime.of(2026, 1, 20, 10, 0)));

            List<Manutencao> resultado = manutencaoRepository.findByStatusAndDataManutencaoBetween("CONCLUIDA", inicio, fim);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getVeiculoId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("deve incluir manutenções nas bordas exatas do intervalo (between inclusivo)")
        void deveIncluirManutencoesNasBordasDoIntervalo() {
            LocalDateTime inicio = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDateTime fim = LocalDateTime.of(2026, 1, 31, 23, 59);

            manutencaoRepository.saveAndFlush(criarManutencao(1L, "CONCLUIDA", inicio));
            manutencaoRepository.saveAndFlush(criarManutencao(2L, "CONCLUIDA", fim));

            List<Manutencao> resultado = manutencaoRepository.findByStatusAndDataManutencaoBetween("CONCLUIDA", inicio, fim);

            assertThat(resultado).hasSize(2);
            assertThat(resultado).extracting(Manutencao::getVeiculoId).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não houver manutenções no status ou intervalo informados")
        void deveRetornarListaVaziaQuandoNaoHouverManutencoesNoIntervalo() {
            manutencaoRepository.saveAndFlush(criarManutencao(1L, "CONCLUIDA", LocalDateTime.of(2025, 1, 15, 10, 0)));

            List<Manutencao> resultado = manutencaoRepository.findByStatusAndDataManutencaoBetween(
                    "CONCLUIDA", LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 31, 23, 59));

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByVeiculoId (paginado)")
    class FindByVeiculoIdPaginado {

        @Test
        @DisplayName("deve paginar as manutenções do veículo informado respeitando tamanho de página")
        void devePaginarManutencoesDoVeiculoInformado() {
            manutencaoRepository.saveAndFlush(criarManutencao(1L, "CONCLUIDA", LocalDateTime.of(2026, 1, 10, 10, 0)));
            manutencaoRepository.saveAndFlush(criarManutencao(1L, "PENDENTE", LocalDateTime.of(2026, 2, 10, 10, 0)));
            manutencaoRepository.saveAndFlush(criarManutencao(1L, "CONCLUIDA", LocalDateTime.of(2026, 3, 10, 10, 0)));
            manutencaoRepository.saveAndFlush(criarManutencao(2L, "CONCLUIDA", LocalDateTime.of(2026, 1, 10, 10, 0)));

            Page<Manutencao> primeiraPagina = manutencaoRepository.findByVeiculoId(1L, PageRequest.of(0, 2));

            assertThat(primeiraPagina.getTotalElements()).isEqualTo(3);
            assertThat(primeiraPagina.getTotalPages()).isEqualTo(2);
            assertThat(primeiraPagina.getContent()).hasSize(2);
            assertThat(primeiraPagina.getContent()).allMatch(manutencao -> manutencao.getVeiculoId().equals(1L));
        }

        @Test
        @DisplayName("deve retornar página vazia quando o veículo não tiver manutenções")
        void deveRetornarPaginaVaziaQuandoVeiculoNaoTiverManutencoes() {
            manutencaoRepository.saveAndFlush(criarManutencao(1L, "CONCLUIDA", LocalDateTime.of(2026, 1, 10, 10, 0)));

            Page<Manutencao> resultado = manutencaoRepository.findByVeiculoId(999L, PageRequest.of(0, 10));

            assertThat(resultado.getTotalElements()).isZero();
            assertThat(resultado.getContent()).isEmpty();
        }
    }
}
