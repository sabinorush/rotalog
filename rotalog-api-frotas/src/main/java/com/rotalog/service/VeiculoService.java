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
import java.util.List;
import java.util.Optional;

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

    /**
     * FIXME: Sem paginação - pode retornar milhares de registros
     * FIXME: Sem cache
     */
    public List<Veiculo> listarTodos() {
        log.info("Listando todos os veículos"); // misturando pt/en nos logs
        return veiculoRepository.findAll();
    }

    public Veiculo buscarPorId(Long id) {
        return veiculoRepository.findById(id)
                .orElseThrow(() -> new VeiculoNaoEncontradoException("Veículo não encontrado: " + id));
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
        log.info("Veículo registrado: {} - {}", salvo.getPlaca(), salvo.getModelo());

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
        return veiculoRepository.save(veiculo);
    }

    /**
     * FIXME: No audit trail
     */
    public Veiculo atualizarQuilometragem(Long veiculoId, Long novaQuilometragem) {
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
        Veiculo veiculo = buscarPorId(veiculoId);
        veiculo.setStatus(StatusVeiculo.INATIVO);
        veiculo.setDataAtualizacao(LocalDateTime.now());

        Veiculo desativado = veiculoRepository.save(veiculo);
        log.info("Veículo desativado: {}", veiculo.getPlaca());

        // Notificar sobre desativação
        notificacaoFallbackAdapter.notificarGestor(
            "VEICULO_DESATIVADO",
            "Veículo " + veiculo.getPlaca() + " foi desativado"
        );

        return desativado;
    }

    public Veiculo reativarVeiculo(Long veiculoId) {
        Veiculo veiculo = buscarPorId(veiculoId);
        veiculo.setStatus(StatusVeiculo.ATIVO);
        veiculo.setDataAtualizacao(LocalDateTime.now());

        log.info("Veículo reativado: {}", veiculo.getPlaca());
        return veiculoRepository.save(veiculo);
    }

    public FrotaEstatisticasDTO obterEstatisticasFrota() {
        long total = veiculoRepository.count();
        long ativos = veiculoRepository.countByStatus(StatusVeiculo.ATIVO);
        long inativos = veiculoRepository.countByStatus(StatusVeiculo.INATIVO);
        long emManutencao = veiculoRepository.countByStatus(StatusVeiculo.MANUTENCAO);

        return new FrotaEstatisticasDTO(total, ativos, inativos, emManutencao);
    }

    /**
     * FIXME: No retry logic
     * FIXME: No circuit breaker
     */
    public void sincronizarComSistemaExterno() {
        // FIXME: URL hardcoded
        log.info("Sincronização com sistema externo iniciada");

        // TODO: Implementar integração real
        // TODO: Adicionar retry logic
        // TODO: Adicionar circuit breaker
    }

    // TODO: Add proper exception handling
    // TODO: Add metrics
    // TODO: Add caching
    // TODO: Add authorization checks
    // TODO: Add audit trail
}
