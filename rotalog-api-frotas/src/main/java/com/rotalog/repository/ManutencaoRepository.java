package com.rotalog.repository;

import com.rotalog.domain.Manutencao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ManutencaoRepository
 *
 * FIXME: Sem ordenação padrão
 */
@Repository
public interface ManutencaoRepository extends JpaRepository<Manutencao, Long> {

    List<Manutencao> findByVeiculoId(Long veiculoId);

    List<Manutencao> findByStatus(String status);

    List<Manutencao> findByVeiculoIdAndStatus(Long veiculoId, String status);

    // FIXME: Query nativa quando poderia ser JPQL
    @Query(value = "SELECT * FROM manutencoes WHERE veiculo_id = :veiculoId ORDER BY data_manutencao DESC LIMIT 1", nativeQuery = true)
    Manutencao findUltimaManutencao(@Param("veiculoId") Long veiculoId);

    List<Manutencao> findByStatusAndDataManutencaoBetween(String status, LocalDateTime inicio, LocalDateTime fim);

    Page<Manutencao> findByVeiculoId(Long veiculoId, Pageable pageable);
}
