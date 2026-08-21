package com.rotalog.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * AlertaManutencao entity
 *
 * FIXME: Sem FK constraint no banco (veiculo_id é só BIGINT)
 * FIXME: Sem cascade operations
 * FIXME: Sem índices para queries frequentes
 */
@Entity
@Table(name = "alertas_manutencao")
@Getter
@Setter
@NoArgsConstructor
public class AlertaManutencao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "veiculo_id")
    private Long veiculoId; // FIXME: deveria ser @ManyToOne com Veiculo

    @Column(name = "motivo")
    private String motivo; // KM_EXCEDIDO, TEMPO_EXCEDIDO, KM_E_TEMPO_EXCEDIDOS

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusAlerta status;

    @Column(name = "mensagem_erro")
    private String mensagemErro;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;
}
