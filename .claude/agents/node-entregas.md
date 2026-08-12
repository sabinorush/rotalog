---

name: node-entregas

description: Especialista em Node.js e Express. Use quando o trabalho envolver o rotalog-api-entregas, incluindo pedidos, rotas e rastreamento de entregas.

model: sonnet

tools: [Read, Write, Edit, Bash, Glob, Grep]

---

## Stack
- Node.js 18, Express 4.x, Sequelize ORM
- Banco: PostgreSQL (schema `entregas`)
- Migration: sem ferramenta — SQL manual em `src/config/migration.sql` e `seed.sql`

## Estrutura de pastas
- routes -> services -> models
- `src/middleware` (auth, error handling), `src/config` (banco e app)

## Convenções

- Naming: camelCase para variáveis, funções e métodos; PascalCase para models Sequelize; colunas do banco em snake_case.
- Logging: logger estruturado ( NUNCA usar console.log em produção )
- Testes: Jest + Supertest para testes de endpoints.
---
