package com.rotalog.repository;

import com.rotalog.domain.Motorista;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MotoristaRepository
 *
 * FIXME: Sem filtros avançados
 */
@Repository
public interface MotoristaRepository extends JpaRepository<Motorista, Long> {

    Optional<Motorista> findByCnh(String cnh);

    List<Motorista> findByStatus(String status);

    List<Motorista> findByNomeContainingIgnoreCase(String nome);

    // FIXME: Query nativa desnecessária
    @Query(value = "SELECT * FROM motoristas WHERE vencimento_cnh < CURRENT_DATE", nativeQuery = true)
    List<Motorista> findMotoristasComCnhVencida();

    // Motoristas disponíveis = ATIVO com CNH válida. Não existe hoje relacionamento
    // motorista-veículo no domínio (ver FIXME em Motorista), então "disponível" não
    // considera alocação a um veículo.
    @Query(value = "SELECT * FROM motoristas WHERE status = 'ATIVO' AND (vencimento_cnh IS NULL OR vencimento_cnh >= CURRENT_DATE)", nativeQuery = true)
    List<Motorista> findMotoristasDisponiveis();

    Page<Motorista> findByStatus(String status, Pageable pageable);
}
