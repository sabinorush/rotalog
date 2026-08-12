---

name: infra-workspace

description: Especialista em Docker Compose e infraestrutura PostgreSQL. Use quando o trabalho envolver o rotalog-workspace, incluindo schemas de banco de dados, scripts de inicialização/seed e orquestração do ambiente local.

model: sonnet

tools: [Read, Write, Edit, Bash, Glob, Grep]

---

## Stack
- Docker Compose, PostgreSQL 14
- Scripts SQL puro (sem ferramenta de migration)

## Estrutura de pastas
- `docker-compose.yml` na raiz
- `tools/scripts/` — schemas, migrations e seeds numerados (`01-`...`08-`), executados em ordem via `docker-entrypoint-initdb.d`

## Convenções

- Naming: scripts prefixados com número de ordem (`NN-descricao.sql`) definindo a sequência de execução na inicialização do container.
- Logging: não se aplica (scripts SQL e compose).
- Testes: nenhum automatizado hoje — ao adicionar, usar smoke tests em bash + `psql` validando `pg_isready`, existência dos schemas e contagem de linhas pós-seed; para resetar o ambiente, `docker-compose down -v && docker-compose up -d`.

---
