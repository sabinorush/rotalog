# ADR-0001: Hardening de `auth.js` e extração de responsabilidades de `entregas.js`

## Status

**Proposto** — 2026-08-12. Documento apenas de análise/decisão; nenhuma alteração de código foi feita.

## Contexto

Dois arquivos de `rotalog-api-entregas` foram apontados para revisão:

- `src/middleware/auth.js` (52 linhas) — middleware de autenticação usado por `/api/entregas` e `/api/frotas` (`index.js:75,77`).
- `src/routes/entregas.js` (365 linhas) — deveria conter só roteamento, mas concentra SQL raw, lógica de negócio e logging não estruturado.

`DIVIDAS-TECNICAS.md` já audita este repositório (seção 3, 51 débitos: 10 arquitetura, 12 inconsistência, 12 segurança, 6 dependências, 11 infraestrutura). Vale registrar que **os dois primeiros itens do "Top 10 críticos" cross-repo do documento são sobre `auth.js`** (`EN-SEC-01` e `EN-SEC-02`) — ou seja, esta ADR toca o ponto de maior severidade já mapeado no workspace inteiro, não apenas neste repositório.

## Diagnóstico — `auth.js`

| # | Local | Problema | ID / Sev |
|---|---|---|---|
| 1 | `auth.js:16-19` | Bypass total de autenticação quando `NODE_ENV !== 'production'` — `.env:5` (versionado) já define `NODE_ENV=development` | `EN-SEC-01` 🔴 |
| 2 | `auth.js:28-46` | "JWT" que não decodifica, não verifica assinatura nem expiração — qualquer string com 10+ caracteres é aceita e vira `req.user = {id:1, nome:'Admin', role:'admin'}` fixo | `EN-SEC-02` 🔴 |
| 3 | `auth.js:12` vs. `.env:19` | `JWT_SECRET` hardcoded como fallback **e** como valor real do `.env` versionado — é literalmente a mesma string (`super-secret-key-that-should-not-be-hardcoded`) nos dois lugares. Configurar a env var não muda nada porque o valor "configurado" é o mesmo do fallback | `EN-SEC-03` 🔴 |
| 4 | Repositório inteiro (confirmado por busca) | Nenhuma biblioteca JWT (`jsonwebtoken` ou similar) no `package.json`, apesar do arquivo se autodenominar "Auth Middleware" | `EN-DEP-05` 🔴 |
| 5 | `auth.js:46` vs. resto do repo (confirmado por busca) | `req.user.role` é fixado como `'admin'` mas **nenhum outro arquivo do repositório lê `req.user.role`** — RBAC é decorativo: existe o dado, não existe nenhuma checagem de autorização em lugar nenhum | achado novo, não catalogado em `DIVIDAS-TECNICAS.md` |
| 6 | `auth.js:17,43` | `console.log` em vez de logger estruturado | parte de `EN-INF-05` 🟡 (73 ocorrências de `console.*` no repo); viola a convenção do agente `node-entregas` ("NUNCA usar console.log em produção") |
| 7 | `auth.js:21,28,33` | `var` em vez de `const`/`let` | parte de `EN-INC-08` ⚪ |
| 8 | `auth.js:2` | Comentário de cabeçalho "Copied from StackOverflow (2020)" | não é vulnerabilidade em si, mas é um sinal explícito de proveniência não auditada do código de segurança mais sensível do serviço |

## Diagnóstico — `entregas.js`

| # | Local | Problema | ID / Sev |
|---|---|---|---|
| 1 | Arquivo inteiro | Zero camada de serviço — 100% da regra de negócio dentro dos route handlers (geração de número de pedido, cálculo de distância, transições de status, atribuição de veículo/motorista) | `EN-ARQ-02` 🟠 |
| 2 | `entregas.js:65-91` (rota `/stats`) | SQL raw com schema `entregas` hardcoded na query, fora do padrão Sequelize/`DB_SCHEMA` usado no resto do arquivo | `EN-ARQ-03` 🟡 |
| 3 | `entregas.js:65-91`, `120-133`, `336-363` vs. demais rotas | Mistura callback (`.then/.catch`) e `async/await` no mesmo arquivo — o próprio cabeçalho do arquivo já documenta isso (linha 4) | `EN-ARQ-01` 🟡 |
| 4 | Arquivo inteiro (365 linhas) | God file: roteamento + validação + regra de negócio + ORM + SQL raw + logging no mesmo arquivo, 28% do `src/` | `EN-ARQ-08` 🟡 |
| 5 | `entregas.js:167-190` (POST), `255-270` (PATCH status), `311-319` (PATCH atribuir), `338-357` (DELETE) | 4 fluxos escrevem em `Entrega` + `Rastreamento` sem `sequelize.transaction()` | `EN-ARQ-07` 🔴 |
| 6 | `entregas.js:54,88,112,130,192,196,224,272,276,321,325,360` | `console.log`/`console.error` em 12 pontos do arquivo, em vez de logger estruturado | parte de `EN-INF-05` 🟡; viola convenção do agente `node-entregas` |
| 7 | Blocos JSDoc antes de cada rota (linhas 1-8, 16-21, 59-64, 93-95, 117-119, 135-141, 202-204, 229-234, 281-286, 330-335) | Comentários em excesso, vários redundantes com o próprio código (`// Filtro por veículo (usado pelo api-frotas)` acima de código autoexplicativo) ou duplicando FIXMEs já centralizados em `DIVIDAS-TECNICAS.md` (ex.: cabeçalho do arquivo, linhas 4-7, repete quase literalmente `EN-ARQ-01`/`EN-ARQ-02`) | parte de `EN-INC-10` ⚪ (densidade alta de FIXME/TODO no repo) |
| 8 | `entregas.js:198` | Erro interno vazado ao cliente (`detalhes: error.message`) na criação de entrega | `EN-INC-02` 🟡 |
| 9 | `entregas.js:244-248` vs. `336-357` | Sem validação de transição de estado: qualquer status do enum é aceito em qualquer ordem; `DELETE` cancela entrega mesmo já `ENTREGUE` (comentário na própria linha 344 confirma a ausência da checagem) | `EN-SEC-12` 🟠 |
| 10 | `entregas.js:289,306-307` | Mass assignment: `motorista_nome`/`veiculo_modelo` gravados direto do `req.body`, sem consultar `api-frotas` para confirmar que existem | `EN-SEC-11` 🟡 / `EN-ARQ-09` 🟠 |
| 11 | `entregas.js:153` | `numero_pedido` gerado com `'PED-' + Date.now() + '-' + Math.floor(Math.random()*1000)` — não é UUID; coluna é `UNIQUE` | `EN-INC-09` 🟡 |
| 12 | `entregas.js:155-162` | Cálculo de distância "fake" (aproximação euclidiana em vez de Haversine ou API de rotas) embutido na rota | reforça item 1 (regra de negócio fora de service) |
| 13 | Arquivo inteiro | Sem middleware de validação de input (`joi`/`zod`/`express-validator`) — cada rota reimplementa (ou esquece) validação | `EN-INF-07` 🟠, causa raiz dos itens 9, 10 e de `EN-INC-06` |

## Relação entre os dois arquivos

`entregas.js` está montado atrás de `authMiddleware` (`index.js:75`). Isso significa que toda a superfície de escrita listada acima — criar, atualizar, mudar status, atribuir veículo/motorista, cancelar entrega — está, na prática, **sem autenticação real nenhuma**: o middleware que deveria protegê-la é exatamente o `auth.js` quebrado (achados 1-4 acima). Consequência direta para o sequenciamento: corrigir só `entregas.js` (transações, validação, camada de serviço) não reduz o risco de escrita anônima em produção enquanto `auth.js` não for corrigido — os dois precisam ser tratados na mesma iniciativa, não em paralelo isolado.

## Decisão proposta (não implementada)

**`auth.js`**
- Adotar uma biblioteca JWT real (`jsonwebtoken`, hoje ausente — `EN-DEP-05`), com verificação de assinatura e expiração.
- Eliminar o segredo hardcoded do código-fonte; garantir que o valor em `.env` não seja o mesmo do fallback e que o `.env` real de produção não seja versionado (débito compartilhado com `EN-SEC-03`, fora do escopo de só editar `auth.js`).
- Remover ou proteger o bypass por `NODE_ENV` — no mínimo, parar de versionar um `.env` com `NODE_ENV=development`.
- Decidir explicitamente entre implementar RBAC de verdade (consumir `req.user.role` em middlewares de autorização por rota) ou remover o campo, já que hoje ele não tem nenhum efeito (achado 5).
- Substituir `console.log` por logger estruturado, conforme convenção do agente `node-entregas`.

**`entregas.js`**
- Extrair uma camada de serviço (`EntregaService`) para: criação de entrega (incluindo geração de número de pedido e cálculo de distância), transições de status, atribuição de veículo/motorista — a rota passa a só validar entrada, chamar o service e formatar resposta.
- Mover a query de `/stats` para agregação via Sequelize (ou para um método de service dedicado), abandonando o SQL raw com schema hardcoded.
- Envolver os 4 fluxos de escrita dupla (`Entrega` + `Rastreamento`) em `sequelize.transaction()`.
- Padronizar o formato de resposta de erro e parar de vazar `error.message` ao cliente.
- Implementar validação de transição de estado (máquina de estados) e bloquear cancelamento de entrega já `ENTREGUE`.
- Validar `veiculo_placa`/`motorista_id` contra `api-frotas` antes de atribuir, em vez de aceitar `motorista_nome`/`veiculo_modelo` direto do corpo da requisição.
- Substituir `console.*` por logger estruturado.
- Reduzir comentários de rota ao essencial (o "porquê", não o "o quê"); remover duplicação com os FIXMEs já centralizados em `DIVIDAS-TECNICAS.md`.

## Alternativas consideradas

1. **Corrigir só `entregas.js` e deixar `auth.js` como está** — rejeitada: qualquer refatoração de `entregas.js` continuaria exposta anonimamente em produção por causa do middleware quebrado (ver seção anterior).
2. **Reescrever `auth.js` com um framework completo (ex.: Passport.js)** — considerada e descartada por ora: maior escopo do que o necessário; `jsonwebtoken` + middleware próprio resolve os achados 1-4 com mudança mais localizada e alinhada ao estilo atual do serviço.
3. **Mover a lógica de `entregas.js` para dentro dos models Sequelize (fat models)** em vez de criar uma camada de serviço — rejeitada: contraria a estrutura `routes -> services -> models` já documentada para este repositório.

## Consequências

**Positivas**: elimina a exposição de escrita anônima em `/api/entregas` (hoje efetivamente pública); `entregas.js` passa a ser testável por camada (service isolado de HTTP); reduz o risco de inconsistência `Entrega`/`Rastreamento` sob falha parcial (transações).

**Negativas / riscos**:
- Ativar autenticação real quebra qualquer cliente/script que hoje depende do bypass de desenvolvimento — inclui potencialmente o app `rastreamento` (React) e o `painel-admin` (Angular), que precisam ser auditados quanto a como (ou se) enviam `Authorization` antes desta mudança ir para produção.
- Implementar validação de transição de estado pode rejeitar fluxos hoje "funcionais" (mesmo que operacionalmente incorretos, como reabrir uma entrega `ENTREGUE`) — vale confirmar com quem opera o sistema se algum desses fluxos é usado intencionalmente antes de bloquear.
- Corrigir `EN-SEC-11`/mass assignment exige uma chamada síncrona a `api-frotas` no caminho de escrita de `entregas.js` — repetir o mesmo erro já mapeado em `EN-ARQ-04`/`FR-ARQ-03` (chamada HTTP sem timeout) anularia parte do ganho; a implementação futura precisa incluir timeout desde o início.

**Nota sobre o caráter didático do repositório**: `EN-SEC-01` e `EN-SEC-02` são os itens #1 e #2 do "Top 10 críticos" cross-repo de `DIVIDAS-TECNICAS.md` — provavelmente os exemplos mais usados no curso para ensinar "autenticação fake"/bypass de ambiente. Mesma ressalva já registrada na ADR equivalente de `rotalog-api-frotas` (`0001-refatoracao-veiculoservice.md`): antes de executar qualquer item desta ADR, vale decidir se o estado atual deve ser preservado (tag/branch) para uso futuro no curso, e/ou se outro exemplo equivalente deve substituí-lo.

## Referências

- `DIVIDAS-TECNICAS.md`, seção 3 (rotalog-api-entregas) — em especial `EN-ARQ-01`, `EN-ARQ-02`, `EN-ARQ-03`, `EN-ARQ-07`, `EN-ARQ-08`, `EN-ARQ-09`, `EN-INC-02`, `EN-INC-08`, `EN-INC-09`, `EN-INC-10`, `EN-SEC-01`, `EN-SEC-02`, `EN-SEC-03`, `EN-SEC-06`, `EN-SEC-11`, `EN-SEC-12`, `EN-DEP-05`, `EN-INF-05`, `EN-INF-07`.
- `rotalog-api-entregas/src/middleware/auth.js` (52 linhas, lido integralmente).
- `rotalog-api-entregas/src/routes/entregas.js` (365 linhas, lido integralmente).
- `rotalog-api-entregas/src/index.js` (linhas 33-42: CORS; 75-77: montagem das rotas com/sem `authMiddleware`).
- `rotalog-api-entregas/.env` (linhas 5 e 19).
- `.claude/agents/node-entregas.md` — convenções de stack (Jest+Supertest, logger estruturado, `routes -> services -> models`) usadas como referência para a arquitetura alvo proposta.
- `rotalog-api-frotas/docs/adr/0001-refatoracao-veiculoservice.md` — ADR anterior no mesmo formato, referida pelo usuário como modelo.
