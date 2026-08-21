package com.rotalog.repository;

import com.rotalog.domain.StatusVeiculo;
import com.rotalog.domain.Veiculo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * VeiculoRepository
 *
 * FIXME: Queries nativas misturadas com derived queries
 * FIXME: Sem specification pattern
 */
@Repository
public interface VeiculoRepository extends JpaRepository<Veiculo, Long> {

    Optional<Veiculo> findByPlaca(String placa);

    List<Veiculo> findByStatus(StatusVeiculo status);

    long countByStatus(StatusVeiculo status);

    // FIXME: Query nativa quando poderia ser derived query
    @Query(value = "SELECT * FROM veiculos WHERE quilometragem > :km", nativeQuery = true)
    List<Veiculo> findVeiculosComQuilometragemAcimaDe(@Param("km") Long quilometragem);

    // FIXME: Sem paginação - pode retornar milhares de registros
    List<Veiculo> findByModeloContainingIgnoreCase(String modelo);

    @Query("SELECT v FROM Veiculo v WHERE v.status = 'ATIVO' AND v.quilometragem > :limite")
    List<Veiculo> findVeiculosAtivosComQuilometragemAlta(@Param("limite") Long limite);

    // Veículos que precisam de manutenção por critério de quilometragem (ver também
    // ManutencaoService#obterVeiculosElegiveisParaAlerta para a regra completa, que
    // combina este critério com o de tempo desde a última manutenção)
    List<Veiculo> findByStatusAndQuilometragemGreaterThan(StatusVeiculo status, Long quilometragem);

    List<Veiculo> findByAnoFabricacao(Integer anoFabricacao);

    Page<Veiculo> findByStatus(StatusVeiculo status, Pageable pageable);
}
