-- RotaLog - API Frotas
-- Alerta de manutenção preventiva

CREATE TABLE IF NOT EXISTS alertas_manutencao (
    id BIGSERIAL PRIMARY KEY,
    veiculo_id BIGINT,
    motivo VARCHAR(30),
    status VARCHAR(20) DEFAULT 'PENDENTE',
    mensagem_erro TEXT,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
