/**
 * Testes do EntregaService (src/services/EntregaService.js)
 *
 * Cobre a lógica de negócio extraída de src/routes/entregas.js (ADR-001,
 * Etapa 2) e as correções de qualidade aplicadas na Etapa 4: geração de
 * número de pedido via crypto.randomUUID(), distância por Haversine, máquina
 * de estados explícita para o campo `status`, ausência de vazamento de
 * `error.message` bruto e estatísticas via `Entrega.findAll` (sem raw SQL).
 *
 * `Entrega` e `Rastreamento` são mockados (jest.mock) - nenhum teste aqui
 * toca banco de dados real.
 */

jest.mock('../models', () => ({
    Entrega: {
        findAll: jest.fn(),
        findByPk: jest.fn(),
        findOne: jest.fn(),
        create: jest.fn()
    },
    Rastreamento: {
        create: jest.fn()
    }
}));

const { Entrega, Rastreamento } = require('../models');
const logger = require('../config/logger');
const {
    listar,
    buscarPorId,
    buscarPorNumeroPedido,
    obterEstatisticas,
    criar,
    atualizar,
    atualizarStatus,
    atribuir,
    cancelar,
    calcularDistanciaHaversine,
    STATUS_VALIDOS,
    TRANSICOES_STATUS,
    EntregaServiceError
} = require('./EntregaService');

function criarEntregaMock(overrides = {}) {
    return {
        id: 1,
        numero_pedido: 'PED-existente',
        status: 'PENDENTE',
        save: jest.fn().mockResolvedValue(undefined),
        ...overrides
    };
}

beforeEach(() => {
    jest.spyOn(logger, 'info').mockImplementation(() => {});
    jest.spyOn(logger, 'error').mockImplementation(() => {});
});

afterEach(() => {
    jest.restoreAllMocks();
    jest.clearAllMocks();
});

describe('EntregaService', () => {
    describe('listar', () => {
        it('whenChamadoSemFiltros_thenConsultaSemWhereEIncluiRastreamentos', async () => {
            // Arrange
            const entregasFake = [criarEntregaMock()];
            Entrega.findAll.mockResolvedValue(entregasFake);

            // Act
            const resultado = await listar();

            // Assert
            expect(resultado).toBe(entregasFake);
            expect(Entrega.findAll).toHaveBeenCalledWith({
                where: {},
                order: [['data_criacao', 'DESC']],
                include: [{
                    model: Rastreamento,
                    as: 'rastreamentos',
                    limit: 5,
                    order: [['data_evento', 'DESC']]
                }]
            });
        });

        it('whenFiltrosInformados_thenAplicaVeiculoStatusUppercaseEMotoristaNoWhere', async () => {
            // Arrange
            Entrega.findAll.mockResolvedValue([]);

            // Act
            await listar({ veiculo: 'ABC1234', status: 'entregue', motorista_id: 7 });

            // Assert
            expect(Entrega.findAll).toHaveBeenCalledWith(
                expect.objectContaining({
                    where: { veiculo_placa: 'ABC1234', status: 'ENTREGUE', motorista_id: 7 }
                })
            );
        });

        it('whenBancoFalha_thenLancaEntregaServiceError500SemExporMensagemOriginal', async () => {
            // Arrange
            const erroBanco = new Error('connection terminated unexpectedly');
            Entrega.findAll.mockRejectedValue(erroBanco);

            // Act & Assert
            await expect(listar()).rejects.toMatchObject({
                name: 'EntregaServiceError',
                status: 500,
                message: 'Erro interno do servidor'
            });
            expect(logger.error).toHaveBeenCalledWith(
                'Erro ao listar entregas',
                expect.objectContaining({ error: erroBanco.message })
            );
        });
    });

    describe('buscarPorId', () => {
        it('whenEntregaExiste_thenRetornaEntregaComRastreamentos', async () => {
            // Arrange
            const entregaFake = criarEntregaMock({ id: 5 });
            Entrega.findByPk.mockResolvedValue(entregaFake);

            // Act
            const resultado = await buscarPorId(5);

            // Assert
            expect(resultado).toBe(entregaFake);
            expect(Entrega.findByPk).toHaveBeenCalledWith(5, expect.objectContaining({
                include: [expect.objectContaining({ model: Rastreamento, as: 'rastreamentos' })]
            }));
        });

        it('whenEntregaNaoExiste_thenLancaEntregaServiceError404', async () => {
            // Arrange
            Entrega.findByPk.mockResolvedValue(null);

            // Act & Assert
            await expect(buscarPorId(999)).rejects.toMatchObject({
                name: 'EntregaServiceError',
                status: 404,
                message: 'Entrega não encontrada'
            });
        });

        it('whenBancoFalha_thenLancaEntregaServiceError500EGeraLogDeErro', async () => {
            // Arrange
            const erroBanco = new Error('timeout');
            Entrega.findByPk.mockRejectedValue(erroBanco);

            // Act & Assert
            await expect(buscarPorId(1)).rejects.toMatchObject({ status: 500 });
            expect(logger.error).toHaveBeenCalled();
        });
    });

    describe('buscarPorNumeroPedido', () => {
        it('whenPedidoExiste_thenRetornaEntrega', async () => {
            // Arrange
            const entregaFake = criarEntregaMock({ numero_pedido: 'PED-123' });
            Entrega.findOne.mockResolvedValue(entregaFake);

            // Act
            const resultado = await buscarPorNumeroPedido('PED-123');

            // Assert
            expect(resultado).toBe(entregaFake);
            expect(Entrega.findOne).toHaveBeenCalledWith({ where: { numero_pedido: 'PED-123' } });
        });

        it('whenPedidoNaoExiste_thenLancaEntregaServiceError404', async () => {
            // Arrange
            Entrega.findOne.mockResolvedValue(null);

            // Act & Assert
            await expect(buscarPorNumeroPedido('PED-inexistente')).rejects.toMatchObject({
                status: 404,
                message: 'Pedido não encontrado'
            });
        });

        it('whenBancoFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            Entrega.findOne.mockRejectedValue(new Error('falha de conexão'));

            // Act & Assert
            await expect(buscarPorNumeroPedido('PED-1')).rejects.toMatchObject({ status: 500 });
        });
    });

    describe('obterEstatisticas', () => {
        it('whenExistemEntregas_thenRetornaTotalSomadoEPorStatusViaFindAllAgregado', async () => {
            // Arrange
            Entrega.findAll.mockResolvedValue([
                { status: 'PENDENTE', total: '3', distancia_media: '10.50', peso_medio: '2.30' },
                { status: 'ENTREGUE', total: '7', distancia_media: '42.10', peso_medio: '5.00' }
            ]);

            // Act
            const resultado = await obterEstatisticas();

            // Assert
            expect(resultado.total).toBe(10);
            expect(resultado.por_status).toHaveLength(2);
            expect(resultado.gerado_em).toEqual(expect.any(String));
            expect(Entrega.findAll).toHaveBeenCalledWith(
                expect.objectContaining({ group: ['status'], raw: true })
            );
            // Não deve mais existir raw SQL (nenhuma chamada a sequelize.query)
            expect(Entrega.findAll.mock.calls[0][0].attributes[0]).toBe('status');
        });

        it('whenNaoExistemEntregas_thenRetornaTotalZero', async () => {
            // Arrange
            Entrega.findAll.mockResolvedValue([]);

            // Act
            const resultado = await obterEstatisticas();

            // Assert
            expect(resultado.total).toBe(0);
            expect(resultado.por_status).toEqual([]);
        });

        it('whenBancoFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            Entrega.findAll.mockRejectedValue(new Error('erro no agregador'));

            // Act & Assert
            await expect(obterEstatisticas()).rejects.toMatchObject({
                status: 500,
                message: 'Erro ao buscar estatísticas'
            });
        });
    });

    describe('criar', () => {
        it('whenEnderecosObrigatoriosAusentes_thenLancaEntregaServiceError400', async () => {
            // Arrange
            const dados = { origem_endereco: 'Rua A' };

            // Act & Assert
            await expect(criar(dados)).rejects.toMatchObject({
                status: 400,
                message: 'Endereços de origem e destino são obrigatórios'
            });
            expect(Entrega.create).not.toHaveBeenCalled();
        });

        it('whenDadosValidosSemCoordenadas_thenCriaEntregaSemDistanciaEComNumeroPedidoUUID', async () => {
            // Arrange
            const entregaCriada = criarEntregaMock({ id: 10 });
            Entrega.create.mockResolvedValue(entregaCriada);
            Rastreamento.create.mockResolvedValue({});

            // Act
            const resultado = await criar({
                origem_endereco: 'Rua A, 100',
                destino_endereco: 'Rua B, 200',
                peso_kg: 5
            });

            // Assert
            expect(resultado).toBe(entregaCriada);
            expect(Entrega.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    numero_pedido: expect.stringMatching(/^PED-[0-9a-f-]{36}$/i),
                    distancia_km: null,
                    tempo_estimado_minutos: null,
                    status: 'PENDENTE'
                })
            );
            expect(Rastreamento.create).toHaveBeenCalledWith(
                expect.objectContaining({ entrega_id: 10, evento: 'PEDIDO_CRIADO' })
            );
        });

        it('whenDadosValidosComCoordenadas_thenCalculaDistanciaComHaversineETempoEstimado', async () => {
            // Arrange
            Entrega.create.mockResolvedValue(criarEntregaMock({ id: 11 }));
            Rastreamento.create.mockResolvedValue({});

            // Act
            await criar({
                origem_endereco: 'Rua A',
                destino_endereco: 'Rua B',
                origem_lat: -23.55052,
                origem_lng: -46.633308,
                destino_lat: -22.906847,
                destino_lng: -43.172897
            });

            // Assert
            const argumentosCreate = Entrega.create.mock.calls[0][0];
            expect(argumentosCreate.distancia_km).toEqual(expect.any(Number));
            expect(argumentosCreate.distancia_km).toBeGreaterThan(350);
            expect(argumentosCreate.distancia_km).toBeLessThan(370);
            expect(argumentosCreate.tempo_estimado_minutos).toBe(Math.ceil(argumentosCreate.distancia_km * 2));
        });

        it('whenEntregaCreateFalha_thenLancaEntregaServiceError500SemExporMensagemOriginal', async () => {
            // Arrange
            const erroBanco = new Error('duplicate key value violates unique constraint');
            Entrega.create.mockRejectedValue(erroBanco);

            // Act & Assert
            await expect(criar({
                origem_endereco: 'Rua A',
                destino_endereco: 'Rua B'
            })).rejects.toMatchObject({
                status: 500,
                message: 'Erro ao criar entrega'
            });
            expect(logger.error).toHaveBeenCalledWith(
                'Erro ao criar entrega',
                expect.objectContaining({ error: erroBanco.message })
            );
        });

        it('whenRastreamentoCreateFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            Entrega.create.mockResolvedValue(criarEntregaMock({ id: 12 }));
            Rastreamento.create.mockRejectedValue(new Error('falha ao gravar rastreamento'));

            // Act & Assert
            await expect(criar({
                origem_endereco: 'Rua A',
                destino_endereco: 'Rua B'
            })).rejects.toMatchObject({ status: 500 });
        });
    });

    describe('atualizar', () => {
        it('whenEntregaNaoExiste_thenLancaEntregaServiceError404', async () => {
            // Arrange
            Entrega.findByPk.mockResolvedValue(null);

            // Act & Assert
            await expect(atualizar(1, {})).rejects.toMatchObject({ status: 404 });
        });

        it('whenCamposInformados_thenAtualizaSomenteOsCamposFornecidos', async () => {
            // Arrange
            const entrega = criarEntregaMock({
                origem_endereco: 'Antiga origem',
                destino_endereco: 'Antigo destino',
                peso_kg: 1,
                observacoes: 'antiga'
            });
            Entrega.findByPk.mockResolvedValue(entrega);

            // Act
            const resultado = await atualizar(1, {
                origem_endereco: 'Nova origem',
                peso_kg: 0,
                observacoes: ''
            });

            // Assert
            expect(resultado.origem_endereco).toBe('Nova origem');
            expect(resultado.destino_endereco).toBe('Antigo destino');
            expect(resultado.peso_kg).toBe(0);
            expect(resultado.observacoes).toBe('');
            expect(entrega.save).toHaveBeenCalledTimes(1);
        });

        it('whenNenhumCampoInformado_thenApenasAtualizaDataDeAtualizacao', async () => {
            // Arrange
            const entrega = criarEntregaMock({ origem_endereco: 'Origem', destino_endereco: 'Destino' });
            Entrega.findByPk.mockResolvedValue(entrega);

            // Act
            const resultado = await atualizar(1, {});

            // Assert
            expect(resultado.origem_endereco).toBe('Origem');
            expect(resultado.destino_endereco).toBe('Destino');
            expect(resultado.data_atualizacao).toEqual(expect.any(Date));
        });

        it('whenBuscaFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            Entrega.findByPk.mockRejectedValue(new Error('erro de conexão'));

            // Act & Assert
            await expect(atualizar(1, {})).rejects.toMatchObject({ status: 500 });
        });

        it('whenSalvarFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            const entrega = criarEntregaMock();
            entrega.save.mockRejectedValue(new Error('erro ao salvar'));
            Entrega.findByPk.mockResolvedValue(entrega);

            // Act & Assert
            await expect(atualizar(1, { peso_kg: 3 })).rejects.toMatchObject({ status: 500 });
        });
    });

    describe('atualizarStatus', () => {
        it('whenStatusNaoPertenceAoConjuntoValido_thenLancaEntregaServiceError400ComListaDeStatusValidos', async () => {
            // Act & Assert
            await expect(atualizarStatus(1, 'INEXISTENTE')).rejects.toMatchObject({
                status: 400,
                message: 'Status inválido',
                details: { statusValidos: STATUS_VALIDOS }
            });
            expect(Entrega.findByPk).not.toHaveBeenCalled();
        });

        it('whenEntregaNaoExiste_thenLancaEntregaServiceError404', async () => {
            // Arrange
            Entrega.findByPk.mockResolvedValue(null);

            // Act & Assert
            await expect(atualizarStatus(1, 'ATRIBUIDA')).rejects.toMatchObject({ status: 404 });
        });

        it('whenTransicaoInvalida_thenLancaEntregaServiceError409ENaoSalva', async () => {
            // Arrange: ENTREGUE é estado terminal, não pode voltar para PENDENTE
            const entrega = criarEntregaMock({ status: 'ENTREGUE' });
            Entrega.findByPk.mockResolvedValue(entrega);

            // Act & Assert
            await expect(atualizarStatus(1, 'PENDENTE')).rejects.toMatchObject({
                status: 409,
                message: 'Transição de status inválida: ENTREGUE -> PENDENTE'
            });
            expect(entrega.save).not.toHaveBeenCalled();
            expect(Rastreamento.create).not.toHaveBeenCalled();
        });

        it('whenStatusAtualNaoConstaNoMapaDeTransicoes_thenLancaEntregaServiceError409', async () => {
            // Arrange: dado legado/corrompido, status atual fora do mapa conhecido
            const entrega = criarEntregaMock({ status: 'LEGADO_DESCONHECIDO' });
            Entrega.findByPk.mockResolvedValue(entrega);

            // Act & Assert
            await expect(atualizarStatus(1, 'CANCELADA')).rejects.toMatchObject({ status: 409 });
        });

        it('whenTransicaoValidaParaEmTransito_thenAtualizaStatusRegistraDataDeColetaECriaEvento', async () => {
            // Arrange
            const entrega = criarEntregaMock({ status: 'ATRIBUIDA' });
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            // Act
            const resultado = await atualizarStatus(1, 'EM_TRANSITO');

            // Assert
            expect(resultado.status).toBe('EM_TRANSITO');
            expect(resultado.data_coleta).toEqual(expect.any(Date));
            expect(entrega.save).toHaveBeenCalledTimes(1);
            expect(Rastreamento.create).toHaveBeenCalledWith(
                expect.objectContaining({ evento: 'STATUS_ALTERADO', entrega_id: 1 })
            );
        });

        it('whenTransicaoValidaParaEntregue_thenAtualizaStatusERegistraDataDeEntrega', async () => {
            // Arrange
            const entrega = criarEntregaMock({ status: 'EM_TRANSITO' });
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            // Act
            const resultado = await atualizarStatus(1, 'ENTREGUE');

            // Assert
            expect(resultado.status).toBe('ENTREGUE');
            expect(resultado.data_entrega).toEqual(expect.any(Date));
        });

        it('whenBuscaFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            Entrega.findByPk.mockRejectedValue(new Error('timeout'));

            // Act & Assert
            await expect(atualizarStatus(1, 'ATRIBUIDA')).rejects.toMatchObject({ status: 500 });
        });

        it('whenSalvarFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            const entrega = criarEntregaMock({ status: 'PENDENTE' });
            entrega.save.mockRejectedValue(new Error('erro ao salvar'));
            Entrega.findByPk.mockResolvedValue(entrega);

            // Act & Assert
            await expect(atualizarStatus(1, 'ATRIBUIDA')).rejects.toMatchObject({ status: 500 });
        });
    });

    describe('atribuir', () => {
        it('whenEntregaNaoExiste_thenLancaEntregaServiceError404', async () => {
            // Arrange
            Entrega.findByPk.mockResolvedValue(null);

            // Act & Assert
            await expect(atribuir(1, { veiculo_placa: 'ABC1234', motorista_id: 1 }))
                .rejects.toMatchObject({ status: 404 });
        });

        it('whenPlacaOuMotoristaAusentes_thenLancaEntregaServiceError400', async () => {
            // Arrange
            Entrega.findByPk.mockResolvedValue(criarEntregaMock());

            // Act & Assert
            await expect(atribuir(1, { veiculo_placa: 'ABC1234' })).rejects.toMatchObject({
                status: 400,
                message: 'Placa do veículo e ID do motorista são obrigatórios'
            });
        });

        it('whenDadosValidos_thenAtribuiVeiculoMotoristaMudaStatusECriaEvento', async () => {
            // Arrange
            const entrega = criarEntregaMock({ status: 'PENDENTE' });
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            // Act
            const resultado = await atribuir(1, {
                veiculo_placa: 'ABC1234',
                motorista_id: 9,
                motorista_nome: 'João',
                veiculo_modelo: 'Fiorino'
            });

            // Assert
            expect(resultado.status).toBe('ATRIBUIDA');
            expect(resultado.veiculo_placa).toBe('ABC1234');
            expect(resultado.motorista_id).toBe(9);
            expect(resultado.motorista_nome).toBe('João');
            expect(resultado.veiculo_modelo).toBe('Fiorino');
            expect(Rastreamento.create).toHaveBeenCalledWith(
                expect.objectContaining({ evento: 'ENTREGA_ATRIBUIDA' })
            );
        });

        it('whenNomeMotoristaEModeloVeiculoNaoInformados_thenAssumeNull', async () => {
            // Arrange
            const entrega = criarEntregaMock();
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            // Act
            const resultado = await atribuir(1, { veiculo_placa: 'XYZ9999', motorista_id: 3 });

            // Assert
            expect(resultado.motorista_nome).toBeNull();
            expect(resultado.veiculo_modelo).toBeNull();
        });

        it('whenBuscaFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            Entrega.findByPk.mockRejectedValue(new Error('timeout'));

            // Act & Assert
            await expect(atribuir(1, { veiculo_placa: 'ABC1234', motorista_id: 1 }))
                .rejects.toMatchObject({ status: 500 });
        });

        it('whenSalvarFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            const entrega = criarEntregaMock();
            entrega.save.mockRejectedValue(new Error('erro ao salvar'));
            Entrega.findByPk.mockResolvedValue(entrega);

            // Act & Assert
            await expect(atribuir(1, { veiculo_placa: 'ABC1234', motorista_id: 1 }))
                .rejects.toMatchObject({ status: 500 });
        });
    });

    describe('cancelar', () => {
        it('whenEntregaNaoExiste_thenLancaEntregaServiceError404', async () => {
            // Arrange
            Entrega.findByPk.mockResolvedValue(null);

            // Act & Assert
            await expect(cancelar(1)).rejects.toMatchObject({ status: 404 });
        });

        it('whenEntregaJaEntregue_thenLancaEntregaServiceError409ENaoAlteraStatus', async () => {
            // Arrange
            const entrega = criarEntregaMock({ status: 'ENTREGUE' });
            Entrega.findByPk.mockResolvedValue(entrega);

            // Act & Assert
            await expect(cancelar(1)).rejects.toMatchObject({
                status: 409,
                message: 'Não é possível cancelar uma entrega com status ENTREGUE'
            });
            expect(entrega.status).toBe('ENTREGUE');
            expect(entrega.save).not.toHaveBeenCalled();
        });

        it('whenEntregaJaCancelada_thenLancaEntregaServiceError409', async () => {
            // Arrange
            const entrega = criarEntregaMock({ status: 'CANCELADA' });
            Entrega.findByPk.mockResolvedValue(entrega);

            // Act & Assert
            await expect(cancelar(1)).rejects.toMatchObject({ status: 409 });
        });

        it('whenEntregaPendenteOuAtribuidaOuEmTransito_thenCancelaERegistraEvento', async () => {
            // Arrange
            const entrega = criarEntregaMock({ status: 'EM_TRANSITO' });
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            // Act
            const resultado = await cancelar(1);

            // Assert
            expect(resultado.message).toBe('Entrega cancelada');
            expect(resultado.entrega.status).toBe('CANCELADA');
            expect(Rastreamento.create).toHaveBeenCalledWith(
                expect.objectContaining({ evento: 'ENTREGA_CANCELADA' })
            );
        });

        it('whenBuscaFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            Entrega.findByPk.mockRejectedValue(new Error('timeout'));

            // Act & Assert
            await expect(cancelar(1)).rejects.toMatchObject({ status: 500 });
        });

        it('whenSalvarFalha_thenLancaEntregaServiceError500', async () => {
            // Arrange
            const entrega = criarEntregaMock({ status: 'PENDENTE' });
            entrega.save.mockRejectedValue(new Error('erro ao salvar'));
            Entrega.findByPk.mockResolvedValue(entrega);

            // Act & Assert
            await expect(cancelar(1)).rejects.toMatchObject({ status: 500 });
        });
    });

    describe('calcularDistanciaHaversine', () => {
        it('whenMesmasCoordenadas_thenDistanciaEhZero', () => {
            // Arrange
            const lat = -23.55052;
            const lng = -46.633308;

            // Act
            const distancia = calcularDistanciaHaversine(lat, lng, lat, lng);

            // Assert
            expect(distancia).toBe(0);
        });

        it('whenCoordenadasConhecidas_thenCalculaDistanciaComPrecisaoDeHaversine', () => {
            // Arrange: São Paulo -> Rio de Janeiro, distância real ~357km
            // Act
            const distancia = calcularDistanciaHaversine(-23.55052, -46.633308, -22.906847, -43.172897);

            // Assert
            expect(distancia).toBeGreaterThan(350);
            expect(distancia).toBeLessThan(365);
        });
    });

    describe('TRANSICOES_STATUS (máquina de estados)', () => {
        it('whenEstadosTerminaisSaoConsultados_thenNaoPermitemNenhumaTransicaoDeSaida', () => {
            expect(TRANSICOES_STATUS.ENTREGUE).toEqual([]);
            expect(TRANSICOES_STATUS.CANCELADA).toEqual([]);
        });

        it('whenStatusValidosSaoListados_thenContemOsCincoStatusDoDominio', () => {
            expect(STATUS_VALIDOS).toEqual(['PENDENTE', 'ATRIBUIDA', 'EM_TRANSITO', 'ENTREGUE', 'CANCELADA']);
        });
    });

    describe('EntregaServiceError', () => {
        it('whenCriadoSemDetails_thenNaoPossuiPropriedadeDetails', () => {
            // Act
            const erro = new EntregaServiceError('mensagem qualquer', 400);

            // Assert
            expect(erro).toBeInstanceOf(Error);
            expect(erro.name).toBe('EntregaServiceError');
            expect(erro.status).toBe(400);
            expect(erro.details).toBeUndefined();
        });
    });
});
