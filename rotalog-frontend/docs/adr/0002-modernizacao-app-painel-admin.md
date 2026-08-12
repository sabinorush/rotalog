# ADR-0002: Modernização de padrões Angular 18/19 em `painel-admin` — HttpClient, lazy loading, control flow, signals e OnPush

## Status

**Proposta** — 2026-08-12. Documento apenas de análise/decisão; nenhuma alteração de código foi feita. Execução depende de aprovação e priorização posteriores.

Mesmo formato usado nas ADRs equivalentes já existentes no workspace:
- `rotalog-frontend/docs/adr/0001-modernizacao-app-rastreamento.md` (mesmo repo, app irmão)
- `rotalog-api-frotas/docs/adr/0001-refatoracao-veiculoservice.md`
- `rotalog-api-entregas/docs/adr/0001-hardening-auth-e-refatoracao-entregas.md`
- `rotalog-api-notificacoes/docs/adr/0001-hardening-e-decomposicao-notificacaoservice.md`

## Contexto

`apps/painel-admin` (Angular 18.2, painel administrativo de gestão de frotas/entregas — dashboard, veículos, motoristas, manutenções, entregas) foi auditado com foco em aderência às boas práticas atuais do Angular 18/19, cobrindo todos os módulos/telas em `apps/painel-admin/src/app/{components,models,services}` e o roteamento (`app.routes.ts`, `app.config.ts`).

Pontos verificados: uso de `fetch()` nativo vs. `HttpClient`; lazy loading por rota; novo control flow (`@if`/`@for`/`@switch`) vs. `*ngIf`/`*ngFor`; `signal()`/`computed()`; `inject()` vs. injeção via construtor; `ChangeDetectionStrategy.OnPush`; tratamento de erro HTTP; tipagem dos retornos de API e uso de `libs/shared-types`/`libs/api-contracts`; Reactive Forms vs. template-driven.

Diferente de `DIVIDAS-TECNICAS.md` (seção 5, rotalog-frontend), que cataloga débitos/bugs já existentes com IDs `FE-*`, esta ADR tem foco prospectivo: um roadmap de modernização Angular 18/19 ordenado por esforço × benefício. Onde os dois documentos se sobrepõem (ex.: `fetch()` vs. `HttpClient`, lazy loading, God components, libs não conectadas), os achados foram cruzados nesta ADR e os IDs `FE-*` são citados para evitar divergência entre os dois documentos. Os tópicos de signals, `inject()`, `OnPush`, novo control flow e Reactive Forms **não** estão cobertos por `DIVIDAS-TECNICAS.md` — são contribuição nova desta ADR.

Toda a análise foi feita por leitura direta do código-fonte, incluindo inspeção do bundle gerado em `dist/apps/painel-admin/browser/` (não apenas do `CLAUDE.md` do repo nem do frontmatter do agente `angular-painel-admin`).

Importante: este é o repositório didático do curso Alura, com dívida técnica **intencional** (`CLAUDE.md`). A seção "Nota sobre o caráter didático" ao final trata essa tensão.

## Diagnóstico

### 1. Chamadas HTTP — `fetch()` vs. `HttpClient`

🔴 Alto — cruza com `FE-ARQ-06`, `FE-ARQ-07`, `FE-ARQ-08` do `DIVIDAS-TECNICAS.md`.

Confirmado por leitura direta: **100% `fetch()`, 0% `HttpClient`, sem exceções** em todo o app.

| Local | Achado |
|---|---|
| `frotas.service.ts:32,48,59,75,91,110,123,134,150,168,180` | 11 chamadas `fetch()` (GET/POST/PUT/DELETE) |
| `entregas.service.ts:18,29,41` | 3 chamadas `fetch()` |
| `app.config.ts:1-7` | `provideHttpClient()` **não** está registrado — só `provideZoneChangeDetection` e `provideRouter` |
| `frotas.service.ts:4,20` | TODOs no próprio código já reconhecem a dívida ("Deveria usar HttpClient... Deveria injetar HttpClient aqui"), nunca implementada |
| `entregas.service.ts:6`, `frotas.service.ts:8` | URLs hardcoded (`localhost:3000`/`localhost:8080`), sem `src/environments/` (diretório não existe) |
| `frotas.service.ts:39-43,51-54,67-70,83-86,96-99`; `entregas.service.ts:21-24,32-35` | `try/catch` manual por chamada, `console.error` + retorno de `[]`/`null`/`false` — impossível hoje ter interceptor central sem reescrever os 14 pontos de chamada |
| `frotas.service.ts:15-17,26-29,105-107,189-193` | Cache manual (`veiculosCache`/`motoristasCache`) nunca invalidado automaticamente — o próprio comentário da linha 15 sugere `RxJS shareReplay` |

### 2. Lazy loading

🟠 Alto — cruza com `FE-ARQ-05`, `FE-ARQ-04`.

`app.routes.ts:1-20` importa e registra todos os 5 componentes de tela via `component:` (eager) — sem `loadComponent`/`loadChildren`. Comentário próprio na linha 8 já documenta a ausência.

Confirmado no bundle real: `dist/apps/painel-admin/browser/main-OESVX37W.js` = **328.125 bytes**, único arquivo, sem chunks lazy separados (`DIVIDAS-TECNICAS.md` `FE-ARQ-05` mede 363 kB em outra reprodução — mesma conclusão, build diferente).

Todos os 5 componentes já são `standalone: true`, sem dependência cruzada além de `CommonModule`/`FormsModule`/services `providedIn: 'root'` — migração para `loadComponent` é mecânica e de baixo risco:

| Componente | Arquivo | Observação |
|---|---|---|
| `DashboardComponent` | `components/dashboard/dashboard.component.ts` (349 linhas) | Rota default (`redirectTo: 'dashboard'`) — avaliar manter eager ou usar preloading |
| `VeiculosComponent` | `components/veiculos/veiculos.component.ts` (464 linhas) | Importa `FormsModule`; maior ganho de bundle-splitting (também é `FE-ARQ-01`, God Object) |
| `MotoristasComponent` | `components/motoristas/motoristas.component.ts` (251 linhas) | Importa `FormsModule` |
| `EntregasComponent` | `components/entregas/entregas.component.ts` (203 linhas) | Importa `FormsModule` só para filtros |
| `ManutencoesComponent` | `components/manutencoes/manutencoes.component.ts` (97 linhas) | Só leitura, menor ganho mas mesma mudança trivial |

`SidebarComponent` (`components/layout/sidebar.component.ts`) está fora do roteamento (usado direto em `app.component.html:1`), não é candidato a lazy load.

Atenção: `dashboard.component.ts:346-348` navega via `window.location.href = path` (`FE-ARQ-04`) em vez do `Router` — isso força reload completo da página e **anularia o benefício do lazy loading** ao clicar nos cards do dashboard. Deve ser corrigido junto.

### 3. Novo control flow (`@if`/`@for`/`@switch`)

🟡 Médio — não coberto por `DIVIDAS-TECNICAS.md`.

Ausente em 100% do código — todos os 6 componentes usam `*ngIf`/`*ngFor`/`[ngClass]` via `CommonModule`:
`dashboard.component.ts:20,22,27,78,100`; `entregas.component.ts:35,54,56,70,87,92`; `manutencoes.component.ts:19,21,36,54`; `motoristas.component.ts:31,33,48,49,55,57,72,77`; `veiculos.component.ts:32,35,50,71,76,126`.

O schematic oficial `ng generate @angular/core:control-flow` cobre a maior parte automaticamente; exige revisão manual dos `[ngClass]` dinâmicos (`dashboard.component.ts:78`, `motoristas.component.ts:49,55`). `CommonModule` continuaria necessário pelas pipes (`| number` em `veiculos.component.ts:62,152`, `manutencoes.component.ts:43`).

### 4. `signal()`/`computed()`

🟡 Médio — não coberto por `DIVIDAS-TECNICAS.md`; relacionado a `FE-ARQ-02` (duplicação de agregações).

Nenhum `signal(`/`computed(` no código. Candidatos concretos:
- `dashboard.component.ts:277-285,319-333` — 9 propriedades derivadas calculadas manualmente em `carregarDados()`, com contagem duplicada em `entregas.component.ts:196-198` (`FE-ARQ-02`: as duas contagens podem divergir sem aviso) — caso de livro para `computed()`.
- `entregas.component.ts:166,185-193`, `motoristas.component.ts:172,199-207`, `veiculos.component.ts:361,390-398` — listas `*Filtrados` recalculadas via método `filtrar()` quase idêntico nos 3 componentes (`FE-INC-10`) — poderiam virar um único `computed()` reagindo a signals de busca/status.
- `loading`/`error` como `boolean`/`string | null` simples em todos os componentes — candidatos naturais a `signal()`.

### 5. `inject()` vs. injeção via construtor

⚪ Baixo — não coberto por `DIVIDAS-TECNICAS.md`.

100% via construtor: `dashboard.component.ts:298-301`, `entregas.component.ts:172`, `manutencoes.component.ts:81`, `motoristas.component.ts:186`, `veiculos.component.ts:377`. Mudança puramente mecânica, sem risco funcional.

### 6. `ChangeDetectionStrategy.OnPush`

🟡 Médio, **dependente do item 4** — não coberto por `DIVIDAS-TECNICAS.md`.

Nenhum `@Component` define `changeDetection` (`dashboard.component.ts:10` já documenta isso via TODO). Ressalva específica deste projeto: os componentes fazem `await fetch(...)` a partir de `ngOnInit` e depois atribuem campos simples da classe (`dashboard.component.ts:314-316`, `entregas.component.ts:180`, etc.) — sem gatilho de `OnPush` (mudança de `@Input`, evento de template, `markForCheck()` ou signals), essa atribuição pode não disparar nova detecção de mudanças. `OnPush` só é seguro **depois** da adoção de signals (item 4), não isoladamente.

### 7. Tratamento de erro HTTP

🟠 Alto — cruza com `FE-ARQ-07`.

Sem interceptors (impossível hoje, sem pipeline de `HttpClient`), sem retry. UX de erro inconsistente entre telas: só `DashboardComponent` mostra banner de erro com "Tentar novamente" (`dashboard.component.ts:22-25,271`); `EntregasComponent`, `ManutencoesComponent`, `MotoristasComponent` e `VeiculosComponent` não tratam falha de rede — se o `fetch` falhar, a tela mostra silenciosamente "Nenhum registro encontrado" (`FE-ARQ-07`: erro de API vira `[]`/`null`, indistinguível de "sem dados").

### 8. Tipagem dos retornos de API / `libs/shared-types` e `libs/api-contracts`

🟠 Alto — cruza com `FE-INC-07`, `FE-ARQ-14`.

**Não usadas.** Grep por `shared-types`/`api-contracts`/`ui-components` em `apps/painel-admin` não retorna nenhum resultado. `tsconfig.base.json` não tem seção `"paths"` — não há sequer alias TS configurado para importar essas libs. `FE-ARQ-14` (`DIVIDAS-TECNICAS.md`) confirma que as 3 libs nem são projetos Nx válidos (sem `project.json`/`tsconfig.json` próprios).

O app usa modelos próprios em `models/index.ts:5-59` (`Veiculo`, `Motorista`, `Manutencao`, `Entrega`), com mistura `snake_case`/`camelCase` reconhecida no próprio arquivo (linhas 1,14). **Importante**: não é um "quick win" trocar diretamente para `libs/shared-types` — `FE-INC-07` já registra que `libs/shared-types/src/index.ts` tem campos **divergentes** do backend real e de `models/index.ts` (ex.: `dataCadastro` vs. `data_cadastro`, `numeroCnh` vs. `numero_cnh`, `numero_pedido`/`driver_name`/`vehicle_plate` em vez de `codigo_rastreio`/`motorista_nome`/`veiculo_placa`). Unificar tipagem exige antes reconciliar os três contratos divergentes. `ApiResponse<T>` genérico existe em `models/index.ts:62-66` mas nunca é usado por nenhum service.

### 9. Formulários (Reactive Forms vs. template-driven)

🟡 Médio — cruza com `FE-SEC-06`, `FE-SEC-09`.

`VeiculosComponent` (`veiculos.component.ts:13,370-373,436-454`) e `MotoristasComponent` (`motoristas.component.ts:12,179-182,236-250`) usam `FormsModule` + `[(ngModel)]` sobre objetos simples, sem `Validators`, com validação manual mínima via `alert()` (`veiculos.component.ts:441`, `motoristas.component.ts:239`; `FE-SEC-06`) e `confirm()` nativo para exclusão (`veiculos.component.ts:458`; `FE-SEC-09`). `EntregasComponent` usa `FormsModule` só para filtros (`entregas.component.ts:24-25`). `ManutencoesComponent` **não tem formulário de criação implementado** — o botão dispara `alert('Funcionalidade não implementada')` (`manutencoes.component.ts:16,94-96`).

### 10. Outros padrões desatualizados observados

- **Navegação fora do Router** (`FE-ARQ-04`): `dashboard.component.ts:346-348` usa `window.location.href` em vez de `Router.navigate()` — força reload completo, quebra a SPA.
- **God components** (`FE-ARQ-01`, `FE-ARQ-02`): `veiculos.component.ts` (464 linhas: lista+filtro+ordenação+form+detalhe) e `dashboard.component.ts` (349 linhas: 2 services, 9 cálculos, alertas hardcoded, tabela duplicada com `EntregasComponent`).
- **Duplicação quase literal** (`FE-INC-10`): `motoristas.component.ts:7` — "Componente copiado de VeiculosComponent com find-replace"; CSS quase idêntico repetido em `veiculos.component.ts:213-315` e `motoristas.component.ts:146-167`.
- **Template/CSS inline no `.ts`** em todos os 5 componentes de tela, diferente de `app.component.ts:12-13` (único com `templateUrl`/`styleUrl` externos). `FE-INC-06` (reproduzido via `nx build painel-admin`) já confirma que 4 componentes estão perto do budget de CSS por componente (2.05 kB) — extrair para arquivos externos ajudaria a aliviar essa margem, não é só estética.
- **`any` explícito** (`FE-INC-03`): `veiculos.component.ts:402` (`ordenar()`) e `dashboard.component.ts:338` (`catch (err: any)`), apesar de `tsconfig.base.json:14` ter `strict: true`.
- **Zone.js ainda ativo**: `project.json:19` (`polyfills: ['zone.js']`), `app.config.ts:6` (`provideZoneChangeDetection`) — sem uso de change detection zoneless; pré-requisito real seria signals (item 4) + `OnPush` (item 6) em todo o app primeiro.
- **Testes**: existe **um único** `*.spec.ts` no app (`app.component.spec.ts`) e ele está **quebrado** — espera `NxWelcomeComponent`, `h1` "Welcome painel-admin" e `title === 'painel-admin'` que não existem mais no `AppComponent` real (`FE-INF-02`). Mais grave: `DIVIDAS-TECNICAS.md` (`FE-INF-01`, `FE-INF-04`, reproduzidos) registra que **`nx test painel-admin` falha com `TS5095`** (conflito `moduleResolution:"bundler"` vs. `module:"commonjs"`) e **`nx lint painel-admin` crasha** (`@typescript-eslint/ban-ts-comment`) — ou seja, hoje não existe rede de segurança automatizada nenhuma (nem teste, nem lint) para validar qualquer item do roadmap abaixo.

## Decisão proposta (roadmap ordenado por esforço × benefício — não implementado)

| # | Item | Esforço | Arquivos afetados | Justificativa da posição |
|---|---|---|---|---|
| 1 | Lazy loading das 5 rotas (`loadComponent`) | Pequeno (1 arquivo) | `app.routes.ts` | Componentes já standalone; mudança mecânica, baixo risco, impacto direto no bundle de 328 kB hoje monolítico |
| 2 | Trocar `window.location.href` por `Router.navigate()` no dashboard | Pequeno (1 arquivo) | `dashboard.component.ts:346-348` | Bug real de UX/perf; deve ser corrigido junto do item 1 para não anular seu ganho |
| 3 | Migrar para novo control flow (`@if`/`@for`/`@switch`) | Pequeno-Médio (schematic + revisão manual) | Todos os 6 componentes | Amplamente automatizável (`ng generate @angular/core:control-flow`), padrão recomendado desde a v17, baixo risco funcional |
| 4 | `inject()` no lugar de construtor | Pequeno (find-replace) | 5 componentes | Puramente estilístico/DX, sem risco, mas impacto menor que os itens acima |
| 5 | Environment files + `fileReplacements` (elimina `API_URL` hardcoded) | Pequeno (2 services + `project.json`) | `entregas.service.ts:6`, `frotas.service.ts:8`, `project.json` | Baixo esforço isolado; naturalmente combinado com o item 6 |
| 6 | Migrar `fetch()` → `HttpClient` + `provideHttpClient()` + interceptor central de erro | Médio (2 services + `app.config.ts` + novo interceptor; toca componentes consumidores) | `frotas.service.ts`, `entregas.service.ts`, `app.config.ts` | Maior benefício estrutural (retry, cancelamento, `shareReplay` no lugar do cache manual, testabilidade via `HttpTestingController`), mas exige tocar todos os componentes consumidores |
| 7 | Adoção de `signal()`/`computed()` para estado e dados derivados | Médio-Grande (5 componentes) | `dashboard.component.ts`, `entregas.component.ts`, `motoristas.component.ts`, `veiculos.component.ts`, `manutencoes.component.ts` | Alto benefício (elimina `filtrar()` duplicado em 3 componentes), pré-requisito real do item 8 |
| 8 | `ChangeDetectionStrategy.OnPush` em todos os componentes | Médio (depende do item 7) | Todos os 6 componentes | Só é seguro depois da migração para signals, dado o padrão atual `await` + atribuição de campo fora de gatilhos de CD |
| 9 | Reactive Forms + `Validators` (Veículos, Motoristas) e implementação real do form de Manutenções | Médio (2-3 componentes) | `veiculos.component.ts`, `motoristas.component.ts`, `manutencoes.component.ts` | Ganho real de robustez/UX, mas exige reescrever a camada de formulário de cada tela |
| 10 | Extrair componentes/CSS duplicados (Veículos vs. Motoristas, badges de status, tabelas) em componentes Angular compartilhados | Grande (refatoração estrutural) | `veiculos.component.ts`, `motoristas.component.ts`, `entregas.component.ts`, `manutencoes.component.ts`, `dashboard.component.ts` | Alto ganho de manutenibilidade a longo prazo; não pode reaproveitar `libs/ui-components` diretamente (é React/TSX — `StatusBadge.tsx:1` importa `React`) |
| 11 | Reconciliar tipagem com `libs/shared-types`/`libs/api-contracts` (incl. configurar `paths` em `tsconfig.base.json`) | Grande (decisão entre repositórios) | `models/index.ts`, `libs/shared-types/src/index.ts`, `tsconfig.base.json`, + APIs backend | Não é ajuste isolado do frontend — os campos de `libs/shared-types` hoje não batem com o formato real das APIs (`FE-INC-07`) |
| 12 | Avaliar change detection zoneless (`provideExperimentalZonelessChangeDetection`) | Grande/estrutural | `app.config.ts`, `project.json`, todos os componentes | Ainda developer preview na v18/19; depende dos itens 7 e 8 concluídos em todo o app antes de fazer sentido — aposta de longo prazo |

## Alternativas consideradas

1. **Ativar `OnPush` antes de signals, isoladamente** — rejeitada: dado o padrão atual (`await` + atribuição de campo simples fora de gatilhos de CD), risco real de quebrar a re-renderização em produção sem aviso em build/lint (que, à parte, já estão quebrados — ver item 10 do diagnóstico).
2. **Consumir `libs/shared-types` imediatamente para "resolver" a tipagem (item 8 do diagnóstico)** — rejeitada como quick win: os campos da lib não batem com o backend real (`FE-INC-07`), adotá-la sem reconciliar aumentaria a superfície de bugs de mapeamento em vez de reduzi-la.
3. **Migrar tudo de uma vez (big-bang: HttpClient + signals + OnPush + control flow + forms em um único PR)** — rejeitada: mistura mudanças mecânicas de baixíssimo risco (itens 1-5) com mudanças estruturais que dependem de ordem (itens 7-8) e de refatoração de God components (item 10), dificultando isolar regressão caso algo quebre — ainda mais sem rede de teste/lint funcional hoje.
4. **Não fazer nada agora, mantendo o estado atual como está** — não é tecnicamente rejeitada; é uma opção válida dado o caráter didático do repositório (ver nota abaixo), mas cabe ao time humano decidir, não a esta ADR.

## Consequências

**Positivas**: bundle inicial reduzido e code-splitting real por rota (itens 1-2); leitura/manutenção mais simples com control flow moderno (item 3); pipeline HTTP centralizado habilitando interceptor de erro/retry/auth futura, eliminando os 14 pontos de `try/catch` manual e o cache nunca invalidado do `FrotasService` (item 6); eliminação de lógica de filtro duplicada em 3 componentes via `computed()` (item 7); formulários com validação real em vez de `alert()`/`confirm()` nativos (item 9).

**Negativas / riscos**:
- O item 8 (`OnPush`) depende estritamente do item 7 (signals) já estar concluído — não são intercambiáveis; aplicar `OnPush` isoladamente hoje arrisca UI que não atualiza após `fetch()` resolver.
- `libs/shared-types`/`libs/api-contracts` **não estão prontas para uso** (item 11): além de não terem `paths` configurado em `tsconfig.base.json`, os campos divergem do backend real (`FE-INC-07`) e as libs nem são projetos Nx válidos (`FE-ARQ-14`) — qualquer adoção precisa vir depois de reconciliar contratos entre os 3 repositórios de API e o frontend.
- `libs/ui-components` (que teria `StatusBadge`/`DataTable` prontos para a duplicação de badges de status hoje repetida em CSS em quase todos os componentes) é React/TSX (`FE-INC-13`) — não pode ser importada diretamente em Angular; o item 10 precisaria criar equivalentes Angular do zero, não reaproveitar a lib existente.
- **Não existe rede de segurança automatizada hoje** para validar nenhum item deste roadmap: o único spec do app está quebrado (`FE-INF-02`) e, mais grave, `nx test painel-admin` falha (`TS5095`) e `nx lint painel-admin` crasha (`FE-INF-01`, `FE-INF-04`, ambos reproduzidos em `DIVIDAS-TECNICAS.md`). Corrigir esses dois bloqueios de infraestrutura está fora do escopo desta ADR (é sobre `painel-admin` especificamente, não sobre o tsconfig do monorepo/regra de lint), mas é pré-requisito prático para executar qualquer item deste roadmap com confiança.
- Extrair templates/estilos para arquivos externos (parte do item 10) toca componentes que já estão perto do budget de CSS configurado (`FE-INC-06`) — mudança recomendada, mas deve ser validada contra o budget de `project.json` (`anyComponentStyle`: `maximumWarning: 2kb`/`maximumError: 4kb`).

**Nota sobre o caráter didático do repositório**: `veiculos.component.ts` (`FE-ARQ-01`) e `dashboard.component.ts` (`FE-ARQ-02`) são os exemplos mais citados de God Object no `DIVIDAS-TECNICAS.md` deste repositório, e o uso de `fetch()` em vez de `HttpClient` é mencionado explicitamente no `CLAUDE.md`/frontmatter do agente `angular-painel-admin` como dívida técnica intencional. Resolver os itens 6, 7, 8 e 10 deste roadmap remove esse material de aula. Recomenda-se, antes de executar qualquer item desta ADR, decidir explicitamente se (a) o estado atual deve ser preservado em uma tag/branch para uso futuro no curso, e/ou (b) outro exemplo de débito equivalente deve ser introduzido em substituição — mesma recomendação já registrada nas ADRs irmãs deste workspace.

## Referências

- `DIVIDAS-TECNICAS.md`, seção 5 (rotalog-frontend) — em especial `FE-ARQ-01`, `FE-ARQ-02`, `FE-ARQ-04`, `FE-ARQ-05`, `FE-ARQ-06`, `FE-ARQ-07`, `FE-ARQ-08`, `FE-ARQ-14`, `FE-INC-03`, `FE-INC-06`, `FE-INC-07`, `FE-INC-10`, `FE-INC-13`, `FE-SEC-06`, `FE-SEC-09`, `FE-INF-01`, `FE-INF-02`, `FE-INF-04`.
- `apps/painel-admin/src/app/{app.routes.ts, app.config.ts, app.component.ts, app.component.html, app.component.spec.ts}`.
- `apps/painel-admin/src/app/services/{frotas.service.ts, entregas.service.ts}`.
- `apps/painel-admin/src/app/components/{dashboard,entregas,layout,manutencoes,motoristas,veiculos}/*.component.ts` (6 componentes, lidos integralmente para esta análise).
- `apps/painel-admin/src/app/models/index.ts`.
- `libs/shared-types/src/index.ts`, `libs/ui-components/src/{index.ts,StatusBadge.tsx}`.
- `apps/painel-admin/project.json`, `apps/painel-admin/eslint.config.js`, `tsconfig.base.json`, `rotalog-frontend/package.json`.
- Bundle reproduzido: `dist/apps/painel-admin/browser/main-OESVX37W.js` (328.125 bytes, arquivo único, sem chunks lazy).
- `rotalog-frontend/docs/adr/0001-modernizacao-app-rastreamento.md` — ADR irmã, mesmo formato, mesmo dia.
- `.claude/agents/angular-painel-admin.md` — convenções de stack (Angular 18, standalone, `fetch()` direto sem HttpClient/interceptors) usadas como ponto de partida, confirmadas por leitura direta do código nesta análise.
