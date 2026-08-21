package com.rotalog.repository;

import com.rotalog.domain.Motorista;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para os métodos de query novos de {@link MotoristaRepository}
 * ({@code findMotoristasDisponiveis} - query nativa - e {@code findByStatus} paginado).
 *
 * Usa H2 em memória (modo de compatibilidade PostgreSQL) via {@code @DataJpaTest}: a
 * query nativa de {@code findMotoristasDisponiveis} usa {@code CURRENT_DATE}, suportado
 * pelo H2, e não depende de nenhuma extensão específica do Postgres.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MotoristaRepositoryTest {

    @Autowired
    private MotoristaRepository motoristaRepository;

    private Motorista criarMotorista(String nome, String cnh, String status, LocalDate vencimentoCnh) {
        Motorista motorista = new Motorista();
        motorista.setNome(nome);
        motorista.setCnh(cnh);
        motorista.setCategoriaCnh("B");
        motorista.setVencimentoCnh(vencimentoCnh);
        motorista.setStatus(status);
        motorista.setDataCadastro(LocalDateTime.now());
        motorista.setDataAtualizacao(LocalDateTime.now());
        return motorista;
    }

    @Nested
    @DisplayName("findMotoristasDisponiveis (query nativa)")
    class FindMotoristasDisponiveis {

        @Test
        @DisplayName("deve retornar apenas motoristas ATIVO com CNH válida (vencimento futuro)")
        void deveRetornarApenasMotoristasAtivosComCnhValida() {
            motoristaRepository.saveAndFlush(
                    criarMotorista("Joao", "11111111111", "ATIVO", LocalDate.now().plusYears(1)));
            motoristaRepository.saveAndFlush(
                    criarMotorista("Maria", "22222222222", "INATIVO", LocalDate.now().plusYears(1)));
            motoristaRepository.saveAndFlush(
                    criarMotorista("Pedro", "33333333333", "ATIVO", LocalDate.now().minusDays(1)));

            List<Motorista> resultado = motoristaRepository.findMotoristasDisponiveis();

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getCnh()).isEqualTo("11111111111");
        }

        @Test
        @DisplayName("deve considerar disponível o motorista ATIVO com vencimento de CNH nulo")
        void deveConsiderarDisponivelMotoristaAtivoComVencimentoCnhNulo() {
            motoristaRepository.saveAndFlush(criarMotorista("Joao", "11111111111", "ATIVO", null));

            List<Motorista> resultado = motoristaRepository.findMotoristasDisponiveis();

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getCnh()).isEqualTo("11111111111");
        }

        @Test
        @DisplayName("deve considerar disponível o motorista ATIVO com CNH vencendo hoje")
        void deveConsiderarDisponivelMotoristaComCnhVencendoHoje() {
            motoristaRepository.saveAndFlush(criarMotorista("Joao", "11111111111", "ATIVO", LocalDate.now()));

            List<Motorista> resultado = motoristaRepository.findMotoristasDisponiveis();

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getCnh()).isEqualTo("11111111111");
        }

        @Test
        @DisplayName("não deve retornar motorista ATIVO com CNH vencida")
        void naoDeveRetornarMotoristaComCnhVencida() {
            motoristaRepository.saveAndFlush(
                    criarMotorista("Pedro", "33333333333", "ATIVO", LocalDate.now().minusDays(1)));

            List<Motorista> resultado = motoristaRepository.findMotoristasDisponiveis();

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("não deve retornar motorista INATIVO mesmo com CNH válida")
        void naoDeveRetornarMotoristaInativoComCnhValida() {
            motoristaRepository.saveAndFlush(
                    criarMotorista("Maria", "22222222222", "INATIVO", LocalDate.now().plusYears(1)));

            List<Motorista> resultado = motoristaRepository.findMotoristasDisponiveis();

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não houver motoristas cadastrados")
        void deveRetornarListaVaziaQuandoNaoHouverMotoristas() {
            List<Motorista> resultado = motoristaRepository.findMotoristasDisponiveis();

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByStatus (paginado)")
    class FindByStatusPaginado {

        @Test
        @DisplayName("deve paginar os motoristas com o status informado respeitando tamanho de página")
        void devePaginarMotoristasComStatusInformado() {
            motoristaRepository.saveAndFlush(criarMotorista("Joao", "11111111111", "ATIVO", LocalDate.now().plusYears(1)));
            motoristaRepository.saveAndFlush(criarMotorista("Maria", "22222222222", "ATIVO", LocalDate.now().plusYears(1)));
            motoristaRepository.saveAndFlush(criarMotorista("Pedro", "33333333333", "ATIVO", LocalDate.now().plusYears(1)));
            motoristaRepository.saveAndFlush(criarMotorista("Ana", "44444444444", "INATIVO", LocalDate.now().plusYears(1)));

            Page<Motorista> primeiraPagina = motoristaRepository.findByStatus("ATIVO", PageRequest.of(0, 2));

            assertThat(primeiraPagina.getTotalElements()).isEqualTo(3);
            assertThat(primeiraPagina.getTotalPages()).isEqualTo(2);
            assertThat(primeiraPagina.getContent()).hasSize(2);
            assertThat(primeiraPagina.getContent()).allMatch(motorista -> "ATIVO".equals(motorista.getStatus()));
        }

        @Test
        @DisplayName("deve retornar página vazia quando não houver motoristas com o status informado")
        void deveRetornarPaginaVaziaQuandoNaoHouverMotoristasComStatus() {
            motoristaRepository.saveAndFlush(criarMotorista("Joao", "11111111111", "ATIVO", LocalDate.now().plusYears(1)));

            Page<Motorista> resultado = motoristaRepository.findByStatus("FERIAS", PageRequest.of(0, 10));

            assertThat(resultado.getTotalElements()).isZero();
            assertThat(resultado.getContent()).isEmpty();
        }
    }
}
