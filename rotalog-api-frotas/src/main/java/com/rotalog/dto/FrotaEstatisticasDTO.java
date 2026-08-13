package com.rotalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO com as estatísticas agregadas da frota.
 */
@Getter
@Setter
@AllArgsConstructor
public class FrotaEstatisticasDTO {
    private long total;
    private long ativos;
    private long inativos;
    private long emManutencao;
}
