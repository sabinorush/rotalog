/**
 * Auth Middleware
 *
 * Valida o token JWT enviado no header `Authorization: Bearer <token>`,
 * verificando assinatura e expiração via `jsonwebtoken`. O segredo vem
 * exclusivamente de `process.env.JWT_SECRET` (sem fallback hardcoded) e o
 * middleware se comporta da mesma forma em todos os ambientes - não há
 * bypass por NODE_ENV.
 *
 * Ver docs/adr/0001-hardening-auth-e-refatoracao-entregas.md e
 * docs/adr/ADR-001-refatoracao-auth-e-entregas-routes.md (Etapa 1) para o
 * diagnóstico que motivou esta reescrita.
 */

const jwt = require('jsonwebtoken');
const logger = require('../config/logger');

function authMiddleware(req, res, next) {
    const authHeader = req.headers.authorization;

    if (!authHeader) {
        return res.status(401).json({ error: 'Token não fornecido' });
    }

    const parts = authHeader.split(' ');
    if (parts.length !== 2 || parts[0] !== 'Bearer') {
        return res.status(401).json({ error: 'Token mal formatado' });
    }

    const token = parts[1];

    try {
        const decoded = jwt.verify(token, process.env.JWT_SECRET);
        req.user = decoded;
        return next();
    } catch (error) {
        logger.warn('Falha na validação do token JWT', { reason: error.message });
        return res.status(401).json({ error: 'Token inválido ou expirado' });
    }
}

module.exports = authMiddleware;
