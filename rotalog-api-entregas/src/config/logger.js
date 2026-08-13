/**
 * Logger estruturado minimalista.
 *
 * O repositório não usa nenhuma lib de logging (winston/pino) hoje - todo
 * o código chama console.log/console.error diretamente (ver FIXMEs
 * espalhados em src/). Este módulo é o primeiro ponto do projeto a emitir
 * saída estruturada (JSON com nível, timestamp e mensagem), sem trazer uma
 * dependência nova só para isso.
 *
 * Uso: const logger = require('../config/logger');
 *      logger.warn('mensagem', { algumCampo: 'valor' });
 */

function write(level, message, meta) {
    const entry = {
        level,
        timestamp: new Date().toISOString(),
        message,
    };

    if (meta !== undefined) {
        entry.meta = meta;
    }

    const line = JSON.stringify(entry);

    if (level === 'error' || level === 'warn') {
        console.error(line);
    } else {
        console.log(line);
    }
}

module.exports = {
    info: (message, meta) => write('info', message, meta),
    warn: (message, meta) => write('warn', message, meta),
    error: (message, meta) => write('error', message, meta),
};
