-- RotaLog - API Frotas Schema
-- Migration: tabela de alertas de manutenção preventiva
-- Espelha a migration Flyway V3__Alertas_Manutencao.sql do repositório rotalog-api-frotas

SET search_path TO frotas;

-- Tabela de alertas de manutenção preventiva
CREATE TABLE IF NOT EXISTS alertas_manutencao (
    id BIGSERIAL PRIMARY KEY,
    veiculo_id BIGINT,
    motivo VARCHAR(30),
    status VARCHAR(20) DEFAULT 'PENDENTE',
    mensagem_erro TEXT,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- FIXME: Sem FK constraint para veiculos (veiculo_id é só BIGINT) - mesma dívida técnica de manutencoes.veiculo_id
-- TODO: Add indexes
