/**
 * EntregaService
 *
 * Concentra a lógica de negócio de entregas hoje espalhada em
 * `src/routes/entregas.js` (consultas, geração de número de pedido, cálculo
 * de distância, máquina de estados e criação de eventos de rastreamento).
 *
 * Etapa 2 da ADR-001 (docs/adr/ADR-001-refatoracao-auth-e-entregas-routes.md):
 * extração do service. As correções de qualidade da Etapa 4 (UUID para
 * número de pedido, Haversine, máquina de estados explícita, erros sem
 * vazamento de detalhes internos e estatísticas via Sequelize) já são
 * aplicadas diretamente aqui.
 *
 * `src/routes/entregas.js` ainda NÃO usa este service (isso é a Etapa 3,
 * fora do escopo desta mudança) - o comportamento observável das rotas
 * continua sendo definido pela lógica inline que já existe lá.
 */

const crypto = require('crypto');
const { Sequelize } = require('sequelize');
const { Entrega, Rastreamento } = require('../models');
const logger = require('../config/logger');

const RAIO_TERRA_KM = 6371;

/**
 * Erro de negócio do EntregaService.
 *
 * Carrega um `status` HTTP sugerido (consumido futuramente pelas rotas,
 * Etapa 3) e uma mensagem segura para exposição ao cliente. Erros
 * inesperados (ex.: falha de banco) nunca chegam aqui com a mensagem
 * original do erro - o erro completo é logado via logger estruturado e
 * apenas uma mensagem genérica é propagada.
 */
class EntregaServiceError extends Error {
    constructor(message, status = 500, details) {
        super(message);
        this.name = 'EntregaServiceError';
        this.status = status;
        if (details !== undefined) {
            this.details = details;
        }
    }
}

/**
 * Mapa explícito de transições de status válidas.
 *
 * PENDENTE -> ATRIBUIDA -> EM_TRANSITO -> ENTREGUE são os passos normais do
 * ciclo de vida. CANCELADA pode ser alcançado a partir de qualquer estado
 * não-terminal. ENTREGUE e CANCELADA são estados terminais (nenhuma
 * transição de saída é permitida a partir deles).
 */
const TRANSICOES_STATUS = {
    PENDENTE: ['ATRIBUIDA', 'CANCELADA'],
    ATRIBUIDA: ['EM_TRANSITO', 'CANCELADA'],
    EM_TRANSITO: ['ENTREGUE', 'CANCELADA'],
    ENTREGUE: [],
    CANCELADA: []
};

const STATUS_VALIDOS = Object.keys(TRANSICOES_STATUS);

function grausParaRadianos(graus) {
    return (graus * Math.PI) / 180;
}

/**
 * Distância em km entre duas coordenadas geográficas usando a fórmula de
 * Haversine (considera a curvatura da Terra, ao contrário da fórmula
 * euclidiana usada anteriormente na rota).
 */
function calcularDistanciaHaversine(origemLat, origemLng, destinoLat, destinoLng) {
    const dLat = grausParaRadianos(destinoLat - origemLat);
    const dLng = grausParaRadianos(destinoLng - origemLng);

    const a =
        Math.sin(dLat / 2) ** 2 +
        Math.cos(grausParaRadianos(origemLat)) *
            Math.cos(grausParaRadianos(destinoLat)) *
            Math.sin(dLng / 2) ** 2;

    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return RAIO_TERRA_KM * c;
}

/**
 * Lista entregas com filtros opcionais (veiculo, status, motorista_id) e os
 * 5 rastreamentos mais recentes de cada uma.
 */
async function listar(filtros = {}) {
    const where = {};

    if (filtros.veiculo) {
        where.veiculo_placa = filtros.veiculo;
    }
    if (filtros.status) {
        where.status = String(filtros.status).toUpperCase();
    }
    if (filtros.motorista_id) {
        where.motorista_id = filtros.motorista_id;
    }

    try {
        return await Entrega.findAll({
            where,
            order: [['data_criacao', 'DESC']],
            include: [{
                model: Rastreamento,
                as: 'rastreamentos',
                limit: 5,
                order: [['data_evento', 'DESC']]
            }]
        });
    } catch (error) {
        logger.error('Erro ao listar entregas', { error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro interno do servidor', 500);
    }
}

/**
 * Busca uma entrega pela chave primária, incluindo todos os rastreamentos.
 */
async function buscarPorId(id) {
    let entrega;

    try {
        entrega = await Entrega.findByPk(id, {
            include: [{
                model: Rastreamento,
                as: 'rastreamentos',
                order: [['data_evento', 'DESC']]
            }]
        });
    } catch (error) {
        logger.error('Erro ao buscar entrega por id', { id, error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro interno do servidor', 500);
    }

    if (!entrega) {
        throw new EntregaServiceError('Entrega não encontrada', 404);
    }

    return entrega;
}

/**
 * Busca uma entrega pelo número do pedido (campo único).
 */
async function buscarPorNumeroPedido(numeroPedido) {
    let entrega;

    try {
        entrega = await Entrega.findOne({ where: { numero_pedido: numeroPedido } });
    } catch (error) {
        logger.error('Erro ao buscar entrega por número de pedido', {
            numeroPedido,
            error: error.message,
            stack: error.stack
        });
        throw new EntregaServiceError('Erro interno do servidor', 500);
    }

    if (!entrega) {
        throw new EntregaServiceError('Pedido não encontrado', 404);
    }

    return entrega;
}

/**
 * Estatísticas agregadas de entregas por status (total, distância média e
 * peso médio), via `Entrega.findAll` com `attributes`/`group` do Sequelize
 * em vez da raw query SQL anterior.
 */
async function obterEstatisticas() {
    let stats;

    try {
        stats = await Entrega.findAll({
            attributes: [
                'status',
                [Sequelize.fn('COUNT', Sequelize.col('id')), 'total'],
                [Sequelize.fn('COALESCE', Sequelize.fn('AVG', Sequelize.col('distancia_km')), 0), 'distancia_media'],
                [Sequelize.fn('COALESCE', Sequelize.fn('AVG', Sequelize.col('peso_kg')), 0), 'peso_medio']
            ],
            group: ['status'],
            raw: true
        });
    } catch (error) {
        logger.error('Erro ao buscar estatísticas de entregas', { error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro ao buscar estatísticas', 500);
    }

    const totalEntregas = stats.reduce((acc, s) => acc + parseInt(s.total, 10), 0);

    return {
        total: totalEntregas,
        por_status: stats,
        gerado_em: new Date().toISOString()
    };
}

/**
 * Cria uma nova entrega: gera o número do pedido, calcula a distância
 * (Haversine, quando as coordenadas de origem/destino são informadas) e
 * registra o evento inicial de rastreamento.
 */
async function criar(dados = {}) {
    const {
        origem_endereco: origemEndereco,
        destino_endereco: destinoEndereco,
        peso_kg: pesoKg,
        observacoes,
        origem_lat: origemLat,
        origem_lng: origemLng,
        destino_lat: destinoLat,
        destino_lng: destinoLng
    } = dados;

    if (!origemEndereco || !destinoEndereco) {
        throw new EntregaServiceError('Endereços de origem e destino são obrigatórios', 400);
    }

    const numeroPedido = 'PED-' + crypto.randomUUID();

    let distanciaKm = null;
    if (origemLat && origemLng && destinoLat && destinoLng) {
        distanciaKm = calcularDistanciaHaversine(
            Number(origemLat),
            Number(origemLng),
            Number(destinoLat),
            Number(destinoLng)
        );
    }

    const tempoEstimado = distanciaKm ? Math.ceil(distanciaKm * 2) : null; // 2 min/km

    try {
        const entrega = await Entrega.create({
            numero_pedido: numeroPedido,
            origem_endereco: origemEndereco,
            destino_endereco: destinoEndereco,
            origem_lat: origemLat || null,
            origem_lng: origemLng || null,
            destino_lat: destinoLat || null,
            destino_lng: destinoLng || null,
            peso_kg: pesoKg || null,
            distancia_km: distanciaKm,
            tempo_estimado_minutos: tempoEstimado,
            status: 'PENDENTE',
            observacoes: observacoes || null,
            data_criacao: new Date(),
            data_atualizacao: new Date()
        });

        await Rastreamento.create({
            entrega_id: entrega.id,
            evento: 'PEDIDO_CRIADO',
            descricao: 'Pedido de entrega criado: ' + numeroPedido,
            data_evento: new Date()
        });

        logger.info('Entrega criada', { numeroPedido });

        return entrega;
    } catch (error) {
        logger.error('Erro ao criar entrega', { error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro ao criar entrega', 500);
    }
}

/**
 * Atualiza campos editáveis de uma entrega existente (endereços, peso e
 * observações).
 */
async function atualizar(id, dados = {}) {
    let entrega;

    try {
        entrega = await Entrega.findByPk(id);
    } catch (error) {
        logger.error('Erro ao buscar entrega para atualizar', { id, error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro ao atualizar entrega', 500);
    }

    if (!entrega) {
        throw new EntregaServiceError('Entrega não encontrada', 404);
    }

    const {
        origem_endereco: origemEndereco,
        destino_endereco: destinoEndereco,
        peso_kg: pesoKg,
        observacoes
    } = dados;

    if (origemEndereco) entrega.origem_endereco = origemEndereco;
    if (destinoEndereco) entrega.destino_endereco = destinoEndereco;
    if (pesoKg !== undefined) entrega.peso_kg = pesoKg;
    if (observacoes !== undefined) entrega.observacoes = observacoes;
    entrega.data_atualizacao = new Date();

    try {
        await entrega.save();
    } catch (error) {
        logger.error('Erro ao salvar atualização de entrega', { id, error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro ao atualizar entrega', 500);
    }

    return entrega;
}

/**
 * Atualiza o status de uma entrega, validando a transição contra
 * `TRANSICOES_STATUS` antes de persistir e registrar o evento.
 */
async function atualizarStatus(id, status) {
    if (!STATUS_VALIDOS.includes(status)) {
        throw new EntregaServiceError('Status inválido', 400, { statusValidos: STATUS_VALIDOS });
    }

    let entrega;

    try {
        entrega = await Entrega.findByPk(id);
    } catch (error) {
        logger.error('Erro ao buscar entrega para atualizar status', { id, error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro ao atualizar status', 500);
    }

    if (!entrega) {
        throw new EntregaServiceError('Entrega não encontrada', 404);
    }

    const statusAnterior = entrega.status;
    const transicoesPermitidas = TRANSICOES_STATUS[statusAnterior] || [];

    if (!transicoesPermitidas.includes(status)) {
        throw new EntregaServiceError(
            `Transição de status inválida: ${statusAnterior} -> ${status}`,
            409
        );
    }

    entrega.status = status;
    entrega.data_atualizacao = new Date();

    if (status === 'EM_TRANSITO') {
        entrega.data_coleta = new Date();
    }
    if (status === 'ENTREGUE') {
        entrega.data_entrega = new Date();
    }

    try {
        await entrega.save();
        await Rastreamento.create({
            entrega_id: entrega.id,
            evento: 'STATUS_ALTERADO',
            descricao: `Status alterado de ${statusAnterior} para ${status}`,
            data_evento: new Date()
        });
    } catch (error) {
        logger.error('Erro ao salvar atualização de status', { id, error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro ao atualizar status', 500);
    }

    logger.info('Status de entrega atualizado', {
        numeroPedido: entrega.numero_pedido,
        statusAnterior,
        statusNovo: status
    });

    return entrega;
}

/**
 * Atribui veículo e motorista a uma entrega e marca o status como
 * ATRIBUIDA, registrando o evento correspondente.
 */
async function atribuir(id, dados = {}) {
    let entrega;

    try {
        entrega = await Entrega.findByPk(id);
    } catch (error) {
        logger.error('Erro ao buscar entrega para atribuir', { id, error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro ao atribuir entrega', 500);
    }

    if (!entrega) {
        throw new EntregaServiceError('Entrega não encontrada', 404);
    }

    const {
        veiculo_placa: veiculoPlaca,
        motorista_id: motoristaId,
        motorista_nome: motoristaNome,
        veiculo_modelo: veiculoModelo
    } = dados;

    if (!veiculoPlaca || !motoristaId) {
        throw new EntregaServiceError('Placa do veículo e ID do motorista são obrigatórios', 400);
    }

    entrega.veiculo_placa = veiculoPlaca;
    entrega.motorista_id = motoristaId;
    entrega.motorista_nome = motoristaNome || null;
    entrega.veiculo_modelo = veiculoModelo || null;
    entrega.status = 'ATRIBUIDA';
    entrega.data_atualizacao = new Date();

    try {
        await entrega.save();
        await Rastreamento.create({
            entrega_id: entrega.id,
            evento: 'ENTREGA_ATRIBUIDA',
            descricao: `Atribuída ao veículo ${veiculoPlaca} e motorista #${motoristaId}`,
            data_evento: new Date()
        });
    } catch (error) {
        logger.error('Erro ao salvar atribuição de entrega', { id, error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro ao atribuir entrega', 500);
    }

    logger.info('Entrega atribuída', { numeroPedido: entrega.numero_pedido, veiculoPlaca });

    return entrega;
}

/**
 * Cancela uma entrega (soft delete via status = CANCELADA), validando que o
 * estado atual permite o cancelamento (entregas ENTREGUE ou já CANCELADA
 * não podem ser canceladas novamente).
 */
async function cancelar(id) {
    let entrega;

    try {
        entrega = await Entrega.findByPk(id);
    } catch (error) {
        logger.error('Erro ao buscar entrega para cancelar', { id, error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro ao cancelar entrega', 500);
    }

    if (!entrega) {
        throw new EntregaServiceError('Entrega não encontrada', 404);
    }

    const statusAnterior = entrega.status;
    const transicoesPermitidas = TRANSICOES_STATUS[statusAnterior] || [];

    if (!transicoesPermitidas.includes('CANCELADA')) {
        throw new EntregaServiceError(
            `Não é possível cancelar uma entrega com status ${statusAnterior}`,
            409
        );
    }

    entrega.status = 'CANCELADA';
    entrega.data_atualizacao = new Date();

    try {
        await entrega.save();
        await Rastreamento.create({
            entrega_id: entrega.id,
            evento: 'ENTREGA_CANCELADA',
            descricao: 'Entrega cancelada',
            data_evento: new Date()
        });
    } catch (error) {
        logger.error('Erro ao salvar cancelamento de entrega', { id, error: error.message, stack: error.stack });
        throw new EntregaServiceError('Erro ao cancelar entrega', 500);
    }

    logger.info('Entrega cancelada', { numeroPedido: entrega.numero_pedido, statusAnterior });

    return { message: 'Entrega cancelada', entrega };
}

module.exports = {
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
    TRANSICOES_STATUS,
    STATUS_VALIDOS,
    EntregaServiceError
};
