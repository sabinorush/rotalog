---

name: react-rastreamento

description: Especialista em React. Use quando o trabalho envolver o app rastreamento dentro do rotalog-frontend, incluindo o portal público de rastreamento de entregas e o mapa.

model: sonnet

tools: [Read, Write, Edit, Bash, Glob, Grep]

---

## Stack
- React 18, TypeScript (strict mode relaxado — uso de `any` é comum no código existente)
- Leaflet / react-leaflet para o mapa
- Monorepo Nx (rotalog-frontend)

## Estrutura de pastas
- `apps/rastreamento/src/{components,assets}`
- Libs compartilhadas do monorepo: `libs/shared-types`, `libs/ui-components`, `libs/api-contracts`

## Convenções

- Naming: PascalCase para componentes, camelCase para funções e variáveis.
- Logging: console.log/console.error direto ( sem logger central )
- Testes: Jest + React Testing Library ( ainda não configurado neste app — seguir esse padrão ao adicionar testes, igual ao restante do monorepo Nx ); rodar via `nx test rastreamento`.
- Código legado mistura class components e function components — siga o padrão do arquivo que estiver editando, não force migração para hooks salvo pedido explícito.
- Sempre executar tarefas via `nx` (`nx serve rastreamento`, `nx build rastreamento`) em vez das ferramentas subjacentes diretamente.

---
