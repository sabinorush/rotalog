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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// Legacy service com dívida técnica intencional do curso (ver docs/adr/0001-refatoracao-veiculoservice.md)
@Slf4j
@Service
public class VeiculoService {

    @Autowired // FIXME: deveria usar injeção por construtor
    private VeiculoRepository veiculoRepository;

    @Autowired
    private NotificacaoFallbackAdapter notificacaoFallbackAdapter;

    @Autowired
    private ManutencaoService manutencaoService;

    @Autowired
    private SistemaExternoClient sistemaExternoClient;

    /**
     * FIXME: Cache em memória simples (ConcurrentHashMap), sem TTL/eviction e sem
     * invalidação entre instâncias - não serve para múltiplas réplicas do serviço.
     * Implementação leve do TODO "Add caching"; um cenário real usaria Spring Cache/Redis.
     */
    private final Map<Long, Veiculo> cachePorId = new ConcurrentHashMap<>();

    /**
     * FIXME: Contadores em memória, perdidos a cada restart e não exportados para
     * Prometheus/Grafana. Implementação leve do TODO "Add metrics"; um cenário real
     * usaria Micrometer.
     */
    private final Map<String, AtomicLong> metricas = new ConcurrentHashMap<>();

    private void incrementarMetrica(String nome) {
        metricas.computeIfAbsent(nome, chave -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Snapshot dos contadores acumulados desde o último restart do serviço.
     */
    public Map<String, Long> obterMetricas() {
        Map<String, Long> snapshot = new HashMap<>();
        metricas.forEach((nome, valor) -> snapshot.put(nome, valor.get()));
        return snapshot;
    }

    /**
     * FIXME: Trilha de auditoria via log estruturado, sem persistência dedicada e sem
     * identidade do ator (o sistema não tem autenticação hoje - ver {@link #verificarAutorizacao}).
     * Implementação leve do TODO "Add audit trail"; um cenário real persistiria em uma
     * tabela de auditoria com o usuário responsável pela ação.
     */
    private void auditoria(String acao, String detalhe) {
        log.info("AUDIT action={} detail={}", acao, detalhe);
    }

    /**
     * FIXME: Placeholder - o sistema não tem autenticação/identidade do chamador
     * (sem Spring Security, sem token/sessão), então não há o que checar de fato.
     * Implementação leve do TODO "Add authorization checks": centraliza o ponto de
     * extensão para quando essa infraestrutura existir, sem fingir aplicar uma
     * política de acesso que não existe.
     */
    private void verificarAutorizacao(String operacao) {
        log.debug("Autorização não implementada (placeholder) para operação: {}", operacao);
    }

    /**
     * FIXME: Sem paginação - pode retornar milhares de registros
     */
    public List<Veiculo> listarTodos() {
        log.info("Listando todos os veículos"); // misturando pt/en nos logs
        return veiculoRepository.findAll();
    }

    /**
     * Busca veículo por id, com cache em memória simples (ver {@link #cachePorId}).
     */
    public Veiculo buscarPorId(Long id) {
        Veiculo cacheado = cachePorId.get(id);
        if (cacheado != null) {
            incrementarMetrica("veiculo.buscarPorId.cacheHit");
            return cacheado;
        }

        Veiculo veiculo = veiculoRepository.findById(id)
                .orElseThrow(() -> new VeiculoNaoEncontradoException("Veículo não encontrado: " + id));
        cachePorId.put(id, veiculo);
        incrementarMetrica("veiculo.buscarPorId.cacheMiss");
        return veiculo;
    }

    public Veiculo buscarPorPlaca(String placa) {
        return veiculoRepository.findByPlaca(placa)
                .orElseThrow(() -> new VeiculoNaoEncontradoException("Veículo não encontrado com placa: " + placa));
    }

    /**
     * FIXME: This method does too many things
     * FIXME: Validation logic is mixed with business logic
     */
    public Veiculo registrarVeiculo(String placa, String modelo, Integer anoFabricacao) {
        verificarAutorizacao("REGISTRAR_VEICULO");

        // Validação misturada com lógica de negócio
        if (placa == null || placa.isEmpty()) {
            throw new PlacaInvalidaException("Placa é obrigatória");
        }

        if (placa.length() != 7) {
            throw new PlacaInvalidaException("Placa deve ter 7 caracteres");
        }

        // Verifica duplicidade
        Optional<Veiculo> existente = veiculoRepository.findByPlaca(placa);
        if (existente.isPresent()) {
            throw new PlacaDuplicadaException("Veículo com placa " + placa + " já existe");
        }

        // Validação do modelo
        if (modelo == null || modelo.isEmpty()) {
            throw new ModeloInvalidoException("Modelo é obrigatório");
        }

        // Validação do ano
        if (anoFabricacao == null || anoFabricacao < 1900 || anoFabricacao > 2100) {
            throw new AnoFabricacaoInvalidoException("Ano de fabricação inválido");
        }

        // Criar veículo
        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(placa.toUpperCase());
        veiculo.setModelo(modelo);
        veiculo.setAnoFabricacao(anoFabricacao);
        veiculo.setStatus(StatusVeiculo.ATIVO);
        veiculo.setQuilometragem(0L);
        veiculo.setDataCadastro(LocalDateTime.now());
        veiculo.setDataAtualizacao(LocalDateTime.now());

        Veiculo salvo = veiculoRepository.save(veiculo);
        if (salvo.getId() != null) {
            cachePorId.put(salvo.getId(), salvo);
        }
        incrementarMetrica("veiculo.registrado");
        log.info("Veículo registrado: {} - {}", salvo.getPlaca(), salvo.getModelo());
        auditoria("REGISTRAR_VEICULO", "veiculoId=" + salvo.getId() + ", placa=" + salvo.getPlaca());

        // Notificar api-notificacoes sobre novo veículo
        notificacaoFallbackAdapter.notificarGestor(
            "NOVO_VEICULO",
            "Novo veículo cadastrado: " + salvo.getPlaca() + " - " + salvo.getModelo()
        );

        return salvo;
    }

    /**
     * FIXME: Atualiza todos os campos sem verificar quais mudaram
     * FIXME: Sem validação de campos individuais
     */
    public Veiculo atualizarVeiculo(Long id, String modelo, Integer anoFabricacao, Long quilometragem) {
        verificarAutorizacao("ATUALIZAR_VEICULO");
        Veiculo veiculo = buscarPorId(id);

        if (modelo != null && !modelo.isEmpty()) {
            veiculo.setModelo(modelo);
        }
        if (anoFabricacao != null) {
            veiculo.setAnoFabricacao(anoFabricacao);
        }
        if (quilometragem != null) {
            if (quilometragem < veiculo.getQuilometragem()) {
                // FIXME: deveria lançar exceção, não apenas logar
                log.warn("Tentativa de reduzir quilometragem do veículo {}: {} -> {}",
                    id, veiculo.getQuilometragem(), quilometragem);
            }
            veiculo.setQuilometragem(quilometragem);
        }

        veiculo.setDataAtualizacao(LocalDateTime.now());
        Veiculo atualizado = veiculoRepository.save(veiculo);
        cachePorId.put(atualizado.getId(), atualizado);
        auditoria("ATUALIZAR_VEICULO", "veiculoId=" + id);
        return atualizado;
    }

    public Veiculo atualizarQuilometragem(Long veiculoId, Long novaQuilometragem) {
        verificarAutorizacao("ATUALIZAR_QUILOMETRAGEM");
        Veiculo veiculo = buscarPorId(veiculoId);

        if (novaQuilometragem < 0) {
            throw new QuilometragemInvalidaException("Quilometragem não pode ser negativa");
        }

        if (novaQuilometragem < veiculo.getQuilometragem()) {
            log.warn("Quilometragem menor que a atual!"); // FIXME: era System.out.println
        }

        veiculo.setQuilometragem(novaQuilometragem);
        veiculo.setDataAtualizacao(LocalDateTime.now());

        Veiculo atualizado = veiculoRepository.save(veiculo);
        cachePorId.put(atualizado.getId(), atualizado);
        auditoria("ATUALIZAR_QUILOMETRAGEM", "veiculoId=" + veiculoId + ", novaQuilometragem=" + novaQuilometragem);

        // Verificar se precisa de manutenção preventiva (política de manutenção delegada ao ManutencaoService)
        manutencaoService.verificarNecessidadeManutencao(veiculoId, novaQuilometragem);

        return atualizado;
    }

    /**
     * FIXME: Hardcoded status values
     * FIXME: No pagination
     */
    public List<Veiculo> obterVeiculosPorStatus(String status) {
        if (!status.equals("ATIVO") && !status.equals("INATIVO") && !status.equals("MANUTENCAO")) {
            throw new StatusInvalidoException("Status inválido: " + status);
        }

        return veiculoRepository.findByStatus(StatusVeiculo.valueOf(status));
    }

    /**
     * FIXME: No soft delete
     * FIXME: No cascade handling
     */
    public Veiculo desativarVeiculo(Long veiculoId) {
        verificarAutorizacao("DESATIVAR_VEICULO");
        Veiculo veiculo = buscarPorId(veiculoId);
        veiculo.setStatus(StatusVeiculo.INATIVO);
        veiculo.setDataAtualizacao(LocalDateTime.now());

        Veiculo desativado = veiculoRepository.save(veiculo);
        cachePorId.put(desativado.getId(), desativado);
        log.info("Veículo desativado: {}", veiculo.getPlaca());
        auditoria("DESATIVAR_VEICULO", "veiculoId=" + veiculoId + ", placa=" + veiculo.getPlaca());

        // Notificar sobre desativação
        notificacaoFallbackAdapter.notificarGestor(
            "VEICULO_DESATIVADO",
            "Veículo " + veiculo.getPlaca() + " foi desativado"
        );

        return desativado;
    }

    public Veiculo reativarVeiculo(Long veiculoId) {
        verificarAutorizacao("REATIVAR_VEICULO");
        Veiculo veiculo = buscarPorId(veiculoId);
        veiculo.setStatus(StatusVeiculo.ATIVO);
        veiculo.setDataAtualizacao(LocalDateTime.now());

        Veiculo reativado = veiculoRepository.save(veiculo);
        cachePorId.put(reativado.getId(), reativado);
        log.info("Veículo reativado: {}", veiculo.getPlaca());
        auditoria("REATIVAR_VEICULO", "veiculoId=" + veiculoId + ", placa=" + veiculo.getPlaca());
        return reativado;
    }

    public FrotaEstatisticasDTO obterEstatisticasFrota() {
        long total = veiculoRepository.count();
        long ativos = veiculoRepository.countByStatus(StatusVeiculo.ATIVO);
        long inativos = veiculoRepository.countByStatus(StatusVeiculo.INATIVO);
        long emManutencao = veiculoRepository.countByStatus(StatusVeiculo.MANUTENCAO);

        return new FrotaEstatisticasDTO(total, ativos, inativos, emManutencao);
    }

    /**
     * Sincroniza a frota com o sistema externo. Integração real, retry e circuit
     * breaker delegados a {@link SistemaExternoClient}. Nunca lança exceção para o
     * chamador - mesma política de fallback "engolir e logar" usada em
     * {@link NotificacaoFallbackAdapter}.
     */
    public void sincronizarComSistemaExterno() {
        log.info("Sincronização com sistema externo iniciada");

        try {
            boolean sucesso = sistemaExternoClient.sincronizar();
            incrementarMetrica(sucesso ? "veiculo.sincronizacaoExterna.sucesso" : "veiculo.sincronizacaoExterna.falha");
            auditoria("SINCRONIZAR_SISTEMA_EXTERNO", "sucesso=" + sucesso);
        } catch (RuntimeException e) {
            // Rede de segurança: SistemaExternoClient já trata falhas de comunicação
            // internamente, mas nenhuma falha inesperada deve propagar daqui.
            incrementarMetrica("veiculo.sincronizacaoExterna.erroInesperado");
            log.error("Sincronização com sistema externo falhou de forma inesperada: {}", e.getMessage());
        }
    }
}
