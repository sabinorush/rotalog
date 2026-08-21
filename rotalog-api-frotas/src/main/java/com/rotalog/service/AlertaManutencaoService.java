package com.rotalog.service;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.ResultadoNotificacaoAlerta;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AlertaManutencaoService - Orquestra a verificação de elegibilidade e o envio
 * de alertas de manutenção preventiva para api-notificacoes.
 *
 * Responsabilidade nova e distinta de {@link ManutencaoService} (política de
 * manutenção) e de {@link VeiculoService} (cadastro de veículos).
 */
@Slf4j
@Service
public class AlertaManutencaoService {

    private static final String DESTINATARIO_GESTOR = "gestor@rotalog.com";

    private final ManutencaoService manutencaoService;
    private final VeiculoRepository veiculoRepository;
    private final AlertaNotificacaoClient alertaNotificacaoClient;
    private final AlertaManutencaoRepository alertaManutencaoRepository;

    public AlertaManutencaoService(ManutencaoService manutencaoService,
                                    VeiculoRepository veiculoRepository,
                                    AlertaNotificacaoClient alertaNotificacaoClient,
                                    AlertaManutencaoRepository alertaManutencaoRepository) {
        this.manutencaoService = manutencaoService;
        this.veiculoRepository = veiculoRepository;
        this.alertaNotificacaoClient = alertaNotificacaoClient;
        this.alertaManutencaoRepository = alertaManutencaoRepository;
    }

    /**
     * Verifica quais veículos estão elegíveis para manutenção preventiva e,
     * para cada um, dispara um alerta via api-notificacoes, persistindo o
     * resultado (ENVIADA/FALHA/PENDENTE).
     *
     * @return os alertas criados nesta execução
     */
    public List<AlertaManutencao> verificarEEmitirAlertas() {
        List<Veiculo> veiculosElegiveis = manutencaoService.obterVeiculosElegiveisParaAlerta();
        List<AlertaManutencao> alertasCriados = new ArrayList<>();

        for (Veiculo veiculoElegivel : veiculosElegiveis) {
            Long veiculoId = veiculoElegivel.getId();

            // FIXME: re-busca o veículo mesmo já tendo a entidade em mãos - mesmo padrão
            // de re-fetch redundante usado em ManutencaoService#iniciarManutencao/concluirManutencao
            Veiculo veiculo = veiculoRepository.findById(veiculoId).orElse(veiculoElegivel);
            String motivo = manutencaoService.motivoAlerta(veiculoId);

            String mensagem = "Veículo " + veiculo.getPlaca() + " elegível para manutenção preventiva: " + motivo;
            String referenciaId = "veiculo-" + veiculoId;
            ResultadoNotificacaoAlerta resultado = alertaNotificacaoClient.enviarAlerta(DESTINATARIO_GESTOR, mensagem, referenciaId);

            AlertaManutencao alerta = new AlertaManutencao();
            alerta.setVeiculoId(veiculoId);
            alerta.setMotivo(motivo);
            alerta.setStatus(resultado.getStatus());
            alerta.setMensagemErro(resultado.getMensagemErro());
            alerta.setDataCriacao(LocalDateTime.now());
            alerta.setDataAtualizacao(LocalDateTime.now());

            AlertaManutencao alertaSalvo = alertaManutencaoRepository.save(alerta);
            alertasCriados.add(alertaSalvo);

            log.info("Alerta de manutenção preventiva criado: veiculo={}, motivo={}, status={}",
                    veiculo.getPlaca(), motivo, resultado.getStatus());
        }

        return alertasCriados;
    }
}
