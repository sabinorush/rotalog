# Mapa de Dívidas Técnicas — RotaLog

> Auditoria do código real dos 5 repositórios do workspace RotaLog (não dos READMEs). O RotaLog é um projeto didático da Alura com dívida técnica **intencional** — o objetivo deste documento não é "consertar", mas mapear com precisão (`arquivo:linha`) o que existe, para orientar as sessões de refatoração do curso.
>
> Legenda de severidade: 🔴 Crítico · 🟠 Alto · 🟡 Médio · ⚪ Baixo — atribuída por impacto real (exploração remota sem auth, corrupção/perda de dado, indisponibilidade), não por esforço de correção.

## Sumário executivo

| Repositório | Arquitetura | Inconsistência | Segurança | Dependências | Infraestrutura | Total |
|---|---:|---:|---:|---:|---:|---:|
| [1. rotalog-workspace](#1-rotalog-workspace) | 4 | 2 | 3 | 2 | 5 | 16 |
| [2. rotalog-api-frotas](#2-rotalog-api-frotas) | 11 | 17 | 10 | 9 | 10 | 57 |
| [3. rotalog-api-entregas](#3-rotalog-api-entregas) | 10 | 12 | 12 | 6 | 11 | 51 |
| [4. rotalog-api-notificacoes](#4-rotalog-api-notificacoes) | 7 | 8 | 11 | 6 | 9 | 41 |
| [5. rotalog-frontend](#5-rotalog-frontend) | 14 | 17 | 10 | 10 | 17 | 68 |
| **Total** | **46** | **56** | **46** | **33** | **52** | **233** |

## Top 10 críticos (cross-repo)

| # | Achado | Onde |
|---|---|---|
| 1 | Bypass total de autenticação fora de `production` — `.env` versionado já define `development` | [api-entregas → Segurança](#entregas-sec) `EN-SEC-01` |
| 2 | O middleware "JWT" não verifica assinatura nem expiração; qualquer string ≥10 chars vira `role: admin` | [api-entregas → Segurança](#entregas-sec) `EN-SEC-02` |
| 3 | Credenciais de banco/SMTP/SMS versionadas em texto puro em 3 repositórios | [frotas](#frotas-sec) `FR-SEC-02` · [entregas](#entregas-sec) `EN-SEC-03` · [notificações](#notif-sec) `NT-SEC-01,02,03` |
| 4 | `api-notificacoes` é um open relay anônimo — qualquer um envia e-mail/SMS com as credenciais da empresa | [notificações → Segurança](#notif-sec) `NT-SEC-05` |
| 5 | Zero autenticação/autorização em 100% dos endpoints das 3 APIs e do backoffice Angular | [frotas](#frotas-sec) `FR-SEC-01` · [entregas](#entregas-sec) `EN-SEC-01` · [notificações](#notif-sec) `NT-SEC-05` · [frontend](#frontend-sec) `FE-SEC-04` |
| 6 | PII (CNH, endereços, nomes) exposta sem autenticação — inclusive no portal público | [frotas → Segurança](#frotas-sec) `FR-SEC-03` · [frontend → Segurança](#frontend-sec) `FE-SEC-05` |
| 7 | XSS armazenado cross-app: endereço malicioso cadastrado no admin executa no portal público via `bindPopup` | [frontend → Segurança](#frontend-sec) `FE-SEC-01` |
| 8 | Chamadas HTTP síncronas sem timeout no caminho de escrita — ~200 requests pendurados derrubam a API | [frotas → Arquitetura](#frotas-arq) `FR-ARQ-03` |
| 9 | Zero testes executáveis nos 5 repositórios — em `frontend`, `nx test` e `nx lint` estão literalmente quebrados | [frotas](#frotas-inf) `FR-INF-01` · [entregas](#entregas-inf) `EN-INF-01` · [notificações](#notif-inf) `NT-INF-01` · [frontend](#frontend-inf) `FE-INF-01,04,05` |
| 10 | Health checks decorativos retornam `UP` sem checar o banco — orquestrador mantém instância quebrada recebendo tráfego | [frotas → Infra](#frotas-inf) `FR-INF-04` · [entregas → Infra](#entregas-inf) `EN-INF-02` · [notificações → Segurança](#notif-sec) `NT-SEC-11` |

## Correções à documentação do projeto

O `rotalog-workspace/CLAUDE_CONTEXT.md` descreve a dívida técnica esperada de cada serviço. Ao ler o código real, alguns pontos **não conferem** e vale registrar antes de usar aquele documento como referência:

- **"MediatR handlers gigantes (God classes)"** (api-notificacoes) — falso. `grep` por `IRequest|IRequestHandler|INotificationHandler` em todo o repo retorna **zero** ocorrências. MediatR está registrado em `Program.cs:38` e nunca é usado. A god class real é `Services/NotificacaoService.cs` (369 linhas) — um service comum, não um handler.
- **"Clean Architecture iniciada e abandonada no meio"** (api-notificacoes) — não existe estrutura `Application/Domain/Infrastructure`; o projeto é MVC clássico (`Controllers/DTOs/Data/Models/Services`). A "Clean Architecture abandonada" se resume a dois pacotes MediatR mortos no `.csproj`.
- **"React: 70% class components"** (frontend) — na prática é **100%**: os 4 componentes de `apps/rastreamento/src/` (`App.tsx`, `MapView.tsx`, `TrackingDashboard.tsx`, `DeliveryList.tsx`) são classes. Zero function components/hooks no app. Os únicos function components do repo (`libs/ui-components/`) não são importados por nenhum app.
- **"Redux sem Toolkit"** (frontend) — Redux **não existe** no projeto. `redux`/`react-redux`/`@reduxjs/toolkit` não estão no `package.json` nem em `node_modules`. Há 8 comentários TODO mencionando Redux e um campo morto `reduxState: null` em `App.tsx:25`. O gerenciamento de estado real é prop drilling manual.
- **"api-entregas: 60% callbacks"** — o próprio comentário em `src/routes/entregas.js:4` está desatualizado. A contagem real no repositório inteiro é **69%** de funções em estilo callback/`.then()` (18 de 26); naquele arquivo específico é 33%.
- Os repositórios documentados apontam para `github.com/Charlinho/rotalog-*`, mas os clones deste workspace vieram de `github.com/alura-cursos/rotalog-*` — nomes de organização divergentes entre a documentação e a origem real.

---

## 1. rotalog-workspace

*Infraestrutura: Docker Compose + PostgreSQL 14, schemas `frotas`/`entregas`/`notificacoes`.*

<a id="workspace-arq"></a>
### Problemas de arquitetura

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| WS-ARQ-01 | 🟡 | `tools/scripts/init-schemas.sql:13-14` | `api-notificacoes` acessa o schema `frotas` diretamente (violação de bounded context), documentada como intencional | Qualquer mudança de schema em `frotas` quebra silenciosamente o `HealthController` de notificações — acoplamento entre serviços que deveriam ser independentes |
| WS-ARQ-02 | 🟡 | `tools/scripts/02,03-migration-*.sql` | Sem FK entre `entregas.entregas` e as tabelas de `frotas` (placa/motorista são strings soltas) | Uma entrega pode referenciar um veículo/motorista inexistente sem qualquer erro de integridade |
| WS-ARQ-03 | ⚪ | `tools/scripts/08-add-motorista-veiculo-info.sql:22-37` | Backfill de `motorista_nome`/`veiculo_modelo` feito com `UPDATE` hardcoded por `motorista_id`/`placa` específicos do seed | Reexecutar os scripts contra um banco com dados diferentes do seed original deixa as colunas denormalizadas `NULL` silenciosamente |
| WS-ARQ-04 | 🟡 | `docker-compose.yml:29-33` | Redis, Kafka, Elasticsearch, Prometheus e Grafana aparecem comentados como "planejados" e nunca foram implementados | Qualquer feature que dependa de cache/fila/observabilidade (mencionadas na lore do curso) não tem base de infraestrutura nenhuma |

<a id="workspace-inc"></a>
### Inconsistência no código

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| WS-INC-01 | ⚪ | `tools/scripts/08-add-motorista-veiculo-info.sql` | Script numerado fora da sequência 01-07, com comentário exigindo execução "depois dos scripts 01-07" em vez de reordenar a numeração | Processo de migração ad-hoc; próximo script "09" pode ser inserido fora de ordem sem ninguém perceber |
| WS-INC-02 | ⚪ | `tools/scripts/init-schemas.sql:13` vs. demais FIXMEs | Alguns comentários marcam explicitamente "dívida intencional para o curso"; outros apenas registram TODO/FIXME sem essa marcação | Ambíguo para quem lê o script se um problema é "o ponto do exercício" ou um bug real a ignorar |

<a id="workspace-sec"></a>
### Vulnerabilidades de segurança

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| WS-SEC-01 | 🔴 | `docker-compose.yml:8-9` | `POSTGRES_PASSWORD: rotalog123` em texto puro, versionado no git | Senha do banco compartilhado pelos 3 microsserviços permanentemente no histórico do repositório |
| WS-SEC-02 | 🟠 | `tools/scripts/init-schemas.sql:17-19` | Um único usuário `rotalog_admin` recebe `GRANT ALL PRIVILEGES` nos 3 schemas — sem roles por serviço | Comprometer qualquer uma das 3 APIs dá acesso de escrita total aos dados dos outros dois serviços |
| WS-SEC-03 | 🟡 | `tools/scripts/init-schemas.sql:21-23` | Sem row-level security e sem tabelas de auditoria (apenas TODO) | Nenhuma alteração de dado é rastreável a um autor — sem trilha para incidentes |

<a id="workspace-dep"></a>
### Estado de dependências

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| WS-DEP-01 | 🟡 | `docker-compose.yml:1` | `version: '3.8'` — atributo marcado obsoleto pelo próprio Docker Compose v5 em runtime (warning confirmado ao subir o stack) | Arquivo já emite aviso de depreciação; versões futuras do Compose podem passar a rejeitar o atributo |
| WS-DEP-02 | ⚪ | `docker-compose.yml:5` | `postgres:14` fixado sem digest e sem estratégia de atualização de minor version documentada | Rebuild em data futura pode puxar um patch de imagem diferente do testado, sem controle |

<a id="workspace-inf"></a>
### Qualidade de infraestrutura

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| WS-INF-01 | 🟠 | `docker-compose.yml:25` (TODO) | Sem health check no container do Postgres | `docker compose up -d` reporta sucesso mesmo que o Postgres ainda esteja inicializando; serviços dependentes que subirem em seguida podem falhar a primeira conexão |
| WS-INF-02 | 🟡 | `docker-compose.yml:26` (TODO) | Sem limites de recursos (CPU/memória) no container | Um serviço com vazamento de conexões pode consumir toda a memória do host sem contenção |
| WS-INF-03 | 🟡 | `docker-compose.yml:27` (TODO) | Sem estratégia de backup do volume `pgdata` | Perda do volume Docker = perda total dos dados, sem plano de recuperação |
| WS-INF-04 | ⚪ | README.md:47-49 | Reset de ambiente depende de comando manual (`docker-compose down -v`) sem script auxiliar | Processo manual sujeito a erro — apagar o volume errado em ambiente compartilhado |
| WS-INF-05 | ⚪ | (ausência de arquivo) | Sem CI/pipeline para validar os scripts SQL de inicialização antes de alterá-los | Uma migration com erro de sintaxe só é descoberta ao subir o container, não em revisão de PR |

---

## 2. rotalog-api-frotas

*Java 11 / Spring Boot 2.7.14 — veículos, motoristas, manutenções.*

<a id="frotas-arq"></a>
### Problemas de arquitetura

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| FR-ARQ-01 | 🟠 | `service/VeiculoService.java:25-339` | God class: 339 linhas / 13 métodos misturando CRUD, validação, notificação HTTP, regra de manutenção, cálculo financeiro e estatísticas | Mudar a regra de precificação exige recompilar e re-testar o núcleo de cadastro de veículos |
| FR-ARQ-02 | 🟡 | `domain/Veiculo.java:20,26-28` | `@Inheritance(TABLE_PER_CLASS)` declarado sem nenhuma subclasse, combinado com `GenerationType.IDENTITY` | No dia em que alguém criar `Caminhao extends Veiculo`, o Hibernate rejeita `IDENTITY` no boot e queries polimórficas viram `UNION ALL` sem índice |
| FR-ARQ-03 | 🔴 | `service/NotificacaoClient.java:30`, usado em `VeiculoService.java:108,175,220,274` | `RestTemplate` sem timeout de conexão/leitura em chamadas síncronas no caminho de escrita | Se `api-notificacoes` aceitar conexão e não responder, cada `POST /api/veiculos` prende uma thread Tomcat para sempre — ~200 cadastros derrubam a API inteira |
| FR-ARQ-04 | 🟠 | `service/MotoristaService.java:126-143`, `controller/MotoristaController.java:63-66` | `GET /api/motoristas/cnh-vencida` dispara um e-mail por motorista dentro de um handler GET | Um health check, crawler ou retry de proxy gera spam de e-mail — viola a semântica idempotente de GET |
| FR-ARQ-05 | 🟡 | `service/ManutencaoService.java:29-30,57-58,112-118` | Acessa `VeiculoRepository` diretamente, duplicando a checagem de "veículo não encontrado" e escrevendo status sem passar pelas regras de `VeiculoService` | Uma futura invariante de veículo (ex.: "não mudar status com entrega ativa") é silenciosamente contornada pelo fluxo de manutenção |
| FR-ARQ-06 | 🟠 | Zero `@Transactional` em todo o `src/` | Operações multi-entidade (ex.: `ManutencaoService.iniciarManutencao:98-121`) gravam veículo e manutenção em transações separadas | Falha entre os dois `save()` deixa o veículo em `MANUTENCAO` com a manutenção ainda `PENDENTE` — inconsistência permanente |
| FR-ARQ-07 | 🟡 | `controller/HealthCheckController.java:28,43,51` | Controller instancia `RestTemplate` e faz 2 chamadas HTTP bloqueantes sem timeout para portas hardcoded | Probe de liveness trava indefinidamente se uma dependência não responder — orquestrador reinicia o pod em loop mesmo saudável |
| FR-ARQ-08 | 🟡 | `controller/ManutencaoController.java:97-102`, `VeiculoController.java:140-147` | Parsing/coerção de tipos (`Map<String,Object>`, `new BigDecimal(...)`) dentro do controller | `{"custoFinal": null}` gera NPE capturada como `RuntimeException` genérica, devolvendo 400 com mensagem vazia |
| FR-ARQ-09 | 🟡 | Todo o projeto | Sem `@ControllerAdvice`, sem exceções de domínio, sem enums de status, sem DTO de resposta (entidade JPA devolvida crua) | Mudar uma coluna de banco muda o contrato público da API sem aviso |
| FR-ARQ-10 | ⚪ | Todos os controllers/services | Injeção 100% por campo com `@Autowired` (nenhum `final`, nenhum construtor) | Impossível instanciar os services em teste unitário sem subir o contexto Spring |
| FR-ARQ-11 | 🟠 | `VeiculoController.java:39-43` e equivalentes; repositórios retornam `List<T>` puro | Nenhum endpoint de listagem tem paginação | `GET /api/veiculos` com a base cheia materializa tudo em heap — `OutOfMemoryError` |

<a id="frotas-inc"></a>
### Inconsistência no código

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| FR-INC-01 | ⚪ | `VeiculoService.java:304`, `VeiculoController.java:195` | Typo `obterEstatisticasFreita()` ("Freita" em vez de "Frota") virou API interna estável | Renomear depois exige tocar em todos os consumidores |
| FR-INC-02 | 🟠 | `VeiculoService.java:78,95` | Checagem de duplicidade usa a placa crua; persistência salva em maiúsculas | `POST` com placa minúscula passa na checagem, colide com a unique constraint no banco e estoura erro 500 mascarado como 400 |
| FR-INC-03 | 🟠 | `VeiculoService.java:137-144` vs `159-165` | Duas regras diferentes para regressão de quilometragem: uma só loga, outra lança exceção | `PUT` e `PATCH` do mesmo campo produzem comportamento diferente para a mesma entrada |
| FR-INC-04 | 🟡 | `VeiculoService.java:138,163` | NPE por unboxing quando `quilometragem` é `null` (coluna sem `NOT NULL`) | Registro legado com quilometragem nula derruba o update com erro genérico |
| FR-INC-05 | 🔴 | `repository/ManutencaoRepository.java:27`, seed `V2__Seed_Data.sql:33` | `ORDER BY data_manutencao DESC LIMIT 1` — no Postgres, `DESC` implica `NULLS FIRST` | `GET /manutencoes/veiculo/1/ultima` retorna a manutenção `PENDENTE` (data nula) em vez da `CONCLUIDA` real — reproduzível com o próprio seed |
| FR-INC-06 | 🟡 | `ManutencaoService.java:155` | Notificação de conclusão usa o parâmetro `custoFinal` (pode ser `null`) em vez do valor persistido | E-mail ao gestor mostra "Custo: R$ null" enquanto o banco tem o valor correto |
| FR-INC-07 | 🔴 | `ManutencaoService.java:141-145,174-178` | Concluir/cancelar manutenção seta `status="ATIVO"` incondicionalmente, sem validar transição de estado | Reconcluir uma manutenção de veículo `INATIVO` (existe no seed) o reativa na frota indevidamente |
| FR-INC-08 | 🟡 | 9 controllers, ~20 ocorrências | Bloco try/catch de erro duplicado literalmente ~20 vezes | Alterar o formato de erro exige 20 edições sincronizadas |
| FR-INC-09 | 🟡 | `VeiculoController.java:79-89` vs `MotoristaController.java:58-61` | Mesmo padrão de endpoint (filtro por status) tratado com validação em um controller e sem em outro | `GET /api/motoristas/status/BANANA` responde 200 `[]` silenciosamente; o equivalente em veículos responde 400 |
| FR-INC-10 | ⚪ | Controllers | Tipos de retorno inconsistentes: `ResponseEntity<List<T>>`, `ResponseEntity<?>`, `ResponseEntity<String>` com JSON montado à mão | Endpoint de estatísticas devolve `Content-Type: text/plain`, quebrando clientes que fazem parse por content-type |
| FR-INC-11 | 🟡 | `VeiculoService.java:116,164,327` | `System.out.println` misturado com SLF4J na mesma classe anotada `@Slf4j` | Avisos importantes (ex.: quilometragem regressiva) não aparecem em nenhum agregador de log |
| FR-INC-12 | ⚪ | `service/EntregaClient.java` (83 linhas), `NotificacaoClient.enviarSms:73-91`, 3 métodos de `VeiculoService` | Código morto: `@Component` nunca injetado, método nunca chamado, métodos nunca expostos | ~150 linhas mantidas sem valor; `EntregaClient` sugere existir uma regra de negócio que na verdade não está ativa |
| FR-INC-13 | ⚪ | `VeiculoRepository.java` | Três estilos de query no mesmo repositório: derived query, SQL nativo e JPQL | Queries nativas não sofrem validação de mapeamento no boot e dependem do `currentSchema` estar correto na URL JDBC |
| FR-INC-14 | ⚪ | Services | Vocabulário incoerente para a mesma operação (`registrar`/`cadastrar`/`agendar`, `obterPorStatus`/`listarPorStatus`) | Aumenta chance de duplicar um método que já existe sob outro verbo |
| FR-INC-15 | ⚪ | `VeiculoService.java:14-40` | Mistura PT/EN no mesmo arquivo (javadoc em inglês, FIXME em português) | Grep/tooling por convenção falha; tradução de mensagens de erro fica inconsistente |
| FR-INC-16 | ⚪ | Entidades e DTOs | Lombok aplicado parcialmente (`@Getter/@Setter` sem `@EqualsAndHashCode`); `@Slf4j` presente em 3 controllers onde `log` nunca é usado | Entidades sem `equals/hashCode` se comportam mal em `Set`/`Map`; `@Slf4j` morto sugere observabilidade que não existe |
| FR-INC-17 | 🟡 | Controllers → Services | Mapeamento DTO→domínio por listas longas de parâmetros posicionais, sem mapper | Dois parâmetros `String` adjacentes trocados de posição compilam sem erro e gravam dado no campo errado |

<a id="frotas-sec"></a>
### Vulnerabilidades de segurança

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| FR-SEC-01 | 🔴 | Todos os 22 endpoints (nenhuma classe de security config) | Ausência total de autenticação/autorização | Qualquer pessoa com acesso de rede lê/cadastra/desativa veículos e cancela manutenções, sem log de autor |
| FR-SEC-02 | 🔴 | `application.properties:10-11` | Credenciais de banco em texto puro, versionadas | Senha no histórico do Git para sempre; clonar o repo é acesso administrativo ao banco |
| FR-SEC-03 | 🔴 | `MotoristaController.java:31-34,47-56` | `GET /api/motoristas` expõe CNH completa sem auth; CNH também usada como path variable | PII (LGPD) vaza em logs de acesso, `Referer`, cache de proxy; enumerável por força bruta (11 dígitos) |
| FR-SEC-04 | 🟠 | `application.properties:16,18,33-35`, `MotoristaService.java:79` | `show-sql=true` + `TRACE` no binder + log da CNH em `log.info` | Todo INSERT/SELECT de motorista despeja CNH e nome no stdout/agregador de log |
| FR-SEC-05 | 🟠 | `config/CorsConfig.java:23-26` | CORS com `allowedOrigins("*")` e todos os métodos liberados | Combinado com a ausência de auth (SEC-01), qualquer página maliciosa aberta na rede interna pode desativar toda a frota via `fetch` |
| FR-SEC-06 | 🟠 | Todo o projeto (grep confirmado) | `spring-boot-starter-validation` declarado e **nunca usado** — zero `@Valid`/`@NotNull` | `descricao` sem limite grava payload de MBs; `custoEstimado` negativo é aceito |
| FR-SEC-07 | 🟡 | ~20 blocos catch em todos os controllers | `e.getMessage()` devolvido direto ao cliente, capturando `RuntimeException` genérica | Violação de constraint expõe nome de tabela/constraint/SQL ao cliente; erros 5xx mascarados como 400 escondem incidentes reais |
| FR-SEC-08 | 🟡 | `application.properties:5` | Sem `server.ssl.*`, sem redirect HTTP→HTTPS, sem HSTS | Credenciais e CNHs trafegam em claro se não houver proxy TLS obrigatório à frente |
| FR-SEC-09 | ⚪ | `service/EntregaClient.java:41` | Concatenação de `placa` não codificada na URL de chamada HTTP externa (código morto, mas armado) | Placa contendo `&admin=true` injeta parâmetro na requisição ao serviço de entregas, no dia em que for ligado |
| FR-SEC-10 | ⚪ | `VeiculoService.java:110,177,222,276`, `MotoristaService.java:84,134` | E-mails de destino hardcoded no código de negócio, canal fixo em `"email"` | Mudar responsável exige deploy; testes automatizados disparam e-mails reais |

> **Nota de precisão:** não há SQL injection nos repositórios — as 3 queries nativas usam parâmetros nomeados corretamente. O risco de injeção real está em `FR-SEC-09` (URL) e no driver desatualizado (`FR-DEP-03`).

<a id="frotas-dep"></a>
### Estado de dependências

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| FR-DEP-01 | 🟠 | `pom.xml:10` | Spring Boot 2.7.14 — suporte OSS encerrado em nov/2023 | Nenhuma correção de segurança chega mais sem contrato pago; falta até o CVE-2023-34055 (DoS) |
| FR-DEP-02 | 🟠 | BOM transitivo do 2.7.14 | Tomcat embarcado 9.0.78, Spring Framework 5.3.29, Hibernate 5.6.15 — todos anteriores a patches de 2024/25 (ex.: HTTP/2 Rapid Reset CVE-2023-44487) | Rapid Reset é explorável remotamente sem autenticação contra o Tomcat embarcado |
| FR-DEP-03 | 🟠 | `pom.xml:48-53` | Driver PostgreSQL 42.5.1, anterior à correção do CVE-2024-1597 (SQLi, CVSS 9.8) | Risco imediato baixo (exige `preferQueryMode=simple`, não configurado), mas o pin manual garante que a correção nunca chega por upgrade do BOM |
| FR-DEP-04 | 🟡 | `pom.xml:51,59,66,117-118` | 3 dependências pinadas à mão (postgresql, flyway, lombok) sem `<dependencyManagement>` centralizada | Cada uma sai da matriz testada pelo Spring Boot; atualizações ficam espalhadas e sem política |
| FR-DEP-05 | 🟡 | `pom.xml:56-60`, `application.properties:24` | Flyway 8.5.1 declarado mas `spring.flyway.enabled=false` | Peso morto no artefato e falsa sensação de disciplina de migração |
| FR-DEP-06 | 🟡 | `pom.xml:24-25` | `maven.compiler.source/target=11` em vez de `maven.compiler.release` | Compilar com JDK diferente do runtime pode gerar bytecode que falha em produção com `NoSuchMethodError` |
| FR-DEP-07 | ⚪ | `pom.xml:71-74` | `spring-boot-starter-logging` declarado explicitamente (já vem transitivo) | Ruído; risco de conflito de binding SLF4J se outro starter de log for adicionado sem exclusão |
| FR-DEP-08 | 🟡 | `pom.xml:84-148` | Nenhum plugin de qualidade/segurança no build (sem JaCoCo, SpotBugs, dependency-check) | Cobertura 0% passa verde; CVE novo em dependência nunca é detectado |
| FR-DEP-09 | ⚪ | `.gitignore:2` referencia `.mvn/wrapper/` que não existe | Sem Maven Wrapper no repositório | Build não reprodutível — cada dev/CI usa a versão de Maven que tiver instalada |

<a id="frotas-inf"></a>
### Qualidade de infraestrutura

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| FR-INF-01 | 🟠 | `src/test` inexistente | Zero testes, apesar de `spring-boot-starter-test` declarado | Bugs triviais de capturar (FR-INC-02, 05, 07) não têm nenhuma rede de segurança; refatoração é feita às cegas |
| FR-INF-02 | 🔴 | `application.properties:15,24` | Flyway desligado + `ddl-auto=validate` — nenhuma fonte de verdade do schema neste repo | A aplicação **não sobe** em um banco novo: ninguém cria as tabelas e `validate` aborta o boot |
| FR-INF-03 | 🟡 | `db/migration/V1,V2` | Migrations existem mas nunca executam; usam `CREATE TABLE IF NOT EXISTS` (anula a garantia do Flyway); seed de dev empacotado como migration versionada | Drift de schema fica invisível até falhar em produção |
| FR-INF-04 | 🟠 | `controller/HealthCheckController.java:31-58` | Retorna `"status":"UP"` fixo sem checar o banco de dados | Com Postgres fora do ar, o health responde 200 e o orquestrador mantém o pod recebendo tráfego 100% falho |
| FR-INF-05 | 🟡 | Sem `spring-boot-starter-actuator` | Sem `/metrics`, `/prometheus`, readiness/liveness separados | Nenhuma observabilidade além do health artesanal |
| FR-INF-06 | 🟡 | Repositório inteiro | Sem Dockerfile, sem CI (`.github/workflows` etc.) | Build e deploy manuais e não reprodutíveis; nada impede merge de código que não compila |
| FR-INF-07 | 🟡 | `application.properties:30-35` | Sem `logback-spring.xml`, sem correlation ID, sem tracing distribuído, apesar de 3 saltos HTTP síncronos entre serviços | Requisição que atravessa frotas→notificações não pode ser correlacionada em incidente |
| FR-INF-08 | 🟡 | `application.properties:16,18,32-35` | Níveis de log DEBUG/TRACE em produção, `show-sql` síncrono no stdout | Sob carga, I/O de log vira gargalo dominante; `/veiculos/estatisticas` despeja a frota inteira no log |
| FR-INF-09 | 🟠 | Um único `application.properties`, tudo hardcoded | Sem perfis por ambiente, sem `${ENV_VAR}` | Promover para produção exige editar código-fonte e recompilar — viola o fator III do 12-factor |
| FR-INF-10 | 🟡 | Sem `@Version`, sem config de pool HikariCP, sem timeout nos `RestTemplate` | Sem controle de concorrência nem timeout de recurso | Dois `PATCH` simultâneos causam lost update silencioso; ausência de timeout é o gatilho de `FR-ARQ-03` |

---

## 3. rotalog-api-entregas

*Node.js 18 / Express 4.18 / Sequelize 6 — pedidos, rotas, rastreamento em tempo real.*

<a id="entregas-arq"></a>
### Problemas de arquitetura

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| EN-ARQ-01 | 🟡 | 26 funções em `src/` | Mistura callback/promise/async-await: 69% callback, 31% async-await; 3 de 12 arquivos são 100% callback | Dois modelos de propagação de erro coexistem; em `rastreamento.js:40-66` a resposta pode ser enviada dentro de um `.then()` antes do `.catch()` externo capturar, risco de `ERR_HTTP_HEADERS_SENT` |
| EN-ARQ-02 | 🟠 | `src/routes/entregas.js` inteiro | Zero camada de serviço — 100% da regra de negócio dentro dos route handlers | Regra não é reutilizável por consumidor de fila/cron; todo teste exige subir o Express inteiro |
| EN-ARQ-03 | 🟡 | `src/routes/entregas.js:67-77` | Raw SQL com schema `entregas` hardcoded, enquanto o resto usa `DB_SCHEMA` configurável | Trocar `DB_SCHEMA` (staging, multi-tenant) faz só `/api/entregas/stats` continuar lendo do schema antigo, sem erro visível |
| EN-ARQ-04 | 🔴 | `src/services/frotasService.js:26,62,117` | `http.get` nativo sem timeout, retry ou circuit breaker para `api-frotas` | Se api-frotas aceitar conexão e não responder, o handler fica pendurado indefinidamente e o pool de conexões esgota |
| EN-ARQ-05 | 🟡 | `src/services/frotasService.js:94-107` | Reimplementa localmente a checagem `status !== 'ATIVO'` que já existe em outro serviço | Quando api-frotas adicionar novo status (ex. reserva por período), esta API continua aprovando veículos indisponíveis |
| EN-ARQ-06 | 🟠 | `src/services/notificacaoService.js` (135 linhas) | Service inteiro morto — não é importado por nenhum arquivo (grep confirmado) | `.env` anuncia `ENABLE_NOTIFICATIONS=true`; a operação acredita que recebe alertas e nunca recebe nenhum |
| EN-ARQ-07 | 🔴 | 4 fluxos de escrita em `src/routes/entregas.js` (167-190, 262-270, 311-319, 348-356) | Sem `sequelize.transaction()` em operações que escrevem em `entregas` + `rastreamentos` | Falha entre as duas queries deixa a entrega sem o evento correspondente, sem forma de detectar a inconsistência |
| EN-ARQ-08 | 🟡 | `src/routes/entregas.js` (365 linhas, 28% do `src/`) | God file: roteamento + validação + regra de negócio + ORM + raw SQL + logging no mesmo arquivo | Qualquer mudança toca o mesmo arquivo; alto risco de conflito de merge |
| EN-ARQ-09 | 🟠 | `src/models/Entrega.js:32-41`, `src/routes/entregas.js:306-307` | `motorista_nome`/`veiculo_modelo` gravados direto do `req.body`, sem consultar api-frotas | Cliente da API escolhe o nome do motorista registrado — vetor de falsificação de registro operacional |
| EN-ARQ-10 | 🟡 | `src/index.js:91` | `sequelize.sync({alter:false})` no boot, coexistindo com `migration.sql` | Se alguém trocar para `alter:true` (já cogitado em FIXME), o boot em produção altera colunas automaticamente e pode truncar dados |

<a id="entregas-inc"></a>
### Inconsistência no código

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| EN-INC-01 | 🟠 | `src/index.js:81`, 0 ocorrências de `next(err)` | `errorHandler` global registrado mas inalcançável a partir das rotas | O único caminho que chega até ele (erro interno do Express) vaza `err.stack` ao cliente |
| EN-INC-02 | 🟡 | Múltiplos arquivos | 4 formatos distintos de resposta de erro, 2 deles vazando `error.message` cru do Sequelize | Nenhum cliente trata erro de forma genérica; nomes de tabela/host internos vazam |
| EN-INC-03 | ⚪ | `src/routes/entregas.js`, `rastreamento.js` | Formatos de resposta de sucesso inconsistentes (array puro / model puro / envelope customizado) | Consumidores da API precisam de lógica ad-hoc por endpoint |
| EN-INC-04 | 🟡 | `rastreamento.js:23-25` vs `entregas.js:52` | 404 para coleção vazia num endpoint, 200+`[]` no outro | Cliente não distingue "entrega inexistente" de "entrega sem eventos ainda" |
| EN-INC-05 | ⚪ | 6 locais | Validação de "entrega existe" duplicada com 3 mensagens de erro diferentes | Manutenção precisa tocar 6 lugares para mudar uma mensagem |
| EN-INC-06 | 🟠 | `entregas.js:212-218`, `rastreamento.js:51-57` | Validação de obrigatoriedade presente em só 2 de 9 rotas de escrita | `PUT /:id` aceita `peso_kg` string/negativo; coordenadas GPS sem checar faixa válida |
| EN-INC-07 | ⚪ | Múltiplos arquivos | Naming inconsistente (`id`/`entregaId`/`numeroPedido`, `?veiculo=` vs coluna `veiculo_placa`), PT/EN misturado | Aumenta curva de aprendizado da API para novos consumidores |
| EN-INC-08 | ⚪ | 15 ocorrências | `var` convivendo com `const`/arrow functions no mesmo projeto | Sintoma de código de eras diferentes sem padronização |
| EN-INC-09 | 🟡 | `entregas.js:153` vs `populate.sql:18` | Formato de `numero_pedido` do código (`PED-timestamp-random`) diverge do seed (`PED-2024-001`) | Coluna é `UNIQUE`; duas criações no mesmo milissegundo têm 1/1000 de chance de colisão → 500 sem retry |
| EN-INC-10 | ⚪ | 158 marcadores no repo | Densidade alta de `FIXME`/`TODO` substituindo implementação real | `entregas.js:300-302` documenta ausência total de validação de veículo/motorista antes de gravar |
| EN-INC-11 | 🟡 | `.env:28-30` | Feature flags declaradas (`ENABLE_TRACKING`, `ENABLE_NOTIFICATIONS`, `ENABLE_CACHE`) e nunca lidas no código | Desligar `ENABLE_NOTIFICATIONS=false` num incidente não tem efeito nenhum — falso kill switch |
| EN-INC-12 | 🟡 | `.gitignore:3,44-45` | Autocontraditório: ignora `.env` e `package-lock.json`, mas ambos estão rastreados no git | Sinaliza decisão consciente de versionar segredo, documentada só em comentário |

<a id="entregas-sec"></a>
### Vulnerabilidades de segurança

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| EN-SEC-01 | 🔴 | `src/middleware/auth.js:16-19`, `.env:5` | Bypass total de auth quando `NODE_ENV !== 'production'`; `.env` versionado já define `development` | Basta a env var não ser setada em produção para a API inteira, incluindo `DELETE`, ficar aberta na internet |
| EN-SEC-02 | 🔴 | `src/middleware/auth.js:28-48` | "JWT" não decodifica, não verifica assinatura, não checa expiração — aceita qualquer string ≥10 chars e atribui `role:'admin'` fixo | `Authorization: Bearer aaaaaaaaaa` autentica como admin; qualquer token acessa qualquer entrega |
| EN-SEC-03 | 🔴 | `.env:13,14,19` (rastreado no git), fallback hardcoded em `database.js:19-20`, `auth.js:12` | Credenciais de banco e `JWT_SECRET` versionadas, com fallback hardcoded no código | Apagar o `.env` não quebra o boot — a app sobe silenciosamente com a senha pública do repositório |
| EN-SEC-04 | 🟠 | `src/index.js:76` | Rotas de rastreamento montadas sem autenticação, incluindo escrita (`POST /api/rastreamento/:entregaId`) | Qualquer pessoa na internet injeta eventos GPS falsos em qualquer entrega, sem rate limit |
| EN-SEC-05 | 🟠 | `rastreamento.js:16,96-123` | IDOR — `entregaId` sequencial e `numeroPedido` público devolvem histórico completo de posições e endereços sem auth | Varrer IDs de 1 a N extrai a base inteira de endereços e posição em tempo real dos veículos |
| EN-SEC-06 | 🟠 | `src/index.js:34-42` | CORS wildcard escrito à mão (`cors` do `package.json` nunca é usado) | Combinado com EN-SEC-01/02, página maliciosa aberta pelo operador pode cancelar todas as entregas via fetch |
| EN-SEC-07 | 🟡 | `errorHandler.js:12,20`, `database.js:27` | Vazamento de stack trace, log do corpo completo da requisição e log de todo SQL com valores | Dados pessoais (endereços) em texto claro em qualquer coletor de log |
| EN-SEC-08 | 🟡 | Repositório inteiro | Sem rate limiting em nenhum endpoint | Agrava EN-SEC-04/05 e permite abuso do `/stats` (full scan sem cache) |
| EN-SEC-09 | 🟡 | `src/index.js`, `database.js:40` | Sem `helmet`/headers de segurança; conexão Postgres sem TLS (TODO) | Nenhuma proteção de header padrão; tráfego de banco em claro |
| EN-SEC-10 | ⚪ | `entregas.js:33,28,38,98`, `rastreamento.js:19` | Não há SQL injection real (queries parametrizadas), mas há falhas de tipo não tratadas (array/objeto onde se espera string) | `?status=a&status=b` ou `id` não numérico gera `TypeError`/erro de cast → 500, vetor trivial de DoS |
| EN-SEC-11 | 🟡 | `entregas.js:289,306-307` | Mass assignment de `motorista_nome`/`veiculo_modelo` vindo do `req.body` sem verificação | Registro de quem realizou a entrega, potencial prova operacional/jurídica, é ditado pelo cliente da API |
| EN-SEC-12 | 🟠 | `entregas.js:245-260,336-357` | Sem máquina de estados — FIXME admite "pode ir de ENTREGUE para PENDENTE"; `DELETE` cancela entrega já `ENTREGUE` | Entrega concluída e faturada pode ser revertida por qualquer chamada, sem trilha de quem fez (req.user é fixo) |

<a id="entregas-dep"></a>
### Estado de dependências

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| EN-DEP-01 | 🟠 | `package.json:17` | `express: ^4.18.2` — piso admite versão anterior ao CVE-2024-29041 (open redirect) | Sem lockfile confiável, instalação livre dentro do caret reintroduz a vulnerabilidade |
| EN-DEP-02 | 🟠 | `package.json:18` | `sequelize: ^6.28.0` — piso anterior à correção de injeção via `replacements`/`attributes` (jan/2023) | Mesmo risco: só o lockfile protege, e o lockfile está em situação ambígua (ver EN-DEP-03) |
| EN-DEP-03 | 🟠 | `.gitignore:3` vs `git ls-files` | `package-lock.json` está no `.gitignore` mas rastreado e modificado sem commit | Divergência de versão entre devs/produção passa despercebida — ativa diretamente EN-DEP-01/02 |
| EN-DEP-04 | ⚪ | `package.json:22` | `cors` declarado e nunca importado (CORS é reimplementado à mão, ver EN-SEC-06) | Superfície de dependência sem contrapartida; falsa impressão em auditoria |
| EN-DEP-05 | 🔴 | Ausente | Nenhuma biblioteca JWT no `package.json`, apesar do middleware se dizer "Auth Middleware" | Confirma tecnicamente EN-SEC-02 — o serviço é estruturalmente incapaz de validar um token |
| EN-DEP-06 | ⚪ | `package.json:25` | `nodemon@^2.0.20` — linha 2.x sem manutenção desde 2023 (dev-only) | Ruído permanente em `npm audit`, impacto limitado |

<a id="entregas-inf"></a>
### Qualidade de infraestrutura

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| EN-INF-01 | 🟠 | `package.json:9` | `"test": "echo ... && exit 1"` — nenhum framework de teste instalado | `npm test` falha por design; qualquer CI que rode isso fica vermelho permanentemente |
| EN-INF-02 | 🔴 | `src/index.js:60-72`, `database.js:45-53` | Health check existe mas é decorativo (`database:'UNKNOWN'` fixo); falha de conexão só é logada, boot prossegue | Com Postgres fora do ar, a API sobe, responde 200 no health e devolve 500 em todo endpoint funcional |
| EN-INF-03 | 🟡 | Repositório inteiro | Sem Dockerfile/docker-compose; scripts `db:migrate`/`db:seed` assumem `psql` local com host hardcoded | Ambiente de execução irreprodutível; migrations não rodam contra outro host sem editar `package.json` |
| EN-INF-04 | 🟡 | Repositório inteiro | Sem CI/CD, sem lint, sem formatter | Nada impede merge de código com `var`, secrets ou dependência vulnerável |
| EN-INF-05 | 🟡 | 73 ocorrências de `console.*` | Logging não estruturado, sem nível, sem correlation ID | Impossível correlacionar um erro 500 com a requisição que o originou sob concorrência |
| EN-INF-06 | 🟠 | `src/index.js:135-144` | `unhandledRejection` só loga; `uncaughtException` faz `process.exit(1)` sem drenar conexões; sem handler de `SIGTERM` | Deploy/rolling update corta requisições em voo no meio de uma escrita multi-tabela (agrava EN-ARQ-07) |
| EN-INF-07 | 🟠 | Ausente | Sem middleware de validação de input (joi/zod/express-validator) | Causa raiz direta de EN-INC-06, EN-SEC-11 e EN-SEC-12 — cada rota nova repete ou esquece a checagem |
| EN-INF-08 | 🟡 | Um único `.env` versionado, sem `.env.example` | Sem configuração por ambiente, leitura ad-hoc com fallback hardcoded | Subir sem `DB_PASSWORD` não falha — cai no fallback público (reforça EN-SEC-03) |
| EN-INF-09 | 🔴 | `src/config/populate.sql:8-9` vs `seed.sql` | Dois scripts de dados divergentes; `populate.sql` faz `TRUNCATE ... CASCADE` sem guarda de ambiente e não é o referenciado no `package.json` | Rodar o script "errado" apaga a base inteira |
| EN-INF-10 | 🟡 | `migration.sql:53-55` | Faltam índices para os padrões de acesso reais (`veiculo_placa`, `motorista_id`, `data_criacao`, `data_evento`); sem FK em `rastreamentos.entrega_id` | Queries mais usadas fazem sequential scan; eventos órfãos possíveis |
| EN-INF-11 | 🟠 | `entregas.js:41-50` | `GET /api/entregas` sem paginação, com eager loading de todos os rastreamentos | Payload e consumo de memória crescem sem limite — é o endpoint que api-frotas consome |

---

## 4. rotalog-api-notificacoes

*.NET Core 6 / EF Core / MediatR (registrado, não usado) — envio de e-mail e SMS transacionais.*

<a id="notif-arq"></a>
### Problemas de arquitetura

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| NT-ARQ-01 | ⚪ | `Program.cs:38` | `AddMediatR(typeof(Program))` registrado; zero `IRequest`/`IRequestHandler` no assembly (grep confirmado) | Dois pacotes MediatR escaneiam o assembly no startup sem entregar nada — falsa impressão de CQRS |
| NT-ARQ-02 | 🟠 | `Program.cs:41`, `Controllers/NotificacoesController.cs:20,24` | `NotificacaoService` concreto injetado, sem interface | Impossível mockar em teste unitário ou trocar implementação (ex.: fila) sem alterar o controller |
| NT-ARQ-03 | 🟠 | `Services/NotificacaoService.cs:19-369` | God class de 369 linhas: CRUD + orquestração de envio + transporte e-mail/SMS + motor de template + retry + estatísticas | Mudança em template ou provedor SMS recompila e re-testa todo o domínio de notificação |
| NT-ARQ-04 | 🔴 | `Controllers/HealthController.cs:56-71,73-87` | Abre conexões `NpgsqlConnection` cruas para os schemas `frotas`/`entregas` de outros microsserviços | `api-frotas`/`api-entregas` não podem renomear tabela sem quebrar o health check de notificações; números ignoram regras de negócio dos donos |
| NT-ARQ-05 | 🟡 | `Controllers/NotificacoesController.cs:88-93` vs `Services/NotificacaoService.cs:82-85` | Validação de obrigatoriedade duplicada e divergente entre controller e service | As duas cópias já divergem (controller exige mensagem OU variáveis; service não) |
| NT-ARQ-06 | 🟡 | `Services/NotificacaoService.cs:130-132` | Envio de e-mail/SMS disparado dentro da própria transação HTTP do `POST` | Timeout de SMTP de 30s vira timeout do `api-entregas` que chamou; sem fila nem dead-letter |
| NT-ARQ-07 | 🟡 | `Data/NotificacoesDbContext.cs:24` | `ConfiguracaoNotificacao` (opt-out, canal preferido) mapeado mas nunca consultado | Sistema envia notificação para quem pediu explicitamente para não receber |

<a id="notif-inc"></a>
### Inconsistência no código

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| NT-INC-01 | ⚪ | `NotificacoesController.cs:159` | Endpoint de templates devolve entidade EF crua; os demais usam `NotificacaoResponse` | Adicionar coluna interna no template vaza automaticamente para consumidores da API |
| NT-INC-02 | 🔴 | `NotificacaoService.cs:113,239-249,53` | Canal salvo sem normalização (`"EMAIL"` != `"email"`) mas comparado com igualdade exata | Cliente que envia `"canal":"EMAIL"` faz a notificação cair em `NotSupportedException` após 3 tentativas e sumir dos filtros |
| NT-INC-03 | 🟡 | `NotificacaoService.cs:47,50,53,56` | Normalização de filtros assimétrica (`ToUpper`/`ToLower` só em alguns campos) | Filtro por `tipo=entrega_criada` retorna vazio silenciosamente se o casing divergir |
| NT-INC-04 | 🔴 | `NotificacaoService.cs:91-108` | Se nenhum template casa Tipo+Canal, usa `request.Mensagem`, que pode ser `""` | Notificação persistida e marcada `ENVIADO` com corpo vazio — perda silenciosa de comunicação |
| NT-INC-05 | 🟡 | `NotificacoesController.cs` (7 blocos), `HealthController.cs:89-107` | Tratamento de exceção inconsistente: controller vaza `ex.Message`; Health engole tudo e sempre retorna 200 | Probe de liveness do Kubernetes nunca falha, mesmo com `status="DEGRADED"` |
| NT-INC-06 | 🟡 | Todo `Controllers/`, `Services/` | Ausência total de `CancellationToken` nas assinaturas async | Cliente desiste do request e o `ToListAsync()` de tabela inteira continua consumindo conexão |
| NT-INC-07 | ⚪ | Todo o projeto | Naming PT/EN misturado (`api_notificacoes` snake_case, métodos PT + `MapToResponse`/`Health()` EN); rota absoluta sobrescrevendo `[Route]` de classe em `HealthController.cs:16,37` | Aumenta carga cognitiva; roteamento inconsistente entre controllers |
| NT-INC-08 | 🟡 | `Models/Notificacao.cs:25,30,48` | `Tipo`/`Status`/`Canal` como `string` livre (FIXME admite que deveria ser enum) | Typo em `"ENVIADO"` não é detectado em compilação e corrompe estatísticas |

<a id="notif-sec"></a>
### Vulnerabilidades de segurança

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| NT-SEC-01 | 🔴 | `appsettings.json:10` (rastreado no git) | Connection string com credencial em texto claro do usuário admin compartilhado | Senha do banco (todos os schemas) permanente no histórico do repositório |
| NT-SEC-02 | 🔴 | `appsettings.json:16` | Senha SMTP hardcoded, remetente `noreply@rotalog.com` | Quem lê o repo pode enviar phishing em nome do domínio legítimo |
| NT-SEC-03 | 🔴 | `appsettings.json:19` | API key de SMS hardcoded | Custo direto por SMS emitido por terceiros que leiam o repositório |
| NT-SEC-04 | 🟠 | `Program.cs:44-52` | CORS `AllowAnyOrigin/AllowAnyMethod/AllowAnyHeader` | Combinado com ausência de auth, qualquer página web dispara `POST /api/notificacoes` a partir do browser da vítima |
| NT-SEC-05 | 🔴 | `Program.cs:54,76`, nenhum `[Authorize]` | Zero autenticação em 100% dos endpoints | `POST /api/notificacoes` é open relay — qualquer um envia e-mail/SMS arbitrário com credenciais da empresa; `GET` sem paginação devolve todo o histórico com PII |
| NT-SEC-06 | 🟠 | `HealthController.cs:101-106` | Endpoint público expõe `SmtpServer`/`SmsApiUrl` e volumetria de negócio de outros serviços | Reconhecimento de infraestrutura sem autenticação |
| NT-SEC-07 | 🟠 | `HealthController.cs:61-79`, `appsettings.json:10` | Acesso direto aos schemas de outros serviços com o mesmo usuário admin | Comprometer notificações = comprometer frotas e entregas |
| NT-SEC-08 | 🟡 | `Program.cs:74`, `launchSettings.json:9`, `appsettings.json:8` | HTTPS redirect comentado; só `http://localhost:5000`; `AllowedHosts:"*"` | PII e futuros tokens trafegam em claro |
| NT-SEC-09 | 🟡 | 7 blocos catch em `NotificacoesController.cs` | `ex.Message` devolvido ao cliente em todos os endpoints | Mensagem do Npgsql expõe host, usuário, tabela e schema a atacante anônimo |
| NT-SEC-10 | 🟡 | `Program.cs:63-68` | Swagger habilitado incondicionalmente (sem guarda `IsDevelopment()`) | Catálogo completo de endpoints publicado em produção |
| NT-SEC-11 | 🟠 | `NotificacaoService.cs:288-295,317-319,127-128`, `Program.cs:88,92` | PII (destinatário, corpo de SMS, CNH via seed) e erro de conexão logados em texto puro | Dado pessoal em log não estruturado sem retenção definida (LGPD) |

<a id="notif-dep"></a>
### Estado de dependências

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| NT-DEP-01 | 🟠 | `api-notificacoes.csproj:4` | `net6.0` — fim de suporte em 12/11/2024, ~21 meses sem patch | Vulnerabilidades de runtime/ASP.NET nunca serão corrigidas |
| NT-DEP-02 | 🟠 | `api-notificacoes.csproj:12` | `Npgsql.EntityFrameworkCore.PostgreSQL 6.0.8`, anterior à correção 6.0.11 (CVE-2024-32655) | Recomenda-se `dotnet list package --vulnerable --include-transitive` para confirmar exposição |
| NT-DEP-03 | 🟡 | `api-notificacoes.csproj:13-14` | EF Core e `.Design` em 6.0.12 (dez/2022), sem os últimos patches da linha 6.0.x | Bugs/CVEs corrigidos na linha não chegam ao projeto |
| NT-DEP-04 | ⚪ | `api-notificacoes.csproj:15-16` | MediatR 11.1.0 + extensão DI — duas dependências mortas (ver NT-ARQ-01) que travam upgrade futuro (MediatR 12+ muda o registro) | Superfície de supply chain sem contrapartida |
| NT-DEP-05 | 🟡 | `api-notificacoes.csproj:11` | Swashbuckle 6.5.0 (2023) desatualizado, relevante porque Swagger é servido em qualquer ambiente | Reforça NT-SEC-10 |
| NT-DEP-06 | 🟡 | `api-notificacoes.csproj:18-21` | Sem pacote de health check, Serilog, FluentValidation ou Polly (declarados como dívida em comentário) | Validação/retry/observabilidade reimplementados manualmente, com bugs próprios (NT-INC-05, NT-INF) |

<a id="notif-inf"></a>
### Qualidade de infraestrutura

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| NT-INF-01 | 🟠 | Repositório inteiro | Zero projeto de teste; agravado por `new Random()` no fluxo de envio | Sem interface no service (NT-ARQ-02) e com aleatoriedade embutida, testes seriam não-determinísticos por construção |
| NT-INF-02 | 🔴 | `Program.cs:80-95` | `EnsureCreated()` em vez de migrations; falha de conexão no boot só é logada, app prossegue | Banco indisponível no boot → app sobe, health responde 200, todo endpoint funcional devolve 500 |
| NT-INF-03 | 🟠 | `Data/migration.sql` vs `Models/*.cs` | Duas fontes de verdade divergentes para o schema — o modelo EF usado por `EnsureCreated()` não define nenhum índice | Quem sobe via `EnsureCreated()` fica sem índice algum; `/stats` e listagem fazem full scan |
| NT-INF-04 | 🟠 | `Program.cs:56`, `HealthController.cs` | Sem `IHealthCheck`; health artesanal abre conexão nova a cada request, sem cache/timeout | Probe agressivo do orquestrador multiplica conexões diretas nos bancos de frotas e entregas |
| NT-INF-05 | 🟡 | `Program.cs:88,92,97-112` vs `ILogger` nos services | Logging misto (`Console.WriteLine` + `ILogger`), sem correlation ID | Incidente em produção sem rastro correlacionável entre `api-entregas` e a notificação falhada |
| NT-INF-06 | 🟡 | `Program.cs:55`, `NotificacoesController.cs:11` | Sem rate limiting; `POST /processar` e `POST /` são anônimos | Loop trivial gera custo ilimitado no provedor de SMS |
| NT-INF-07 | 🟡 | Repositório inteiro | Sem Dockerfile, sem docker-compose, sem CI | Deploy 100% manual; commit com `appsettings.json` alterado pode ir direto a produção |
| NT-INF-08 | ⚪ | `obj/*` (5 arquivos rastreados) apesar de `.gitignore:2` conter `obj/` | Artefatos de build versionados, modificados a cada build | Ruído/conflito em todo PR; caminhos absolutos da máquina de origem no repositório |
| NT-INF-09 | 🟠 | `NotificacaoService.cs:231-273,206-223` | Retry sem backoff exponencial, sem circuit breaker, sem lock distribuído no processamento em lote | Duas instâncias (ou 2 POSTs concorrentes em `/processar`) selecionam o mesmo lote e enviam a mesma notificação duas vezes |

---

## 5. rotalog-frontend

*NX 19.8 — Angular 18.2 (`painel-admin`) + React 18.3 (`rastreamento`), libs compartilhadas não conectadas.*

<a id="frontend-arq"></a>
### Problemas de arquitetura

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| FE-ARQ-01 | 🟠 | `apps/painel-admin/.../veiculos.component.ts:1-464` | God Object: 464 linhas concentrando filtros, tabela, formulário e painel de detalhes no mesmo arquivo | Qualquer alteração em um dos quatro blocos toca o arquivo inteiro — conflitos de merge constantes |
| FE-ARQ-02 | 🟡 | `.../dashboard.component.ts:1-349,319-335` | God Object com 9 agregações de negócio calculadas no componente, duplicadas em `entregas.component.ts:196-198` | As duas contagens podem divergir sem nenhum aviso |
| FE-ARQ-03 | 🟡 | `dashboard.component.ts:314-316` | 3 `await` sequenciais contra 2 backends distintos em vez de `Promise.all` | Latência somada (3× RTT) sem necessidade |
| FE-ARQ-04 | 🟠 | `dashboard.component.ts:346-348` | `window.location.href` em vez do `Router` do Angular (já injetado) | Full page reload a cada clique — perde estado e cache em memória |
| FE-ARQ-05 | 🟠 | `app.routes.ts:11-20` | Zero lazy loading — todas as rotas eager, sem `loadComponent`/`loadChildren` | Bundle inicial único de 363 kB mesmo que o usuário só abra o Dashboard |
| FE-ARQ-06 | 🔴 | `frotas.service.ts` (11 chamadas), `entregas.service.ts` (3), `app.config.ts:6` | `fetch()` cru em vez de `HttpClient` — e `provideHttpClient()` sequer está registrado | Impossível adicionar interceptor de auth/retry/erro global sem reescrever 14 pontos de chamada |
| FE-ARQ-07 | 🔴 | `frotas.service.ts` e `entregas.service.ts` (14 catches) | Todo erro de API é engolido e vira `[]`/`null` | API fora do ar renderiza "Nenhum veículo encontrado" em vez de erro — operador pode recadastrar duplicado |
| FE-ARQ-08 | 🟡 | `frotas.service.ts:16-17,190-193` | Cache manual invalidado só em 2 telas; `entregas.service.ts` não tem cache | Dashboard exibe contagens defasadas após cadastro em outra tela, até reload completo |
| FE-ARQ-09 | 🟡 | `App.tsx:12-26` | 100% prop drilling manual — todo o estado da SPA React vive no componente raiz | Qualquer novo consumidor de estado exige alterar `App.tsx` e toda a cadeia de props |
| FE-ARQ-10 | ⚪ | `main.tsx:6`, `App.tsx:24-25,38`, outros | Redux citado 8 vezes em TODOs mas não existe no projeto (ver correção de lore) | Comentários enganam quem for refatorar — não há Redux para "migrar para Toolkit" |
| FE-ARQ-11 | 🟡 | `DeliveryList.tsx:16-19,22-26` | Estado derivado de props via `componentDidUpdate`, comparação por referência | Lista pode congelar se o pai passar o mesmo array mutado |
| FE-ARQ-12 | 🟡 | `MapView.tsx:32-36`, `App.tsx:23,57-64` | `mapCenter`/`zoom` nunca disparam `setState` — recentragem acontece só por acaso via `fitBounds` | Estado morto que não reflete o que a UI mostra |
| FE-ARQ-13 | 🟡 | Todos os componentes React | Nenhuma separação container/presentational — fetch, transformação e render juntos | Nada é testável sem mock de rede nem reutilizável fora do portal |
| FE-ARQ-14 | 🔴 | `libs/shared-types`, `ui-components`, `api-contracts` | Sem `project.json`/`tsconfig.json`; não são projetos Nx; zero import em `apps/` (grep confirmado) | As 3 libs "compartilhadas" são código morto — nunca compiladas, lintadas ou testadas |

<a id="frontend-inc"></a>
### Inconsistência no código

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| FE-INC-01 | ⚪ | `apps/rastreamento/src/` | React é **100%** class components (não 70% — ver correção de lore); zero function components no app | Migração para hooks é do app inteiro, não parcial |
| FE-INC-02 | 🟠 | 4 componentes React, `nx lint` confirmado | 40 usos de `any` (`@typescript-eslint/no-explicit-any`) — todos os componentes são `React.Component<any,any>` | Erros de campo (`delivery.numero_pedido` etc.) só aparecem em runtime como `undefined` |
| FE-INC-03 | 🟡 | `veiculos.component.ts:402`, `dashboard.component.ts:338` | `any` também no Angular, contornando `noPropertyAccessFromIndexSignature: true` | `ordenar()` com campo inexistente compila e retorna ordem arbitrária |
| FE-INC-04 | 🟠 | `tsconfig.base.json:14` vs `apps/rastreamento/tsconfig.app.json:7-11`, `.babelrc:9` | `strict:true` no base, mas React desliga 5 flags e usa Babel (que apaga tipos sem checar) | Zero type-check real no pipeline do React — erros de tipo vão para produção |
| FE-INC-05 | 🟡 | Angular (CSS inline no decorator), CSS global (`styles.css` com `*`/`button`/`a`), libs (inline styles JS) | 3 abordagens de estilo diferentes no mesmo monorepo | Botão novo herda verde sólido do CSS global sem ninguém ter declarado |
| FE-INC-06 | 🟡 | `nx build painel-admin` (reproduzido) | 4 componentes já excedem o budget de CSS por componente (2.05 kB) | Mais ~1.2 kB de CSS em qualquer um deles quebra o build de produção |
| FE-INC-07 | 🟠 | `models/index.ts:5-18` vs `libs/shared-types/src/index.ts:5-16,31-48` | 3 definições divergentes do mesmo domínio (`Veiculo`, `Entrega`) — snake_case/camelCase/PT/EN misturados no mesmo objeto | Mapeamento automático (ex.: gerador a partir de OpenAPI) é impossível; React consome campos que não existem em nenhuma das duas |
| FE-INC-08 | 🟡 | `models/index.ts:69-75` | Enum `StatusVeiculo` coexiste com `status:string` sem uso do enum nos templates | Enum é código morto; validação de status inexistente |
| FE-INC-09 | 🟡 | `dashboard.component.ts:327-329,262-265` | Dashboard tolera dois casings de status ao mesmo tempo (`EM_TRANSITO`/`em_transito`) | Terceiro formato (`Em_Transito`) some da contagem silenciosamente |
| FE-INC-10 | 🟡 | `motoristas.component.ts:7` (comentário explícito) | Lógica duplicada entre componentes Angular via copy-paste declarado | Correção de bug no filtro precisa ser aplicada 3 vezes (veículos/motoristas/entregas) |
| FE-INC-11 | 🟡 | `DeliveryList.tsx:5-11` e `TrackingDashboard.tsx:6-12` | `STATUS_CONFIG` duplicado literalmente entre 2 componentes React (FIXME admite) | Adicionar status `ATRASADA` exige editar dois arquivos |
| FE-INC-12 | ⚪ | `libs/ui-components/StatusBadge.tsx` vs 6 CSS locais diferentes | Lib de badge existe mas cada tela reimplementa sua própria paleta de status | 6 paletas independentes; a lib usa chaves em inglês que nenhum backend devolve |
| FE-INC-13 | ⚪ | README.md vs `libs/ui-components/src/*.tsx` | README descreve a lib como Angular; o código é React | Quem tentar consumir a lib no painel-admin descobre tarde que é incompatível |
| FE-INC-14 | ⚪ | `App.tsx:2,126-128`, 3 outros componentes | PropTypes + TypeScript simultâneos via `as any`; em `App` o objeto de PropTypes está vazio | Falsa sensação de contrato de props; erro de lint `no-unused-vars` confirmado |
| FE-INC-15 | 🟡 | `package.json:75-76`, `MapView.tsx:8,52-63` | Leaflet/react-leaflet instalados mas nunca importados; mapa real carrega Leaflet via CDN global | 3 dependências empacotadas sem uso; mapa em produção depende de host de terceiros |
| FE-INC-16 | ⚪ | `nx-welcome.component.ts:1-808` | Maior arquivo do app (808 linhas) é boilerplate morto do gerador Nx, só referenciado por um teste | Polui métricas de tamanho e buscas no repositório |
| FE-INC-17 | 🟡 | Commit inicial menciona "Angular 14 e React 17"; `package.json` está em 18.2/18.3 | Salto de 4 majors do Angular feito sem `migrations.json`/registro | Explica os resíduos de configuração encontrados nos itens de infra (ex. módulo/target divergentes) |

<a id="frontend-sec"></a>
### Vulnerabilidades de segurança

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| FE-SEC-01 | 🔴 | `MapView.tsx:141-159,207-215` | XSS armazenado: dados da API concatenados em `bindPopup`/`L.divIcon` como HTML cru | Endereço malicioso (`<img onerror=...>`) cadastrado no admin executa no navegador de qualquer visitante do portal público |
| FE-SEC-02 | 🟠 | `frotas.service.ts:8`, `entregas.service.ts:6`, `App.tsx:40`, `TrackingDashboard.tsx:56` | URLs de API hardcoded em `http://localhost`, sem pasta `environments/` | Build de produção aponta para localhost — não funciona sem recompilar; `http://` puro bloqueia mixed content em host HTTPS |
| FE-SEC-03 | 🔴 | `app.component.ts:5`, `app.routes.ts` (sem guards) | Zero autenticação/autorização em todo o backoffice | Qualquer pessoa com a URL executa `DELETE /api/veiculos/{id}` pela UI, sem identificação de autor |
| FE-SEC-04 | 🔴 | `App.tsx:40` | Portal público lista a base inteira de entregas (nome do motorista, placa, endereços) sem filtro nem auth | Concorrente lê a operação inteira abrindo o site; destinatário vê o endereço de outro cliente |
| FE-SEC-05 | 🟡 | `TrackingDashboard.tsx:38-64` | Polling de 5s por entrega, sem auth, sem uso da resposta (`data` descartado, confirmado pelo lint) | 12 req/min por aba aberta sem benefício funcional — vetor de amplificação/DoS |
| FE-SEC-06 | 🟠 | `veiculos.component.ts:440-443`, `motoristas.component.ts:238-241` | Validação de formulário praticamente inexistente (`alert()`, sem `Validators`, sem máscara) | CPF/CNH/placa inválidos e `km_atual` negativo entram no banco sem checagem |
| FE-SEC-07 | 🟡 | `index.html` de ambos os apps | Sem Content-Security-Policy; `MapView.tsx` injeta `<script>` externo em runtime (mitigado parcialmente por SRI) | Sem CSP, o XSS de FE-SEC-01 tem execução e exfiltração irrestritas |
| FE-SEC-08 | 🟡 | `App.tsx:50-53,86` | Erro do backend (`err.message`) renderizado direto na UI pública | Disclosure de estrutura interna (stack, tabela) no portal público |
| FE-SEC-09 | ⚪ | `veiculos.component.ts:441,458`, `motoristas.component.ts:239` | `confirm()`/`alert()` nativos como única proteção contra exclusão destrutiva | Sem confirmação de digitação nem verificação de permissão antes de excluir |
| FE-SEC-10 | ⚪ | — (verificado, não é achado) | Não há `localStorage`/`innerHTML`/`dangerouslySetInnerHTML`/tokens no código — grep confirmou zero ocorrências | Risco de "dado sensível em storage" não existe porque não existe nenhuma persistência de sessão |

<a id="frontend-dep"></a>
### Estado de dependências

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| FE-DEP-01 | 🟡 | `package.json:21-22` | `@nrwl/angular`/`@nrwl/react` (namespace legado, renomeado para `@nx/*` desde o Nx 16/2023) convivendo com os pacotes `@nx/*` atuais | `nx migrate` pode atualizar um caminho e deixar o outro para trás |
| FE-DEP-02 | ⚪ | `package.json:22,29` | `@nrwl/react` e `@nx/react` declarados simultaneamente, mesma versão | Peso redundante e ambiguidade sobre qual usar em generators |
| FE-DEP-03 | 🟡 | `package.json:23-31` | `@nx/angular` **não está declarado** — só chega via o pacote legado `@nrwl/angular` | Remover o pacote legado quebra os geradores Angular sem aviso |
| FE-DEP-04 | 🟡 | `apps/rastreamento/project.json:32` | Executor legado `@nx/linter:eslint` (depreciado desde Nx 16.8), enquanto `painel-admin` usa o atual `@nx/eslint:lint` | Inconsistência entre apps; quebra assim que `@nx/eslint` parar de arrastar o shim |
| FE-DEP-05 | 🟠 | `package.json:12-19,51-53` vs `74-77` | Dependências de runtime (`@angular/*`, `react`, `react-dom`) em `devDependencies`; `dependencies` só tem `leaflet`/`react-leaflet` | `npm ci --omit=dev` produz um build que não compila |
| FE-DEP-06 | ⚪ | `package.json:75-76,40` | `leaflet`/`react-leaflet`/`@types/leaflet` instalados e nunca importados (ver FE-INC-15) | Superfície de CVE/auditoria sem entrega de valor |
| FE-DEP-07 | ⚪ | `package.json:50` | `prettier@^2.6.2`, uma major atrás de `eslint-config-prettier@^9` | Formatação divergente conforme a versão global instalada no dev |
| FE-DEP-08 | ⚪ | `package.json:39` | `@types/node` fixado em `18.16.9` exato, sem range | APIs de Node modernas usadas em config/scripts não tipam |
| FE-DEP-09 | 🟡 | `package.json:5,71-73` | `scripts:{}` vazio; `workspaces:["packages/*"]` aponta para diretório com só `.gitkeep` | Nenhum entrypoint padronizado para CI/onboarding |
| FE-DEP-10 | ⚪ | commit inicial vs `package.json` atual | Salto de Angular 14→18 e React 17→18 sem `migrations.json` registrado | Sem rastro de como/quando a migração de major aconteceu |

<a id="frontend-inf"></a>
### Qualidade de infraestrutura

| ID | Sev | Local | Problema | Cenário de falha |
|---|---|---|---|---|
| FE-INF-01 | 🔴 | Reproduzido: `nx test painel-admin` | **Falha** com `TS5095` — `moduleResolution:"bundler"` no base conflita com `module:"commonjs"` do `tsconfig.spec.json` | Nenhum teste unitário do monorepo roda — suíte inteira inalcançável, sem CI para detectar |
| FE-INF-02 | 🟡 | `app.component.spec.ts:13-26` | Os únicos 2 testes existentes esperam `h1` e `title` que não existem mais no componente atual | Cobertura real do painel-admin = 0, mascarada por specs obsoletos do boilerplate |
| FE-INF-03 | 🟠 | `apps/rastreamento/project.json` | Sem target `test`; nenhum `jest.config.ts` nem arquivo `*.spec.tsx` no app | 726 linhas de React (incluindo `MapView`, com o XSS de FE-SEC-01) sem uma única asserção automatizada |
| FE-INF-04 | 🔴 | Reproduzido: `nx lint painel-admin` | **Crash** — `Cannot read properties of undefined (reading 'at')` na regra `@typescript-eslint/ban-ts-comment` | Lint do app principal é inutilizável; regras Angular nunca chegam a rodar |
| FE-INF-05 | 🟠 | Reproduzido: `nx lint rastreamento` | **Falha com 43 erros** (40 `no-explicit-any`, 2 `no-unused-vars`, 1 `prefer-const`) | Gate de qualidade vermelho; impossível ativar "falhar build no lint" sem limpar antes |
| FE-INF-06 | 🟡 | `apps/rastreamento/` sem `eslint.config.js` próprio | Nenhuma regra de React ativa (`eslint-plugin-react`/`react-hooks` nem estão no `package.json`) | Violações clássicas (key por índice em `TrackingDashboard.tsx:143`, deps de efeito) passam invisíveis |
| FE-INF-07 | ⚪ | `eslint.config.js:29-38,17-27` | Bloco de regras vazio; `enforce-module-boundaries` configurado para permitir tudo depender de tudo (`tags:[]` em todos os `project.json`) | Aparência de governança arquitetural sem impor limite algum |
| FE-INF-08 | 🟡 | `painel-admin-e2e/project.json`, `example.spec.ts:3-8` | Único teste E2E é placeholder do gerador, esperando `h1` "Welcome" que não existe mais na rota `/` | Cobertura funcional E2E real = 0, ainda roda contra 3 browsers desperdiçando tempo de CI |
| FE-INF-09 | 🟡 | Ausente | Não existe projeto e2e para `rastreamento` — o app com o XSS e o mapa não tem teste de integração algum | Regressões no fluxo de rastreamento público só são descobertas em produção |
| FE-INF-10 | 🟡 | `apps/rastreamento/project.json` (target `build`) | Sem bloco `configurations` (`production`/`development`) — ao contrário do `painel-admin` | `nx build rastreamento --configuration=production` não existe; bundle de 173 KiB não minificado, sem hash de conteúdo, vai para o "deploy" |
| FE-INF-11 | 🟡 | `.babelrc:9`, `tsconfig.app.json:7-11` | Nenhum type-check no pipeline do React — Babel apaga tipos sem validar | Campo inexistente em qualquer interface do repo (`delivery.numero_pedido`) compila e vira `undefined` na tela |
| FE-INF-12 | ⚪ | `tsconfig.app.json` (React) vs `tsconfig.json` (Angular) | Dois padrões de rigor de TypeScript no mesmo monorepo | Código movido do React para uma lib compartilhada não compilaria sob o padrão Angular |
| FE-INF-13 | 🟡 | Repositório inteiro | Sem Dockerfile, sem CI, `package.json` com `scripts:{}` | Nada impede que FE-INF-01/04/05 (test/lint quebrados) permaneçam quebrados indefinidamente — foi o que aconteceu |
| FE-INF-14 | 🟡 | `jest.config.ts` (`passWithNoTests:true`), sem `coverageThreshold` | Nenhum limiar de cobertura configurado | `nx test` "passa" mesmo em projetos sem teste algum, mascarando FE-INF-03 |
| FE-INF-15 | ⚪ | `error.log:1-2` (rastreado desde o commit inicial) | Artefato acidental de `git commit --amend` malsucedido, versionado na raiz | Sinaliza ausência de revisão de commit; `.gitignore` incompleto para `*.log` na raiz |
| FE-INF-16 | ⚪ | `AGENTS.md`/`CLAUDE.md` (idênticos byte-a-byte) | Documentação duplicada cobrindo só convenções de Nx — não menciona portas dos backends nem que test/lint estão quebrados | Onboarding não avisa sobre nenhum dos bloqueios reais do repositório |
| FE-INF-17 | 🟡 | `libs/api-contracts/src/openapi.yaml` (o próprio arquivo admite: "desatualizado há 2 anos") | Contrato documenta ~20% da superfície real da API chamada pelo código, sem validação automatizada | Documentação enganosa em vez de contrato confiável |

---

## Dívidas transversais (padrões repetidos nos 5 repositórios)

| Padrão | Onde aparece |
|---|---|
| **Zero CI/CD e Dockerfile de aplicação** (só o compose de banco no workspace) | Todos os 5 |
| **Credenciais hardcoded e versionadas** | frotas (`FR-SEC-02`), entregas (`EN-SEC-03`), notificações (`NT-SEC-01,02,03`), workspace (`WS-SEC-01`) |
| **Zero autenticação/autorização em todo endpoint de escrita** | frotas, entregas, notificações, frontend (`*-SEC-01`/`04`/`05`/`03`) |
| **Health checks decorativos** (retornam `UP` sem checar dependência real) | frotas (`FR-INF-04`), entregas (`EN-INF-02`), notificações (`NT-SEC-11`/`NT-INF-04`) |
| **Zero testes executáveis** | frotas (`FR-INF-01`), entregas (`EN-INF-01`), notificações (`NT-INF-01`), frontend (`FE-INF-01,03,04,05`) |
| **Logging não estruturado**, misturando `console.log`/`System.out.println`/`Console.WriteLine` com logger real | frotas (`FR-INC-11`), entregas (`EN-INF-05`), notificações (`NT-INF-05`) |
| **CORS wildcard** | frotas (`FR-SEC-05`), entregas (`EN-SEC-06`), notificações (`NT-SEC-04`) |
| **Sem rate limiting em nenhum serviço** | frotas (`FR-SEC-06`*), entregas (`EN-SEC-08`), notificações (`NT-INF-06`), frontend (`FE-SEC-05`) |
| **Sem paginação em nenhum endpoint de listagem** | frotas (`FR-ARQ-11`), entregas (`EN-INF-11`), notificações (`NT-SEC-05c`) |
| **Schema de banco sem fonte única de verdade** — Flyway desligado (frotas), `sequelize.sync()` (entregas), `EnsureCreated()` (notificações), scripts do workspace como 4ª fonte | `FR-INF-02`, `EN-ARQ-10`, `NT-INF-02`, `WS-ARQ-02` |
| **Mensagens de erro internas vazadas ao cliente** (`ex.Message`/`error.message`/stack) | frotas (`FR-SEC-07`), entregas (`EN-INC-02`, `EN-SEC-07`), notificações (`NT-SEC-09`), frontend (`FE-SEC-08`) |
| **PII (CNH, endereços, nomes) exposta sem controle de acesso** | frotas (`FR-SEC-03`), notificações (`NT-SEC-05b/11`), frontend (`FE-SEC-04`) |
