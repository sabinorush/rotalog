# ADR-0002: Auditoria de contratos — `shared-types` e `api-contracts` vs. backends reais

## Status

**Aceito (como registro de auditoria)** — 2026-08-12. Documento apenas de análise/diagnóstico; nenhuma alteração de código foi feita. A decisão registrada aqui é **não corrigir agora** (ver seção "Decisão"), não a de executar as correções — execução depende de aprovação e priorização posteriores.

## Contexto

`libs/shared-types` e `libs/api-contracts` são duas das três libs "compartilhadas" do monorepo Nx `rotalog-frontend`. Ambas já eram conhecidas como dívida técnica antes desta auditoria (`DIVIDAS-TECNICAS.md`, `FE-ARQ-14`): não têm `project.json`/`tsconfig.json`, não são projetos Nx reais (`nx show projects` não as lista) e **zero import** em `apps/painel-admin` ou `apps/rastreamento` — código morto do ponto de vista de build. `libs/api-contracts/src/openapi.yaml` se autodeclara desatualizado há 2 anos (`FE-INF-17`). Além disso, `apps/painel-admin` mantém sua própria definição local de domínio em `models/index.ts`, divergente tanto do backend quanto de `shared-types` — três definições concorrentes do mesmo domínio no mesmo monorepo (`FE-INC-07`).

O que faltava era responder: **quão divergentes são essas definições em relação ao contrato real** exposto pelos 3 backends (`rotalog-api-frotas`, `rotalog-api-entregas`, `rotalog-api-notificacoes`)? `shared-types/src/index.ts` já tinha comentários `TODO` apontando supostos mismatches, mas eles foram tratados como pistas não confiáveis nesta auditoria, não como fonte de verdade — e vários se confirmaram **desatualizados ou errados** quanto à causa raiz (ver Diagnóstico). A verificação foi feita lendo diretamente os controllers/routes e as entidades/DTOs/models de cada backend, não a documentação.

## Diagnóstico

### (a) `Veiculo` vs. `rotalog-api-frotas`

Backend serializa a entidade JPA direto (sem DTO de resposta) em `VeiculoController` (`/api/veiculos`).

- **Falta**: `dataAtualizacao` (`Veiculo.java:48-49`).
- **Nome divergente não documentado**: TS usa `ano` (`index.ts:9`); backend usa `anoFabricacao` (`Veiculo.java:37`).
- **TODO desatualizado**: o comentário sobre `data_cadastro`/`dataCadastro` (`index.ts:10-11`) está errado — como a entidade Java é serializada direto, o JSON usa `dataCadastro` (camelCase), igual ao TS. Esse campo na verdade bate.
- **Enum quebrado**: TS declara `'ativo'|'inativo'|'manutencao'` minúsculo (`index.ts:12`); backend só aceita `ATIVO/INATIVO/MANUTENCAO` maiúsculo (`VeiculoService.java:98,195,266,291,307-309`).
- `ultimaManutencao?: Date` (`index.ts:14-15`) de fato não existe na entidade — TODO correto. O dado equivalente vem de um endpoint separado (`GET /manutencoes/veiculo/{id}/ultima`, `ManutencaoController.java:57-66`), não embutido na resposta de veículo.

### (b) `Motorista` vs. `rotalog-api-frotas`

- **Campos inteiramente inventados, sem correspondente algum no backend**: `email`, `telefone` (`index.ts:21-22`) — o próprio backend documenta a ausência: `Motorista.java:51`, `// FIXME: Falta campo email e telefone na tabela`.
- `veiculo_id: number` (`index.ts:27`) — não existe FK/coluna/campo em nenhuma camada; `Motorista.java:50` confirma: `// FIXME: Falta relacionamento com Veiculo`.
- `ativo: boolean` (`index.ts:28`) — backend não tem booleano de status; o campo real é `status: String` (`Motorista.java:42`), valores `"ATIVO"`/`"INATIVO"` usados de fato (`MotoristaService.java:74,150,162`). Divergência estrutural (boolean vs. enum string), não só de nome.
- **TODOs desatualizados**: `numero_cnh`/`dataVencimentoCnh` (`index.ts:23-26`) não batem com os nomes reais — o backend usa `cnh` e `vencimentoCnh` (`Motorista.java:32-39`), nomes diferentes, não apenas casing.
- **Faltam**: `categoriaCnh` (`Motorista.java:36`), `dataCadastro`, `dataAtualizacao` (`Motorista.java:44-48`).

### (c) `Entrega` + `EntregaEvento` vs. `rotalog-api-entregas`

Maior divergência do relatório. Backend serializa o model Sequelize direto (`underscored: true`, `database.js:32`) via `res.json(entrega)`.

- **TODO desatualizado**: `numero_pedido` (`index.ts:34`) apesar do comentário na linha 33, **bate exatamente** com o backend (`Entrega.js:19`).
- **Campos TS inventados em inglês, sem correspondente sob nenhum nome** — backend é 100% português/snake_case: `driver_name`→`motorista_nome`, `vehicle_plate`→`veiculo_placa`, `origin_address`→`origem_endereco`, `destination_address`→`destino_endereco`, `origin_lat/lng`→`origem_lat/lng`, `destination_lat/lng`→`destino_lat/lng`, `distance_km`→`distancia_km`, `estimated_time_minutes`→`tempo_estimado_minutos` (`Entrega.js:24-77`).
- **Tipo divergente real**: campos `DECIMAL` (lat/lng, distância, peso) são serializados como **string** pelo Sequelize/`pg` (sem parser customizado), TS declara `number` (`migration.sql:17-24`).
- `progress_percentage` (`index.ts:46`) **não existe em lugar nenhum** do backend — inventado no frontend.
- `events?: EntregaEvento[]` (`index.ts:47`) — o backend usa a chave **`rastreamentos`** (alias Sequelize, `models/index.js:12`), e só é incluído em `GET /entregas` e `GET /entregas/:id`, não em `POST`/`PUT`/`PATCH`/`DELETE`.
- **Enum de status**: TS usa minúsculo e **falta o valor `ATRIBUIDA`**; backend valida `['PENDENTE','ATRIBUIDA','EM_TRANSITO','ENTREGUE','CANCELADA']` maiúsculo (`entregas.js:245`).
- **Faltam por completo**: `veiculo_modelo`, `motorista_id`, `peso_kg`, `observacoes`, `data_criacao`/`data_atualizacao`, `data_coleta`/`data_entrega`.
- `EntregaEvento` (`index.ts:50-54`) vs. model `Rastreamento`: `timestamp`→`data_evento`, `description`→`descricao`, e `status` não existe — o campo análogo é `evento` (nome de evento de rastreamento, ex. `POSICAO_ATUALIZADA`, não um status de entrega). Faltam `id`, `entrega_id`, `latitude`, `longitude`.

### (d) `Notificacao` vs. `rotalog-api-notificacoes`

Contrato real é o DTO `NotificacaoResponse` (`DTOs/NotificacaoRequest.cs:27-42`), camelCase (`System.Text.Json` padrão).

- **Mismatch semântico grave**: TS trata `tipo: 'email'|'sms'|'webhook'` (`index.ts:59`) como canal de envio. No backend, `tipo` é o **tipo de evento de negócio** (`"NOVO_VEICULO"`, `"CNH_VENCIDA"` etc., string livre). O canal real é outro campo, `canal` (`Notificacao.cs:27-30`), com só `email`/`sms` de fato suportados — `webhook` lança `NotSupportedException` (`NotificacaoService.cs:239-250`). TS não tem `canal`.
- `enviado_em?: Date` (`index.ts:64`) → nome real é `dataEnvio` — o conceito existe, só com outro nome (TODO subestima o problema).
- **Faltam**: `canal`, `status` (envio: `PENDENTE`/`ENVIADO`/`FALHA`), `tentativas`, `erroMensagem`, `servicoOrigem`, `referenciaId`, `dataCriacao`.

### (e) `openapi.yaml` — endpoints não documentados

- **frotas**: só `GET/POST /veiculos` documentado; faltam ~8 rotas de veículos e **todo** o `MotoristaController` e `ManutencaoController`, incluindo `GET /api/health`. O comentário `# /api/motoristas/{id}/disponibilidade` (`openapi.yaml:74`) não corresponde a nenhum endpoint real.
- **entregas**: só `GET /entregas` documentado; falta o CRUD completo, todo `rastreamento.js` e todo o proxy `frotas.js`, mais `GET /api/health`.
- **notificações**: **nada** documentado de fato (só um comentário genérico).
- **Endpoint fantasma**: `GET /api/rastreamento/real-time` (`openapi.yaml:61-69`) está documentado (já `deprecated: true`) mas não existe em nenhum backend.
- **Schema já documentado, mas com dados errados**: `VeiculoInput` usa `ano` em vez de `anoFabricacao`; o schema `Entrega` herda os mesmos nomes em inglês inventados do TS.

### (f) Entidades de backend sem nenhuma interface em `shared-types`

`Manutencao` (`ManutencaoController`, `/api/manutencoes`), `Rastreamento` (mal coberto por `EntregaEvento`), `TemplateNotificacao` (`GET /notificacoes/templates`), `ConfiguracaoNotificacao` (a própria classe documenta: `// FIXME: Tabela criada mas nunca usada de verdade`), e agregados de estatísticas (`GET /entregas/stats`, `GET /notificacoes/stats`).

## Decisão

**Manter as libs como estão hoje** (não registradas como projects Nx, não consumidas por nenhum app) e registrar formalmente as divergências encontradas nesta auditoria, sem executar correção agora. Justificativa: `CLAUDE.md` do repositório instrui a não corrigir dívida técnica proativamente — esse estado (contratos fabricados/divergentes, endpoints não documentados) é material de curso, no mesmo espírito do que já está catalogado em `FE-ARQ-14`, `FE-INC-07`, `FE-INF-17`. Como as libs não são importadas por nenhum app (confirmado por grep), a divergência não tem efeito em runtime hoje — o risco é inteiramente para quem, no futuro, tentar consumi-las assumindo que refletem a API real.

## Alternativas consideradas (caminhos de remediação, nenhum executado)

1. **Descomissionar as libs** (remover `shared-types`/`api-contracts` como código morto) — mais simples, mas destrói o exemplo didático de "contrato desalinhado" que este ADR acabou de mapear.
2. **Gerar `shared-types`/`openapi.yaml` automaticamente a partir dos backends** (ex.: `springdoc-openapi` em frotas, `swagger-jsdoc` em entregas, Swashbuckle em notificações, com geração de tipos TS a partir do OpenAPI agregado) e registrar as libs como projects Nx reais consumidos pelos apps — resolveria a causa raiz (contratos escritos à mão, divergentes), mas é o maior esforço e exige decidir isso nos 3 backends, fora do escopo de um único repo.
3. **Corrigir campo a campo manualmente**, sem automação — rejeitada como primeiro passo: sem um gate (ex. `typecheck` real, já apontado como ausente em `FE-INC-04`) ou geração automática, a lib voltaria a divergir silenciosamente na próxima mudança de backend.

Nenhuma foi executada nesta ADR; ficam registradas para uma decisão futura explícita.

## Consequências

**Positivas**: existe agora um checklist rastreável (`arquivo:linha` de cada lado) para quem decidir agir sobre `FE-ARQ-14`/`FE-INC-07`/`FE-INF-17`; os TODOs incorretos em `shared-types/src/index.ts` foram identificados e não devem mais ser usados como guia sem verificação.

**Negativas / riscos**:
- As divergências continuam em produção — se `shared-types` ou `api-contracts` forem adotadas por um app no futuro sem revisão, os campos inventados (`progress_percentage`, `Motorista.email/telefone/ativo`, `Notificacao.tipo` como canal) quebrariam em runtime sem erro de compilação, já que a maioria dos consumos reais de API no repo usa `any` (`FE-INC-02`, `FE-INC-03`).
- `apps/painel-admin/.../models/index.ts` continua sendo uma **terceira** definição divergente do mesmo domínio (`FE-INC-07`) — este ADR audita `shared-types`/`api-contracts` contra os backends, mas não reconcilia as três fontes entre si; isso ficaria para uma ADR separada se for decidido unificar.
- `libs/ui-components` não foi auditada aqui (fora do escopo pedido) — `FE-INC-12` já registra que ela usa chaves de status em inglês que nenhum backend devolve, o que é consistente com os achados desta auditoria.

## Referências

- `rotalog-frontend/libs/shared-types/src/index.ts` (interfaces `Veiculo`, `Motorista`, `Entrega`, `EntregaEvento`, `Notificacao`).
- `rotalog-frontend/libs/api-contracts/src/openapi.yaml`.
- `rotalog-api-frotas`: `domain/Veiculo.java`, `domain/Motorista.java`, `domain/Manutencao.java`, `controller/VeiculoController.java`, `controller/MotoristaController.java`, `controller/ManutencaoController.java`, `dto/VeiculoRequest.java`.
- `rotalog-api-entregas`: `src/models/Entrega.js`, `src/models/Rastreamento.js`, `src/models/index.js`, `src/routes/entregas.js`, `src/routes/rastreamento.js`, `src/routes/frotas.js`, `src/config/database.js`, `src/config/migration.sql`, `src/index.js`.
- `rotalog-api-notificacoes`: `Models/Notificacao.cs`, `Models/TemplateNotificacao.cs`, `Models/ConfiguracaoNotificacao.cs`, `DTOs/NotificacaoRequest.cs`, `Controllers/NotificacoesController.cs`, `Services/NotificacaoService.cs`, `Program.cs`.
- `DIVIDAS-TECNICAS.md` (raiz do workspace) — `FE-ARQ-14`, `FE-INC-02`, `FE-INC-03`, `FE-INC-07`, `FE-INC-12`, `FE-INF-17`.
- `.claude/agents/nx-monorepo.md` — escopo/convenções usadas como referência para esta análise de nível workspace.
- `docs/adr/0001-modernizacao-app-rastreamento.md` — mesma convenção de formato/status usada aqui.
