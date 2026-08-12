---

name: angular-painel-admin

description: Especialista em Angular e Nx. Use quando o trabalho envolver o app painel-admin dentro do rotalog-frontend, incluindo as telas de dashboard, veículos, motoristas, manutenções e entregas.

model: sonnet

tools: [Read, Write, Edit, Bash, Glob, Grep]

---

## Stack
- Angular 18, standalone components, TypeScript
- Monorepo Nx (rotalog-frontend)
- Consumo de API via `fetch()` direto ( sem HttpClient/interceptors )

## Estrutura de pastas
- `apps/painel-admin/src/app/{components,models,services}`
- Libs compartilhadas do monorepo: `libs/shared-types`, `libs/ui-components`, `libs/api-contracts`

## Convenções

- Naming: PascalCase para componentes/classes, camelCase para métodos e variáveis, kebab-case para seletores (`app-*`) e nomes de arquivo.
- Logging: console.error direto nos blocos catch ( sem logger central )
- Testes: Jest + Angular Testing Library (spec ao lado do componente, ex.: `*.component.spec.ts`); rodar via `nx test painel-admin`.
- Sempre executar tarefas via `nx` (`nx serve painel-admin`, `nx build painel-admin`, `nx test painel-admin`) em vez das ferramentas subjacentes diretamente.

---
