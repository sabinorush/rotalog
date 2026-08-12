# ADR-0001: Modernização do app `rastreamento` — React 19, class components → hooks e PropTypes → TypeScript estrito

## Status

**Proposto** — 2026-08-12. Documento apenas de análise/decisão; nenhuma alteração de código foi feita. Execução depende de aprovação e priorização posteriores.

## Contexto

`apps/rastreamento` (React 18, portal público de rastreamento de entregas) é um app pequeno dentro do monorepo Nx `rotalog-frontend`: 5 arquivos-fonte, 744 linhas (`App.tsx` 130, `main.tsx` 18, `components/DeliveryList.tsx` 105, `components/MapView.tsx` 324, `components/TrackingDashboard.tsx` 167). Não há testes (`.spec.tsx`), e nenhuma lib compartilhada do monorepo (`shared-types`, `ui-components`, `api-contracts`) é importada por este app (confirmado por grep sem resultados).

O time levantou 4 questões de modernização:

1. Impacto de atualizar de React 18 para a versão mais atual (React 19);
2. Class components vs. function components com hooks;
3. PropTypes vs. TypeScript — riscos;
4. Priorização por esforço x benefício.

Esta ADR confronta cada ponto com o código real (`arquivo:linha`), cruza com o mapeamento já existente em `DIVIDAS-TECNICAS.md` (seção 5, rotalog-frontend) e propõe uma ordem de execução, sem implementar nada.

Importante: este é o repositório didático do curso Alura, com dívida técnica **intencional** (`CLAUDE.md`). Os 4 class components e o uso extensivo de `any` são material de curso — a seção "Nota sobre caráter didático" trata explicitamente essa tensão.

## Diagnóstico

### 1. React 18 → 19

🟢 Baixo risco técnico, mas há um bloqueio de peer-dependency.

Versões atuais (`rotalog-frontend/package.json`): `react`/`react-dom` **18.3.1**, `@types/react` **18.3.28**, `typescript` **5.5.4**, `react-leaflet` **4.2.1**, Nx **19.8.4**. O app não tem `package.json` próprio.

O código não usa nenhuma API removida em React 19: `main.tsx:10-12` já usa `createRoot`; zero `ReactDOM.render`/`hydrate`/`findDOMNode`; zero string refs; zero lifecycles `UNSAFE_*`/legados; zero legacy Context API (todos grep confirmados, zero ocorrências em `src/`). Class components continuam 100% suportados no React 19 — o upgrade em si é ortogonal à migração para hooks.

**Bloqueio real**: `react-leaflet@4.2.1` declara peer-dependency `react: ^18.0.0` / `react-dom: ^18.0.0` (lido de `node_modules/react-leaflet/package.json`), o que formalmente quebraria/geraria conflito num upgrade. Porém `react-leaflet` **não é importado em nenhum lugar do código** — `MapView.tsx:4-8,53` carrega Leaflet via `<script>` de CDN por um problema conhecido de webpack/Nx documentado no próprio comentário do autor. É dependência morta que só existe no `package.json`.

**Efeito colateral**: `propTypes` estático (`(Componente as any).propTypes = {...}`, presente nos 4 componentes) vira no-op silencioso no React 19, já que a checagem em runtime feita pelo `createElement` foi removida — não quebra build, mas as 3 declarações não-vazias (`DeliveryList.tsx:98-103`, `MapView.tsx:318-322`, `TrackingDashboard.tsx:163-165`) perdem efeito sem nenhum aviso.

**Detalhe estrutural relevante para qualquer upgrade**: o build usa Babel (`.babelrc`, `@babel/preset-typescript`), que apaga anotações de tipo sem checá-las, e `apps/rastreamento/project.json` não tem target `typecheck`. Ou seja, hoje nenhum erro de tipo bloqueia `nx build`/`nx serve` — incompatibilidades entre `@types/react@18` e uma futura `@types/react@19` não seriam pegas pelo pipeline atual.

### 2. Class components vs. function components com hooks

🟡 Médio — ganho real varia por componente, não é uniforme.

Grep (`extends React.Component` / `extends Component` em `apps/rastreamento/src`) confirma **4 de 4 componentes são class components** — 100%, não os "70%" que o comentário em `App.tsx:7` sugere (`FE-INC-01` do `DIVIDAS-TECNICAS.md` já registra essa divergência). Zero function components dentro do app (as únicas existem em `libs/ui-components/`, não importadas aqui).

| Componente | Estado/lifecycle usado | Ganho técnico concreto de migrar |
|---|---|---|
| `DeliveryList.tsx` | `state.filteredDeliveries` inicializado no constructor a partir de `props.deliveries`, ressincronizado em `componentDidUpdate` via comparação de referência (`prevProps.deliveries !== this.props.deliveries`, linha 23) | **Ganho real, não estético**: `filteredDeliveries` é 100% derivável de `props.deliveries` + `sortBy`. Um `useMemo` eliminaria a classe de bug em que o componente trava desatualizado se o pai um dia mutar o array no lugar (`FE-ARQ-11`) |
| `MapView.tsx` | `mapInstance`/`markersLayer`/`routeLine` como campos de instância (nunca em `state`), `componentDidMount`/`componentDidUpdate`/`componentWillUnmount` | Candidato mais natural: os 3 campos mapeiam 1:1 para `useRef`; mount+unmount viram um único `useEffect` com cleanup. Maior risco de regressão dos 4, por concentrar o ciclo de vida do Leaflet e o ponto de XSS já mapeado (`FE-SEC-01`) |
| `TrackingDashboard.tsx` | `setInterval` guardado em `state.refreshInterval` (anti-padrão: ID de interval não deveria disparar re-render); `refreshData` lê `this.props.delivery` ao vivo a cada execução | **Atenção**: migração ingênua para `useEffect(() => setInterval(...), [])` introduziria stale closure que **não existe hoje** (o método de classe sempre lê a prop atual). Agravante: **`eslint-plugin-react-hooks` não está instalado** no repo — nada pegaria esse erro automaticamente |
| `App.tsx` | `state` monolítico com campo morto `reduxState: null` (resquício de Redux que nunca existiu, comentário próprio linha 24) e `filters` atualizado mas nunca lido no `render` (linha 79) | Migrar exigiria primeiro decidir o que fazer com o estado morto — o ganho de hooks aqui é secundário ao problema arquitetural (prop drilling para os 3 filhos) |

### 3. PropTypes vs. TypeScript — riscos

🔴 Alto — risco concreto já observável no núcleo funcional do app (mapa).

- `prop-types` (`^15.8.1`) está de fato importado e usado nos 4 arquivos, mas em `App.tsx:126-128` o objeto de `propTypes` está **vazio** e o import (`App.tsx:2`) nem é usado (`nx lint` confirma `'PropTypes' is defined but never used`).
- `tsconfig.base.json:14` do monorepo exige `strict: true`, mas `apps/rastreamento/tsconfig.app.json:7-11` **sobrescreve** isso só para este app: `strict: false`, `noImplicitAny: false`, `noImplicitOverride: false`, `noUnusedLocals: false`, `noUnusedParameters: false`.
- Contagem real (`nx lint rastreamento`, reproduzido nesta análise): **40 ocorrências de `@typescript-eslint/no-explicit-any`**, lint falha com 43 erros no total (não é "quase passando").
- **Caso concreto de risco**: `MapView.tsx:112-118`, dentro de `updateMapMarkers` — `delivery` chega como `any` (prop de `React.Component<any, any>`, linha 10); campos como `origem_lat`/`origem_lng` são convertidos via `parseFloat` + `isNaN`. Se o backend (`rotalog-api-entregas`) renomear ou mudar o tipo de um campo, o marker de origem **some do mapa sem nenhum erro, warning ou crash** — o núcleo funcional do app (rastreamento público) falha em silêncio.
- **Segundo caso**: `STATUS_CONFIG: any` está duplicado literalmente entre `DeliveryList.tsx:5-11` e `TrackingDashboard.tsx:6-12` (há um `FIXME` próprio admitindo a duplicação). Por ser `any`, TypeScript não detecta se as duas cópias divergirem ao adicionar um novo status.

## Decisão proposta (ordem de execução — não implementada)

Ordenado por esforço x benefício, maior ROI primeiro:

1. **Adicionar target `typecheck` (`tsc --noEmit`) ao `project.json`** — esforço muito baixo, risco nenhum (só configuração). Pré-requisito de tudo abaixo: hoje o Babel apaga tipos sem checar, então qualquer correção de tipagem não tem efeito prático no pipeline sem isso.
2. **Remover `react-leaflet`/`leaflet`/`@types/leaflet` do `package.json`** (código morto confirmado) — esforço baixo, risco nenhum. Elimina o único bloqueio formal de peer-dependency para o item 8.
3. **Migrar `DeliveryList.tsx` para function component + `useMemo`** — esforço baixo, risco baixo. Único caso com ganho técnico real (corrige bug de sincronização), não apenas estilístico.
4. **Tipar payloads de API** (`Entrega`, eventos de rastreamento) e eliminar os 40 `any` — esforço médio, risco baixo se feito depois do item 1. Ataca diretamente o risco descrito no ponto 3 do diagnóstico.
5. **Migrar `TrackingDashboard.tsx` para hooks** — esforço médio, risco médio-alto (stale closure no polling sem rede de segurança de lint).
6. **Migrar `MapView.tsx` para hooks** — esforço alto, risco alto (maior arquivo, ciclo de vida do Leaflet, ponto de XSS já conhecido `FE-SEC-01`).
7. **Migrar `App.tsx` para hooks**, resolvendo o estado morto (`filters`, `reduxState`) — esforço alto, risco alto (efeito cascata sobre os 3 filhos).
8. **Upgrade React 18 → 19** (depois do item 2) — esforço baixo-médio, risco baixo, mas sem pressão funcional real hoje — nenhuma API em uso foi removida na v19.

## Alternativas consideradas

1. **Fazer o upgrade do React 18→19 antes de tudo, isoladamente** — rejeitada como primeiro passo: tecnicamente possível, mas deixa a peer-dependency morta do `react-leaflet` como bloqueio desnecessário; melhor resolver o item 2 primeiro.
2. **Migrar todos os 4 componentes para hooks de uma vez, num único PR** — rejeitada: mistura o único caso de ganho técnico real (`DeliveryList`) com os de maior risco (`MapView`, `TrackingDashboard`), dificultando isolar regressão caso algo quebre.
3. **Corrigir a tipagem (`any` → interfaces) sem antes ligar um target de `typecheck`** — rejeitada: sem gate de compilação, erros de tipo introduzidos ou corrigidos não têm efeito verificável no build/CI; a ordem 1 → 4 é necessária, não intercambiável.

## Consequências

**Positivas**: pipeline passa a ter um gate real de tipos (item 1); remove uma dependência morta que hoje bloqueia formalmente o upgrade de major do React (item 2); elimina uma classe de bug de sincronização de estado já documentada (item 3); reduz o risco de o mapa de rastreamento público falhar em silêncio por divergência de contrato de API (item 4).

**Negativas / riscos**:
- Ligar `strict`/`noImplicitAny` neste app (mesmo que só via `typecheck`, sem bloquear build) vai expor os 40 pontos de `any` de uma vez — recomenda-se tratar como trabalho incremental, não como PR único.
- Migrar `TrackingDashboard.tsx` e `MapView.tsx` para hooks sem antes instalar `eslint-plugin-react-hooks` tem risco real e específico de regressão silenciosa (stale closures) — instalar o plugin deveria ser pré-requisito dos itens 5 e 6, não follow-up.
- Remover `react-leaflet`/`leaflet` do `package.json` é seguro hoje (código morto confirmado), mas qualquer trabalho futuro que planeje de fato adotar `react-leaflet` (em vez do CDN) precisaria reverter essa remoção — vale confirmar que não há essa intenção antes de executar o item 2.

**Nota sobre o caráter didático do repositório**: os 4 class components e o uso extensivo de `any`/`PropTypes` são exemplos usados no curso para contrastar com hooks/TypeScript estrito. Resolver esses pontos remove esse material de aula. Recomenda-se, antes de executar qualquer item desta ADR, decidir explicitamente se (a) o estado atual deve ser preservado em uma tag/branch para uso futuro no curso, e/ou (b) outro exemplo de débito equivalente deve ser introduzido em substituição.

## Referências

- `DIVIDAS-TECNICAS.md`, seção 5 (rotalog-frontend) — em especial `FE-ARQ-10`, `FE-ARQ-11`, `FE-DEP-06`, `FE-INC-01`, `FE-INC-02`, `FE-INC-15`, `FE-INF-03`, `FE-INF-06`, `FE-SEC-01`, `FE-SEC-05`.
- `rotalog-frontend/apps/rastreamento/src/{App.tsx, main.tsx, components/DeliveryList.tsx, components/MapView.tsx, components/TrackingDashboard.tsx}` (744 linhas, lidos integralmente para esta análise).
- `rotalog-frontend/package.json`, `tsconfig.base.json`, `tsconfig.json`, `nx.json`, `apps/rastreamento/project.json`, `apps/rastreamento/tsconfig.app.json`, `apps/rastreamento/.babelrc`.
- Saída de `nx lint rastreamento` reproduzida durante esta análise (43 erros, 40 dos quais `@typescript-eslint/no-explicit-any`).
- `.claude/agents/react-rastreamento.md` — convenções de stack (React 18, TS strict relaxado, Jest+RTL ainda não configurado) usadas como referência para a arquitetura alvo proposta.
