package com.rotalog.repository;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.StatusAlerta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para {@link AlertaManutencaoRepository}.
 *
 * Usa H2 em memória (modo de compatibilidade PostgreSQL) via {@code @DataJpaTest},
 * com o schema gerado pelo Hibernate a partir da entidade {@link AlertaManutencao}
 * (ver src/test/resources/application-test.properties).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlertaManutencaoRepositoryTest {

    @Autowired
    private AlertaManutencaoRepository alertaManutencaoRepository;

    private AlertaManutencao criarAlerta(Long veiculoId, String motivo, StatusAlerta status, String mensagemErro) {
        AlertaManutencao alerta = new AlertaManutencao();
        alerta.setVeiculoId(veiculoId);
        alerta.setMotivo(motivo);
        alerta.setStatus(status);
        alerta.setMensagemErro(mensagemErro);
        alerta.setDataCriacao(LocalDateTime.now());
        alerta.setDataAtualizacao(LocalDateTime.now());
        return alerta;
    }

    @Nested
    @DisplayName("findByStatus")
    class FindByStatus {

        @Test
        @DisplayName("deve retornar apenas os alertas com o status informado")
        void deveRetornarApenasAlertasComStatusInformado() {
            alertaManutencaoRepository.saveAndFlush(criarAlerta(1L, "KM_EXCEDIDO", StatusAlerta.ENVIADA, null));
            alertaManutencaoRepository.saveAndFlush(criarAlerta(2L, "TEMPO_EXCEDIDO", StatusAlerta.FALHA, "erro qualquer"));
            alertaManutencaoRepository.saveAndFlush(criarAlerta(3L, "KM_E_TEMPO_EXCEDIDOS", StatusAlerta.PENDENTE, "Connection refused"));
            alertaManutencaoRepository.saveAndFlush(criarAlerta(4L, "KM_EXCEDIDO", StatusAlerta.ENVIADA, null));

            List<AlertaManutencao> resultado = alertaManutencaoRepository.findByStatus(StatusAlerta.ENVIADA);

            assertThat(resultado).hasSize(2);
            assertThat(resultado).extracting(AlertaManutencao::getVeiculoId).containsExactlyInAnyOrder(1L, 4L);
            assertThat(resultado).allMatch(alerta -> alerta.getStatus() == StatusAlerta.ENVIADA);
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não houver alertas com o status informado")
        void deveRetornarListaVaziaQuandoNaoHouverAlertasComStatus() {
            alertaManutencaoRepository.saveAndFlush(criarAlerta(1L, "KM_EXCEDIDO", StatusAlerta.ENVIADA, null));

            List<AlertaManutencao> resultado = alertaManutencaoRepository.findByStatus(StatusAlerta.FALHA);

            assertThat(resultado).isEmpty();
        }
    }
}
