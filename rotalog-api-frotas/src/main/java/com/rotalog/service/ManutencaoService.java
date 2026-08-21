package com.rotalog.service;

import com.rotalog.domain.Manutencao;
import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import com.rotalog.exception.VeiculoNaoEncontradoException;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ManutencaoService - Serviço de gerenciamento de manutenções
 *
 * FIXME: Lógica de negócio misturada com infraestrutura
 * FIXME: Sem transações explícitas
 * FIXME: Sem validação de estados
 */
@Slf4j
@Service
public class ManutencaoService {

    @Autowired // FIXME: deveria usar injeção por construtor
    private ManutencaoRepository manutencaoRepository;

    @Autowired
    private VeiculoRepository veiculoRepository;

    @Autowired
    private NotificacaoClient notificacaoClient;

    @Autowired
    private NotificacaoFallbackAdapter notificacaoFallbackAdapter;

    public List<Manutencao> listarTodas() {
        return manutencaoRepository.findAll(); // FIXME: sem paginação
    }

    public Manutencao buscarPorId(Long id) {
        return manutencaoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Manutenção não encontrada: " + id));
    }

    public List<Manutencao> listarPorVeiculo(Long veiculoId) {
        return manutencaoRepository.findByVeiculoId(veiculoId);
    }

    /**
     * Busca o veículo pelo id ou lança {@link VeiculoNaoEncontradoException}.
     * Helper consolidado para eliminar a duplicação de checagem "veículo não encontrado"
     * espalhada pelos métodos desta classe (FR-ARQ-05).
     */
    private Veiculo buscarVeiculoOuFalhar(Long id) {
        return veiculoRepository.findById(id)
                .orElseThrow(() -> new VeiculoNaoEncontradoException("Veículo não encontrado: " + id));
    }

    /**
     * Agendar manutenção
     *
     * FIXME: Sem validação se veículo já tem manutenção pendente
     * FIXME: Sem transação explícita
     * FIXME: Atualiza status do veículo diretamente (acoplamento)
     */
    public Manutencao agendarManutencao(Long veiculoId, String tipoManutencao, String descricao, BigDecimal custoEstimado) {
        // Verificar se veículo existe
        Veiculo veiculo = buscarVeiculoOuFalhar(veiculoId);

        if (tipoManutencao == null || tipoManutencao.trim().isEmpty()) {
            throw new RuntimeException("Tipo de manutenção é obrigatório");
        }

        // FIXME: Sem validação se já existe manutenção pendente para o mesmo veículo
        Manutencao manutencao = new Manutencao();
        manutencao.setVeiculoId(veiculoId);
        manutencao.setTipoManutencao(tipoManutencao);
        manutencao.setDescricao(descricao);
        manutencao.setCusto(custoEstimado);
        manutencao.setQuilometragemManutencao(veiculo.getQuilometragem());
        manutencao.setStatus("PENDENTE");
        manutencao.setDataCriacao(LocalDateTime.now());
        manutencao.setDataAtualizacao(LocalDateTime.now());

        Manutencao salva = manutencaoRepository.save(manutencao);
        log.info("Manutenção agendada: veículo={}, tipo={}", veiculo.getPlaca(), tipoManutencao);

        // Notificar sobre agendamento
        try {
            notificacaoClient.enviarNotificacao(
                "MANUTENCAO_AGENDADA",
                "oficina@rotalog.com",
                "Manutenção " + tipoManutencao + " agendada para veículo " + veiculo.getPlaca()
            );
        } catch (Exception e) {
            log.error("Falha ao notificar agendamento: {}", e.getMessage());
        }

        return salva;
    }

    /**
     * Iniciar manutenção
     *
     * FIXME: Sem validação de transição de estado
     * FIXME: Atualiza veículo sem transação
     */
    public Manutencao iniciarManutencao(Long manutencaoId) {
        Manutencao manutencao = buscarPorId(manutencaoId);

        // FIXME: Sem validação de transição de estado (poderia ir de CONCLUIDA para EM_ANDAMENTO)
        if (!"PENDENTE".equals(manutencao.getStatus())) {
            log.warn("Iniciando manutenção que não está PENDENTE: status={}", manutencao.getStatus());
            // FIXME: deveria lançar exceção, mas apenas loga
        }

        manutencao.setStatus("EM_ANDAMENTO");
        manutencao.setDataManutencao(LocalDateTime.now());
        manutencao.setDataAtualizacao(LocalDateTime.now());

        // FIXME: Atualiza status do veículo diretamente - acoplamento forte
        Veiculo veiculo = veiculoRepository.findById(manutencao.getVeiculoId())
                .orElse(null);
        if (veiculo != null) {
            veiculo.setStatus(StatusVeiculo.MANUTENCAO);
            veiculo.setDataAtualizacao(LocalDateTime.now());
            veiculoRepository.save(veiculo); // FIXME: sem transação com a manutenção
        }

        return manutencaoRepository.save(manutencao);
    }

    /**
     * Concluir manutenção
     *
     * FIXME: Sem validação de custo final
     * FIXME: Sem transação
     */
    public Manutencao concluirManutencao(Long manutencaoId, BigDecimal custoFinal) {
        Manutencao manutencao = buscarPorId(manutencaoId);

        manutencao.setStatus("CONCLUIDA");
        if (custoFinal != null) {
            manutencao.setCusto(custoFinal);
        }
        manutencao.setDataAtualizacao(LocalDateTime.now());

        // Reativar veículo
        Veiculo veiculo = veiculoRepository.findById(manutencao.getVeiculoId())
                .orElse(null);
        if (veiculo != null) {
            veiculo.setStatus(StatusVeiculo.ATIVO);
            veiculo.setDataAtualizacao(LocalDateTime.now());
            veiculoRepository.save(veiculo); // FIXME: sem transação
        }

        Manutencao concluida = manutencaoRepository.save(manutencao);

        // Notificar conclusão
        try {
            String placaVeiculo = veiculo != null ? veiculo.getPlaca() : "N/A";
            notificacaoClient.enviarNotificacao(
                "MANUTENCAO_CONCLUIDA",
                "gestor@rotalog.com",
                "Manutenção concluída para veículo " + placaVeiculo + ". Custo: R$ " + custoFinal
            );
        } catch (Exception e) {
            log.error("Falha ao notificar conclusão de manutenção: {}", e.getMessage());
        }

        return concluida;
    }

    /**
     * Cancelar manutenção
     */
    public Manutencao cancelarManutencao(Long manutencaoId) {
        Manutencao manutencao = buscarPorId(manutencaoId);
        manutencao.setStatus("CANCELADA");
        manutencao.setDataAtualizacao(LocalDateTime.now());

        // Se veículo estava em manutenção, reativar
        Veiculo veiculo = veiculoRepository.findById(manutencao.getVeiculoId()).orElse(null);
        if (veiculo != null && StatusVeiculo.MANUTENCAO.equals(veiculo.getStatus())) {
            veiculo.setStatus(StatusVeiculo.ATIVO);
            veiculo.setDataAtualizacao(LocalDateTime.now());
            veiculoRepository.save(veiculo);
        }

        log.info("Manutenção cancelada: id={}", manutencaoId);
        return manutencaoRepository.save(manutencao);
    }

    /**
     * Listar manutenções pendentes
     */
    public List<Manutencao> listarPendentes() {
        return manutencaoRepository.findByStatus("PENDENTE");
    }

    /**
     * Obter última manutenção de um veículo
     */
    public Manutencao obterUltimaManutencao(Long veiculoId) {
        Manutencao ultima = manutencaoRepository.findUltimaManutencao(veiculoId);
        if (ultima == null) {
            throw new RuntimeException("Nenhuma manutenção encontrada para veículo: " + veiculoId);
        }
        return ultima;
    }

    /**
     * Calcula custo de manutenção
     *
     * FIXME: Business logic hardcoded
     * FIXME: No configuration management
     */
    public Double calcularCustoManutencao(String modelo, Long quilometragem) {
        // FIXME: Hardcoded costs
        Double custoPorKm = 0.05;
        Double custoBase = 500.0;

        return custoBase + (quilometragem * custoPorKm);
    }

    /**
     * Verifica se veículo precisa de manutenção
     *
     * FIXME: Hardcoded thresholds
     */
    public Boolean precisaDeManutencao(Long veiculoId) {
        Veiculo veiculo = buscarVeiculoOuFalhar(veiculoId);

        // FIXME: Hardcoded threshold
        Long limiteQuilometragem = 50000L;

        return veiculo.getQuilometragem() != null && veiculo.getQuilometragem() >= limiteQuilometragem;
    }

    /**
     * Agenda manutenção preventiva
     *
     * FIXME: Complex business logic mixed with infrastructure concerns
     * FIXME: Hardcoded maintenance intervals
     * FIXME: Não persiste nenhum registro de manutenção nem calcula uma data/quilometragem-limite real
     */
    public void agendarManutencaoPreventiva(Long veiculoId, Long quilometragemLimite) {
        Veiculo veiculo = buscarVeiculoOuFalhar(veiculoId);

        log.info("Manutenção preventiva agendada para veículo {} em {} km",
            veiculo.getPlaca(), quilometragemLimite);

        // Notificar sobre agendamento
        notificacaoFallbackAdapter.notificarGestor(
            "MANUTENCAO_AGENDADA",
            "Manutenção preventiva agendada para veículo " + veiculo.getPlaca() + " em " + quilometragemLimite + " km"
        );
    }

    /**
     * Verifica a necessidade de manutenção preventiva para o veículo e, se aplicável,
     * dispara um alerta para o gestor. Chamado por {@link VeiculoService#atualizarQuilometragem}
     * após uma atualização de quilometragem.
     */
    public void verificarNecessidadeManutencao(Long veiculoId, Long quilometragemAtual) {
        if (Boolean.TRUE.equals(precisaDeManutencao(veiculoId))) {
            Veiculo veiculo = buscarVeiculoOuFalhar(veiculoId);
            notificacaoFallbackAdapter.notificarGestor(
                "ALERTA_MANUTENCAO",
                "Veículo " + veiculo.getPlaca() + " atingiu " + quilometragemAtual + " km. Agendar manutenção preventiva."
            );
        }
    }

    // FIXME: Hardcoded threshold, mesmo padrão de limiteQuilometragem em precisaDeManutencao
    private static final int MESES_LIMITE_SEM_MANUTENCAO = 6;

    /**
     * Verifica se já se passaram mais de {@link #MESES_LIMITE_SEM_MANUTENCAO} meses desde a
     * última manutenção do veículo. Na ausência de manutenção registrada, usa a data de
     * cadastro do veículo como referência.
     */
    private boolean excedeuTempoSemManutencao(Long veiculoId, Veiculo veiculo) {
        Manutencao ultima = manutencaoRepository.findUltimaManutencao(veiculoId);
        LocalDateTime dataReferencia = (ultima != null && ultima.getDataManutencao() != null)
                ? ultima.getDataManutencao()
                : veiculo.getDataCadastro();

        if (dataReferencia == null) {
            return false;
        }

        return dataReferencia.isBefore(LocalDateTime.now().minusMonths(MESES_LIMITE_SEM_MANUTENCAO));
    }

    /**
     * Retorna o motivo pelo qual o veículo é elegível para um alerta de manutenção
     * preventiva, ou {@code null} se nenhum critério for atendido.
     *
     * Critérios (elegível se QUALQUER um for verdadeiro):
     * - Km: {@link #precisaDeManutencao(Long)}
     * - Tempo: mais de {@link #MESES_LIMITE_SEM_MANUTENCAO} meses sem manutenção
     */
    public String motivoAlerta(Long veiculoId) {
        Veiculo veiculo = buscarVeiculoOuFalhar(veiculoId);

        boolean excedeuKm = Boolean.TRUE.equals(precisaDeManutencao(veiculoId));
        boolean excedeuTempo = excedeuTempoSemManutencao(veiculoId, veiculo);

        if (excedeuKm && excedeuTempo) {
            return "KM_E_TEMPO_EXCEDIDOS";
        }
        if (excedeuKm) {
            return "KM_EXCEDIDO";
        }
        if (excedeuTempo) {
            return "TEMPO_EXCEDIDO";
        }
        return null;
    }

    /**
     * Lista os veículos ATIVOS elegíveis para um alerta de manutenção preventiva
     * (ver {@link #motivoAlerta(Long)}).
     *
     * FIXME: N+1 - motivoAlerta refaz a busca do veículo por id para cada item da lista
     */
    public List<Veiculo> obterVeiculosElegiveisParaAlerta() {
        return veiculoRepository.findByStatus(StatusVeiculo.ATIVO).stream()
                .filter(veiculo -> motivoAlerta(veiculo.getId()) != null)
                .collect(Collectors.toList());
    }
}
