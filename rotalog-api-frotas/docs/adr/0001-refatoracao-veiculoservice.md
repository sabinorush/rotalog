# ADR-0001: Refatoração de `VeiculoService` — quebra de responsabilidades, exceções de domínio e cobertura de teste

## Status

**Proposto** — 2026-08-12. Documento apenas de análise/decisão; nenhuma alteração de código foi feita. Execução depende de aprovação e priorização posteriores.

## Contexto

`VeiculoService` (`rotalog-api-frotas/src/main/java/com/rotalog/service/VeiculoService.java`) é a service mais citada em `DIVIDAS-TECNICAS.md` (`FR-ARQ-01`, item 1 do "Top 10 críticos" de arquitetura do repositório): 339 linhas, 14 métodos públicos (a auditoria original registra 13; a contagem aqui inclui `sincronizarComSistemaExterno`, ver seção 9), concentrando CRUD de veículo, validação de entrada, orquestração de notificação HTTP síncrona, regra de manutenção preventiva e cálculo financeiro.

O time revisou a classe e levantou 8 pontos de melhoria. Esta ADR:

1. Confronta cada ponto com o código real (`arquivo:linha`) e com o mapeamento já existente em `DIVIDAS-TECNICAS.md`;
2. Lista problemas adicionais encontrados durante a análise que não estavam nos 8 pontos originais;
3. Propõe uma decisão de arquitetura alvo, sem implementá-la.

Importante: este é o repositório didático do curso Alura, com dívida técnica **intencional** (`CLAUDE.md`). `VeiculoService` é o exemplo canônico de God Class usado no curso — a seção 10 trata explicitamente essa tensão.

## Diagnóstico — os 8 pontos levantados

### 1. Classe muito grande (>300 linhas) — dividir responsabilidades

🟠 Alto — já mapeado como `FR-ARQ-01`.

339 linhas / 14 métodos públicos misturando 5 responsabilidades distintas:

| Responsabilidade | Métodos |
|---|---|
| CRUD / consulta de veículo | `listarTodos`, `buscarPorId`, `buscarPorPlaca`, `registrarVeiculo`, `atualizarVeiculo`, `obterVeiculosPorStatus`, `desativarVeiculo`, `reativarVeiculo` |
| Orquestração de notificação HTTP | chamadas inline a `notificacaoClient.enviarNotificacao(...)` em `registrarVeiculo:108-112`, `atualizarQuilometragem:174-179`, `agendarManutencaoPreventiva:219-224`, `desativarVeiculo:273-278` — 4 blocos try/catch quase idênticos |
| Regra de manutenção preventiva | `agendarManutencaoPreventiva:208-228`, `precisaDeManutencao:249-256` |
| Cálculo financeiro | `calcularCustoManutencao:236-242` |
| Estatísticas de frota | `obterEstatisticasFreita:304-316` |

Ponto de atenção: `ManutencaoService` já acessa `VeiculoRepository` diretamente e duplica a checagem de "veículo não encontrado" (`FR-ARQ-05`). Qualquer divisão de `VeiculoService` que ignore essa sobreposição corre o risco de criar uma **terceira** cópia da mesma regra em vez de consolidar.

### 2. Exceções genéricas — criar exceções específicas

🟡 Médio — parte de `FR-ARQ-09` (sem exceções de domínio, sem `@ControllerAdvice`).

Todo erro de negócio na classe é um `new RuntimeException(...)` com mensagem construída na hora: linhas 49, 57, 70, 74, 80, 85, 90, 160, 196. Cada `catch` no controller devolve `e.getMessage()` direto ao cliente (`FR-SEC-07`, fora do escopo desta service mas alimentado por ela). Não há como o controller diferenciar "não encontrado" (404) de "validação inválida" (400) sem fazer *string matching* na mensagem.

### 3. Cobertura de teste ≥90% (teste unitário)

🟠 Alto — `FR-INF-01`: não existe diretório `src/test` no repositório; `spring-boot-starter-test` está declarado no `pom.xml` e nunca foi usado.

Pré-requisito técnico a resolver antes de perseguir 90%: os dois colaboradores (`veiculoRepository`, `notificacaoClient`) são injetados por campo com `@Autowired` (linhas 27-31, `FR-ARQ-10`, com `FIXME` já no código). Isso não impede Mockito (`@InjectMocks` funciona via reflection), mas migrar para injeção por construtor tornaria os testes mais diretos e é normalmente pré-requisito para extrair as classes do ponto 1/7.

### 4. Remover javadoc em excesso

⚪ Baixo (cosmético, mas gera ruído) — relacionado a `FR-INC-15` (mistura PT/EN no arquivo).

Praticamente todo método tem um bloco `/** ... */` que apenas repete o nome do método em português, seguido de `FIXME`s que **duplicam** os `FIXME` já presentes como comentário de linha dentro do corpo do método (ex.: `atualizarQuilometragem`, javadoc linhas 150-155 vs. comentário inline linha 153). O javadoc de classe (linhas 13-22) tem tom de nota de curso ("Legacy service... Intentional technical debt for the course") misturado com `TODO`s reais — não deveria estar em javadoc de produção.

### 5. Remover logs com `System.out.println`

🟡 Médio — `FR-INC-11`, ocorrências confirmadas em linhas **116**, **164** e **327**, convivendo com SLF4J (`@Slf4j`, `log.info/warn/error`) na mesma classe e até no mesmo método (linha 326 já usa `log.info` logo antes da linha 327 usar `System.out.println` para dizer basicamente a mesma coisa).

### 6. Criar enums para status (evitar strings soltas)

🟡 Médio — parte de `FR-ARQ-09`; relacionado a `FR-INC-08` (typo de status não detectado em outro controller).

`"ATIVO"`/`"INATIVO"`/`"MANUTENCAO"` aparecem como string literal em 6+ pontos (linhas 98, 195, 199 [via `findByStatus`], 266, 291, 307-309). `VeiculoRepository.findByStatus(String)` também recebe string crua.

**Atenção antes de decidir a implementação**: `rotalog-api-entregas` reimplementa localmente a mesma checagem `status !== 'ATIVO'` como string (`FR-EN-ARQ-05`, `frotasService.js:94-107`), consumindo esse valor pela API HTTP. Se o enum vazar para o JSON de resposta com serialização diferente da string atual, quebra esse consumidor silenciosamente. Recomenda-se enum apenas internamente (domínio/JPA via `@Enumerated(STRING)`), preservando a representação String no contrato HTTP.

### 7. Criar classes auxiliares / outros services para dividir responsabilidade

🟠 Alto — mesma raiz do ponto 1; tratados juntos na seção de Decisão abaixo.

### 8. Remover variáveis não utilizadas

⚪ Baixo isoladamente, mas expõe um problema funcional maior.

Achado concreto: em `agendarManutencaoPreventiva` (linhas 208-228), as variáveis `intervaloQuilometragem` (linha 212, `10000L`) e `intervaloMeses` (linha 213, `3`) são declaradas e **nunca lidas** — nem no `log.info` (linhas 215-216, que usa apenas `quilometragemLimite`, parâmetro do método), nem na notificação (linhas 219-224). O método não persiste nenhum registro de manutenção nem calcula uma data/quilometragem-limite real; apenas loga e dispara notificação. Ou seja, o "agendamento" hoje não agenda nada — as duas variáveis mortas são um sintoma de uma lógica que foi esboçada e nunca terminada, não apenas um lint warning.

## Problemas adicionais encontrados (fora dos 8 pontos originais)

| # | Local | Problema | Ref. |
|---|---|---|---|
| A | `registrarVeiculo:78` vs. `:95` | Checagem de placa duplicada usa a string crua (`findByPlaca(placa)`); a persistência salva em maiúsculas (`placa.toUpperCase()`) | `FR-INC-02` 🟠 |
| B | `atualizarVeiculo:138-142` vs. `atualizarQuilometragem:159-161` | Duas regras diferentes para regressão de quilometragem: uma só loga um aviso, a outra lança `RuntimeException` — mesma invariante de negócio, dois comportamentos | `FR-INC-03` 🟠 |
| C | `atualizarVeiculo:138`, `atualizarQuilometragem:163` | Unboxing de `Long quilometragem` sem checar `null` — `NullPointerException` genérica se a coluna vier `null` (não é `NOT NULL` no schema) | `FR-INC-04` 🟡 |
| D | Linhas 110, 177, 222, 276 | E-mail de destino (`"gestor@rotalog.com"`) e canal (`"email"`) hardcoded em 4 pontos do código de negócio | `FR-SEC-10` ⚪ |
| E | `NotificacaoClient` usado em 4 métodos (linhas 108, 175, 220, 274) | `RestTemplate` sem timeout de conexão/leitura no caminho de escrita — cada cadastro/atualização pode prender uma thread indefinidamente se `api-notificacoes` não responder | `FR-ARQ-03` 🔴 (crítico no repo, mas fora do escopo de refatoração desta classe — mitigação fica em `NotificacaoClient`) |
| F | `agendarManutencaoPreventiva` (linha 208), `sincronizarComSistemaExterno` (linha 324) | Nenhum controller chama esses dois métodos públicos (confirmado por busca no `src/`) — código morto ou funcionalidade nunca exposta via API | novo achado, não catalogado em `DIVIDAS-TECNICAS.md` |
| G | `obterEstatisticasFreita:304-316` | Typo histórico no nome (`Freita`), `String` JSON montada manualmente em vez de DTO, `.size()` sobre lista completa em vez de `count` query | `FR-INC-01`, parte de `FR-ARQ-09` |

## Decisão proposta (arquitetura alvo — não implementada)

Caso aprovado, o alvo para os pontos 1 e 7 é decompor `VeiculoService` em:

- **`VeiculoService`** (permanece): CRUD/consulta puro — `listarTodos`, `buscarPorId`, `buscarPorPlaca`, `registrarVeiculo`, `atualizarVeiculo`, `obterVeiculosPorStatus`, `desativarVeiculo`, `reativarVeiculo`.
- **Política de manutenção**: `calcularCustoManutencao`, `precisaDeManutencao`, `agendarManutencaoPreventiva` — candidatos a mover para dentro de `ManutencaoService` (que já é o dono do domínio de manutenção) em vez de criar uma terceira classe, resolvendo a duplicação com `FR-ARQ-05` no mesmo movimento.
- **Adapter de notificação**: encapsular os 4 blocos `try { notificacaoClient... } catch { log... }` hoje duplicados dentro de `VeiculoService` em um único ponto (ex.: um método `notificarComFallback(tipo, mensagem)` ou uma classe dedicada), incluindo o tratamento dos hardcodes do achado D.
- **Estatísticas**: substituir `obterEstatisticasFreita` por um DTO real (`FrotaEstatisticasDTO`) e queries `count` no repositório em vez de `.size()` sobre listas completas; corrigir o typo como parte da mesma mudança (é breaking change de nome de método/endpoint — avaliar deprecação em vez de remoção direta, já que é consumido por `VeiculoController.java:195`).
- **Exceções de domínio** (ponto 2): hierarquia sob uma exceção base (ex.: `VeiculoNaoEncontradoException`, `PlacaInvalidaException`, `PlacaDuplicadaException`, `ModeloInvalidoException`, `AnoFabricacaoInvalidoException`, `QuilometragemInvalidaException`, `StatusInvalidoException`), preparando terreno para um futuro `@ControllerAdvice` (mudança de controller, fora do escopo desta ADR).
- **Enum `StatusVeiculo`** (ponto 6): uso interno/JPA, mantendo serialização String no contrato HTTP para não quebrar `rotalog-api-entregas`.
- **Testes** (ponto 3): escritos por último, depois da divisão — testar a classe monolítica hoje significaria re-testar tudo de novo após a quebra.

## Alternativas consideradas

1. **Extrair um único "Helper" genérico** para notificação + manutenção + estatísticas — rejeitada: apenas move o problema de uma classe grande para outra, sem separar responsabilidades reais.
2. **Reescrever a classe do zero** — rejeitada: sem rede de testes hoje (`FR-INF-01`), reescrever é o cenário de maior risco de regressão silenciosa (ex.: destino dos achados B e C).
3. **Testar antes de dividir** (escrever testes de caracterização sobre o comportamento atual, só então refatorar) — não é uma alternativa excludente, é a ordem recomendada de execução caso este ADR seja aprovado.

## Consequências

**Positivas**: classe testável isoladamente; erros diferenciáveis por tipo (404 vs. 400 vs. 422) no controller; menor acoplamento entre cadastro de veículo e regra de manutenção; elimina 2 variáveis mortas e 3 `System.out.println`.

**Negativas / riscos**:
- Mover `calcularCustoManutencao`/`precisaDeManutencao`/`agendarManutencaoPreventiva` para `ManutencaoService` muda o dono do domínio — qualquer código hoje acoplado a `VeiculoService` para essas chamadas (nenhum controller encontrado, achado F) precisa ser confirmado como realmente inexistente antes de mover.
- Introduzir enum de status é uma mudança que toca `Veiculo` (entidade JPA), `VeiculoRepository` e potencialmente a migration Flyway — maior que o escopo de "só a service".
- Corrigir o typo `obterEstatisticasFreita` é breaking change de nome de método público; se algum outro consumidor além do controller local existir (não identificado nesta análise), quebra silenciosamente.

**Nota sobre o caráter didático do repositório**: `VeiculoService`/`FR-ARQ-01` é o primeiro item do "Top 10 críticos" de `DIVIDAS-TECNICAS.md` e provavelmente o exemplo mais usado no curso para ensinar God Class. Resolver esse débito remove esse material de aula. Recomenda-se, antes de executar qualquer item deste ADR, decidir explicitamente se (a) o estado atual deve ser preservado em uma tag/branch para uso futuro no curso, e/ou (b) outro exemplo de débito equivalente deve ser introduzido em substituição, para não esvaziar o repositório de exercícios.

## Referências

- `DIVIDAS-TECNICAS.md`, seção 2 (rotalog-api-frotas) — em especial `FR-ARQ-01`, `FR-ARQ-03`, `FR-ARQ-05`, `FR-ARQ-09`, `FR-ARQ-10`, `FR-INC-01`, `FR-INC-02`, `FR-INC-03`, `FR-INC-04`, `FR-INC-08`, `FR-INC-11`, `FR-INC-15`, `FR-SEC-07`, `FR-SEC-10`, `FR-INF-01`.
- `rotalog-api-frotas/src/main/java/com/rotalog/service/VeiculoService.java` (339 linhas, lido integralmente para esta análise).
- `.claude/agents/java-frotas.md` — convenções de stack (Java 11/Spring Boot 2.7, SLF4J, JUnit5+Mockito) usadas como referência para a arquitetura alvo proposta.
