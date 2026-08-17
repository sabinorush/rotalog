-- RotaLog - API Frotas Seed Data
-- Veículos adicionais para testar a regra de elegibilidade de alerta de manutenção preventiva
-- Regra (implementada no serviço Java, rotalog-api-frotas): veículo ATIVO e
-- (quilometragem > 50000 OU mais de 6 meses desde a última manutenção - considerando
-- a manutenção mais recente por data_manutencao, ou a data_cadastro do veículo quando
-- não há nenhuma manutenção registrada)
-- Datas relativas (CURRENT_DATE - INTERVAL ...) para o seed continuar válido em qualquer execução

SET search_path TO frotas;

-- Veículos
INSERT INTO veiculos (placa, modelo, ano_fabricacao, quilometragem, status, data_cadastro, data_atualizacao) VALUES
-- Cenário 1: elegível somente por KM (quilometragem > 50000); manutenção recente, então não elegível por tempo
('XPT1O23', 'Ford Cargo 1723', 2019, 85000, 'ATIVO', CURRENT_DATE - INTERVAL '3 years', CURRENT_DATE - INTERVAL '2 months'),
-- Cenário 2: elegível somente por KM; sem manutenção registrada, e data_cadastro recente (não elegível por tempo)
('QRZ4N56', 'Hyundai HR', 2021, 62000, 'ATIVO', CURRENT_DATE - INTERVAL '1 month', CURRENT_DATE - INTERVAL '1 month'),
-- Cenário 3: elegível somente por tempo (última manutenção há mais de 6 meses); KM baixo, não elegível por KM
('MNB7V89', 'Fiat Strada', 2022, 18000, 'ATIVO', CURRENT_DATE - INTERVAL '2 years', CURRENT_DATE - INTERVAL '9 months'),
-- Cenário 4: elegível somente por tempo; sem manutenção registrada, data_cadastro há mais de 6 meses
('LKJ0H12', 'Chevrolet S10', 2020, 22000, 'ATIVO', CURRENT_DATE - INTERVAL '10 months', CURRENT_DATE - INTERVAL '10 months'),
-- Cenário 5: elegível por ambos os critérios (KM alto E manutenção antiga)
('WSX3E45', 'Iveco Tector', 2018, 95000, 'ATIVO', CURRENT_DATE - INTERVAL '4 years', CURRENT_DATE - INTERVAL '8 months'),
-- Cenário 6 (contraste): NÃO elegível - KM baixo e manutenção recente
('PLM6K78', 'Renault Kangoo', 2023, 12000, 'ATIVO', CURRENT_DATE - INTERVAL '1 year', CURRENT_DATE - INTERVAL '1 month');

-- Manutenções associadas (via subquery por placa, já que os ids dependem do estado da sequence)

-- Cenário 1 (XPT1O23): manutenção recente (2 meses atrás) - não contribui para elegibilidade por tempo
INSERT INTO manutencoes (veiculo_id, tipo_manutencao, data_manutencao, quilometragem_manutencao, custo, descricao, status, data_criacao, data_atualizacao)
SELECT id, 'PREVENTIVA', CURRENT_DATE - INTERVAL '2 months', 82000, 950.00, 'Troca de óleo e filtros', 'CONCLUIDA', CURRENT_DATE - INTERVAL '2 months', CURRENT_DATE - INTERVAL '2 months'
FROM veiculos WHERE placa = 'XPT1O23';

-- Cenário 2 (QRZ4N56): elegível somente por KM, sem manutenção registrada
-- (nenhum INSERT em manutencoes para este veículo, propositalmente - elegibilidade por tempo cai no fallback data_cadastro)

-- Cenário 3 (MNB7V89): última manutenção há 9 meses - dispara elegibilidade por tempo
INSERT INTO manutencoes (veiculo_id, tipo_manutencao, data_manutencao, quilometragem_manutencao, custo, descricao, status, data_criacao, data_atualizacao)
SELECT id, 'PREVENTIVA', CURRENT_DATE - INTERVAL '9 months', 12000, 700.00, 'Revisão geral', 'CONCLUIDA', CURRENT_DATE - INTERVAL '9 months', CURRENT_DATE - INTERVAL '9 months'
FROM veiculos WHERE placa = 'MNB7V89';

-- Cenário 4 (LKJ0H12): elegível somente por tempo, sem manutenção registrada
-- (nenhum INSERT em manutencoes para este veículo, propositalmente - fallback usa data_cadastro, há 10 meses)

-- Cenário 5 (WSX3E45): manutenção há 8 meses somada a KM alto - dispara elegibilidade por ambos os critérios
INSERT INTO manutencoes (veiculo_id, tipo_manutencao, data_manutencao, quilometragem_manutencao, custo, descricao, status, data_criacao, data_atualizacao)
SELECT id, 'CORRETIVA', CURRENT_DATE - INTERVAL '8 months', 88000, 3200.00, 'Reparo no sistema de suspensão', 'CONCLUIDA', CURRENT_DATE - INTERVAL '8 months', CURRENT_DATE - INTERVAL '8 months'
FROM veiculos WHERE placa = 'WSX3E45';

-- Cenário 6 (PLM6K78 - contraste, não elegível): manutenção recente (1 mês atrás) e KM baixo
INSERT INTO manutencoes (veiculo_id, tipo_manutencao, data_manutencao, quilometragem_manutencao, custo, descricao, status, data_criacao, data_atualizacao)
SELECT id, 'PREVENTIVA', CURRENT_DATE - INTERVAL '1 month', 11500, 480.00, 'Troca de óleo e filtros', 'CONCLUIDA', CURRENT_DATE - INTERVAL '1 month', CURRENT_DATE - INTERVAL '1 month'
FROM veiculos WHERE placa = 'PLM6K78';

-- FIXME: Sem dados semeados na tabela alertas_manutencao - alertas devem ser gerados pelo serviço Java
