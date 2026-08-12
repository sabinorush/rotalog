# Rotalog

## Visão Geral

Sistema de gestão de frotas e entregas. Gerencia veículos, motorista, entregas e notificação.
Composto por 3 APIs de backend e 2 frontends (monorepo nx).

## Contexto do projeto

Projeto didático (curso Alura "Desenvolvendo com IA") com dívida técnica
intencional: arquiteturas abandonadas, antipatterns e bugs sutis espalhados
pelos repositórios. Ao trabalhar aqui:

- Não corrija a dívida técnica proativamente — ela é material do curso.
- Siga o estilo (legado) de cada repositório ao adicionar features, a menos
  que o usuário peça refatoração explicitamente.
- Alguns serviços acessam diretamente o banco de dados de outros (ex.:
  `rotalog-api-notificacoes`), violando bounded context — isso é conhecido.
- READMEs individuais podem estar desatualizados em relação à estrutura real
  do código (ex.: arquitetura documentada mas nunca terminada); prefira o que
  está descrito abaixo, derivado do código atual.

## Repositórios

### rotalog-api-frotas (Java 11 + Spring Boot 2.7)

- Responsabilidade: veículos, motoristas, manutenções
- Banco de dados: PostgreSQL (schema `frotas`), porta 8080
- Migrações: Flyway em `src/main/resources/db/migration`
- Estrutura: controller -> service -> repository
- Entidades JPA em `domain` (não em `model`, como o README sugere)

### rotalog-api-entregas (Node.js 18 + Express 4.x)

- Responsabilidade: pedidos, rotas, rastreamento em tempo real
- Banco de dados: PostgreSQL (schema `entregas`), porta 3000
- Migrações: SQL manual (`src/config/migration.sql`, `seed.sql`), sem
  ferramenta de migration
- Estrutura: routes -> services -> models
- Models Sequelize em `src/models`

### rotalog-api-notificacoes (.NET Core 6)

- Responsabilidade: envio de notificações por e-mail e SMS, histórico de envios
- Banco de dados: PostgreSQL (schema `notificacoes`), porta 5000
- Entity Framework Core (Npgsql) + MediatR
- Estrutura real: Controllers -> Services -> Data (DbContext) / Models
  (o README descreve uma Clean Architecture com Domain/Application/
  Infrastructure que foi iniciada e abandonada — não existe no código)
- `HealthController` acessa diretamente o banco de outros serviços

### rotalog-frontend (monorepo Nx — Angular 18 + React 18)

- Responsabilidade: aplicações web para usuários finais e gestores
- `apps/painel-admin` (Angular 18, porta 4200): painel de gestão — dashboard,
  veículos, motoristas, manutenções, entregas
- `apps/rastreamento` (React 18, porta 3001): portal público de rastreamento
- Libs compartilhadas: `libs/shared-types`, `libs/ui-components`,
  `libs/api-contracts`
- Estrutura Angular: `app/{components,models,services}`
- Estrutura React: componentes em `src/components`
- Sempre rodar tarefas via `nx` (`nx run`, `nx run-many`, `nx affected`)

### rotalog-workspace (infraestrutura)

- Responsabilidade: orquestração via Docker Compose e documentação central
- PostgreSQL 14 com 3 schemas: `frotas`, `entregas`, `notificacoes`
- Scripts de inicialização em `tools/scripts/` (schemas -> migrations ->
  seeds), executados automaticamente no primeiro `docker-compose up`
- Redis, Kafka, Elasticsearch, Prometheus e Grafana aparecem como TODO no
  compose — nunca foram implementados
- Senha do Postgres hardcoded no `docker-compose.yml` (débito conhecido)
