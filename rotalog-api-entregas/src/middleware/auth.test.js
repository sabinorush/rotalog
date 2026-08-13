/**
 * Testes do middleware de autenticação (src/middleware/auth.js)
 *
 * Cobre o comportamento real pós-hardening: verificação de assinatura e
 * expiração via `jsonwebtoken`, sem bypass por NODE_ENV e sem "validação"
 * fake de token (ver docs/adr/0001-hardening-auth-e-refatoracao-entregas.md
 * e docs/adr/ADR-001-refatoracao-auth-e-entregas-routes.md, Etapa 1).
 */

const TEST_SECRET = 'test-secret-nao-usar-em-producao';

beforeAll(() => {
    process.env.JWT_SECRET = TEST_SECRET;
});

const jwt = require('jsonwebtoken');
const express = require('express');
const request = require('supertest');
const authMiddleware = require('./auth');
const logger = require('../config/logger');

function mockRes() {
    const res = {};
    res.status = jest.fn().mockReturnValue(res);
    res.json = jest.fn().mockReturnValue(res);
    return res;
}

function signValidToken(payload = { id: 42, nome: 'Ana', role: 'operador' }) {
    return jwt.sign(payload, TEST_SECRET, { expiresIn: '1h' });
}

describe('authMiddleware (unitário, req/res/next mockados)', () => {
    beforeEach(() => {
        jest.spyOn(logger, 'warn').mockImplementation(() => {});
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('retorna 401 "Token não fornecido" quando não há header authorization', () => {
        const req = { headers: {} };
        const res = mockRes();
        const next = jest.fn();

        authMiddleware(req, res, next);

        expect(res.status).toHaveBeenCalledWith(401);
        expect(res.json).toHaveBeenCalledWith({ error: 'Token não fornecido' });
        expect(next).not.toHaveBeenCalled();
    });

    it('retorna 401 "Token mal formatado" quando o header não tem duas partes (sem espaço)', () => {
        const req = { headers: { authorization: 'TokenSemEspaco' } };
        const res = mockRes();
        const next = jest.fn();

        authMiddleware(req, res, next);

        expect(res.status).toHaveBeenCalledWith(401);
        expect(res.json).toHaveBeenCalledWith({ error: 'Token mal formatado' });
        expect(next).not.toHaveBeenCalled();
    });

    it('retorna 401 "Token mal formatado" quando o header tem mais de duas partes', () => {
        const req = { headers: { authorization: 'Bearer abc def' } };
        const res = mockRes();
        const next = jest.fn();

        authMiddleware(req, res, next);

        expect(res.status).toHaveBeenCalledWith(401);
        expect(res.json).toHaveBeenCalledWith({ error: 'Token mal formatado' });
        expect(next).not.toHaveBeenCalled();
    });

    it('retorna 401 "Token mal formatado" quando o esquema não é "Bearer"', () => {
        const token = signValidToken();
        const req = { headers: { authorization: `Basic ${token}` } };
        const res = mockRes();
        const next = jest.fn();

        authMiddleware(req, res, next);

        expect(res.status).toHaveBeenCalledWith(401);
        expect(res.json).toHaveBeenCalledWith({ error: 'Token mal formatado' });
        expect(next).not.toHaveBeenCalled();
    });

    it('retorna 401 "Token inválido ou expirado" quando a assinatura é inválida (assinado com outro secret)', () => {
        const tokenComOutroSecret = jwt.sign({ id: 1 }, 'outro-secret-qualquer');
        const req = { headers: { authorization: `Bearer ${tokenComOutroSecret}` } };
        const res = mockRes();
        const next = jest.fn();

        authMiddleware(req, res, next);

        expect(res.status).toHaveBeenCalledWith(401);
        expect(res.json).toHaveBeenCalledWith({ error: 'Token inválido ou expirado' });
        expect(next).not.toHaveBeenCalled();
        expect(req.user).toBeUndefined();
        expect(logger.warn).toHaveBeenCalledWith(
            'Falha na validação do token JWT',
            expect.objectContaining({ reason: expect.any(String) })
        );
    });

    it('retorna 401 "Token inválido ou expirado" quando o token está expirado', () => {
        const tokenExpirado = jwt.sign({ id: 1 }, TEST_SECRET, { expiresIn: -10 });
        const req = { headers: { authorization: `Bearer ${tokenExpirado}` } };
        const res = mockRes();
        const next = jest.fn();

        authMiddleware(req, res, next);

        expect(res.status).toHaveBeenCalledWith(401);
        expect(res.json).toHaveBeenCalledWith({ error: 'Token inválido ou expirado' });
        expect(next).not.toHaveBeenCalled();
    });

    it('retorna 401 "Token inválido ou expirado" quando o token não é um JWT (string arbitrária)', () => {
        const req = { headers: { authorization: 'Bearer isso-nao-e-um-jwt-valido' } };
        const res = mockRes();
        const next = jest.fn();

        authMiddleware(req, res, next);

        expect(res.status).toHaveBeenCalledWith(401);
        expect(res.json).toHaveBeenCalledWith({ error: 'Token inválido ou expirado' });
        expect(next).not.toHaveBeenCalled();
    });

    it('chama next() e popula req.user com o payload decodificado quando o token é válido', () => {
        const payload = { id: 42, nome: 'Ana', role: 'operador' };
        const token = signValidToken(payload);
        const req = { headers: { authorization: `Bearer ${token}` } };
        const res = mockRes();
        const next = jest.fn();

        authMiddleware(req, res, next);

        expect(next).toHaveBeenCalledTimes(1);
        expect(res.status).not.toHaveBeenCalled();
        expect(req.user).toMatchObject(payload);
        expect(req.user.iat).toEqual(expect.any(Number));
        expect(req.user.exp).toEqual(expect.any(Number));
    });
});

describe('authMiddleware (integração via Supertest, rota protegida de exemplo)', () => {
    function buildApp() {
        const app = express();
        app.get('/protegida', authMiddleware, (req, res) => {
            res.status(200).json({ ok: true, user: req.user });
        });
        return app;
    }

    beforeEach(() => {
        jest.spyOn(logger, 'warn').mockImplementation(() => {});
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('bloqueia com 401 quando nenhum token é enviado', async () => {
        const app = buildApp();

        const response = await request(app).get('/protegida');

        expect(response.status).toBe(401);
        expect(response.body).toEqual({ error: 'Token não fornecido' });
    });

    it('bloqueia com 401 quando o token tem assinatura inválida', async () => {
        const app = buildApp();
        const tokenInvalido = jwt.sign({ id: 1 }, 'outro-secret-qualquer');

        const response = await request(app)
            .get('/protegida')
            .set('Authorization', `Bearer ${tokenInvalido}`);

        expect(response.status).toBe(401);
        expect(response.body).toEqual({ error: 'Token inválido ou expirado' });
    });

    it('libera acesso com um token válido e retorna req.user (payload real) no corpo', async () => {
        const app = buildApp();
        const token = signValidToken({ id: 7, nome: 'Bia', role: 'gestor' });

        const response = await request(app)
            .get('/protegida')
            .set('Authorization', `Bearer ${token}`);

        expect(response.status).toBe(200);
        expect(response.body.ok).toBe(true);
        expect(response.body.user).toMatchObject({ id: 7, nome: 'Bia', role: 'gestor' });
    });
});
