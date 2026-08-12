# ADR-0001: Hardening de segurança e decomposição de `NotificacaoService`

## Status

**Proposto** — 2026-08-12. Documento apenas de análise/decisão; nenhuma alteração de código foi feita.

Mesmo formato e escopo de decisão usados nas ADRs equivalentes já criadas para os outros dois backends:
- `rotalog-api-frotas/docs/adr/0001-refatoracao-veiculoservice.md`
- `rotalog-api-entregas/docs/adr/0001-hardening-auth-e-refatoracao-entregas.md`

## Contexto

`DIVIDAS-TECNICAS.md` (seção 4) audita `rotalog-api-notificacoes` com 41 débitos (7 arquitetura, 8 inconsistência, 11 segurança, 6 dependências, 9 infraestrutura). Dois pontos merecem destaque antes do diagnóstico:

- O item #4 do "Top 10 críticos" cross-repo do documento é sobre este serviço: `api-notificacoes` é um **open relay anônimo** — qualquer um envia e-mail/SMS com as credenciais da empresa (`NT-SEC-05`).
- `NotificacaoService.cs` (369 linhas) é citada nas "Correções à documentação do projeto" do próprio `DIVIDAS-TECNICAS.md`: a "Clean Architecture abandonada"/"MediatR god handlers" descrita no README/`CLAUDE_CONTEXT.md` é imprecisa — MediatR está registrado e nunca usado (zero `IRequest`/`IRequestHandler` no assembly), e a god class real é este service comum, não um handler MediatR.

## Validação do `DIVIDAS-TECNICAS.md` contra o código atual

Antes de gerar a ADR, os arquivos abaixo foram lidos integralmente e cada `NT-*` citado nas seções seguintes foi conferido linha a linha contra o código real (não apenas contra o resumo do documento):

`Program.cs`, `Services/NotificacaoService.cs`, `Controllers/NotificacoesController.cs`, `Controllers/HealthController.cs`, `Models/Notificacao.cs`, `Data/NotificacoesDbContext.cs`, `appsettings.json`, `api-notificacoes.csproj`.

**Resultado: nenhuma divergência encontrada.** Todos os `arquivo:linha` citados em `DIVIDAS-TECNICAS.md` para este repositório continuam corretos no código atual — diferente do que o próprio documento registra para outros repositórios (ex.: a seção "Correções à documentação do projeto" aponta imprecisões no README do frontend e no `CLAUDE_CONTEXT.md`). Para `api-notificacoes`, o mapeamento pode ser tratado como fonte confiável sem ressalvas adicionais.

## Diagnóstico — Arquitetura

| ID | Sev | Local (verificado) | Problema |
|---|---|---|---|
| `NT-ARQ-01` | ⚪ | `Program.cs:38` | `AddMediatR(typeof(Program))` registrado; zero `IRequest`/`IRequestHandler` no assembly |
| `NT-ARQ-02` | 🟠 | `Program.cs:41`, `NotificacoesController.cs:20,24` | `NotificacaoService` injetado como classe concreta, sem interface — impossível mockar em teste unitário |
| `NT-ARQ-03` | 🟠 | `NotificacaoService.cs:19-369` | God class de 369 linhas: CRUD + orquestração de envio + transporte e-mail/SMS + motor de template + retry + estatísticas, tudo em uma única classe (confirmado: 11 métodos públicos/privados) |
| `NT-ARQ-04` | 🔴 | `HealthController.cs:56-71` (frotas), `73-87` (entregas) | Abre `NpgsqlConnection` cru para os schemas de outros microsserviços, trocando `SearchPath` na própria connection string |
| `NT-ARQ-05` | 🟡 | `NotificacoesController.cs:87-93` vs. `NotificacaoService.cs:82-85` | Validação de obrigatoriedade duplicada e já divergente: o controller exige `Mensagem` OU `Variaveis`; o service só valida `Tipo`/`Destinatario` |
| `NT-ARQ-06` | 🟡 | `NotificacaoService.cs:131-132` | `CriarNotificacao` chama `TentarEnviar` de forma síncrona, dentro da própria requisição HTTP do `POST` |
| `NT-ARQ-07` | 🟡 | `NotificacoesDbContext.cs:24` | `DbSet<ConfiguracaoNotificacao> Configuracoes` mapeado; nenhuma referência a `_context.Configuracoes` em todo `NotificacaoService.cs` (confirmado) — a entidade de opt-out/canal preferido nunca é consultada |

## Diagnóstico — Segurança

| ID | Sev | Local (verificado) | Problema |
|---|---|---|---|
| `NT-SEC-01` | 🔴 | `appsettings.json:10` | Connection string com senha em texto claro (`rotalog123`), usuário admin compartilhado entre os 3 schemas |
| `NT-SEC-02` | 🔴 | `appsettings.json:16` | Senha SMTP hardcoded, remetente `noreply@rotalog.com` |
| `NT-SEC-03` | 🔴 | `appsettings.json:19` | API key de SMS hardcoded |
| `NT-SEC-04` | 🟠 | `Program.cs:44-52` | CORS `AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader()` |
| `NT-SEC-05` | 🔴 | `Program.cs:76`, nenhum `[Authorize]` em nenhum controller | Zero autenticação em 100% dos endpoints — `POST /api/notificacoes` (o próprio XML doc do endpoint, `NotificacoesController.cs:80`, admite ser "chamado pelo api-frotas e api-entregas", mas nada impede qualquer outro chamador) é open relay; `GET /api/notificacoes` devolve todo o histórico com PII sem paginação |
| `NT-SEC-06` | 🟠 | `HealthController.cs:101-106` | Endpoint público expõe `EmailSettings:SmtpServer`/`SmsSettings:ApiUrl` |
| `NT-SEC-07` | 🟠 | `HealthController.cs:61,77`, `appsettings.json:10` | Health check acessa `frotas`/`entregas` com o mesmo usuário admin da connection string principal |
| `NT-SEC-08` | 🟡 | `Program.cs:74` (comentada), `appsettings.json:8` | `UseHttpsRedirection()` desativada; `AllowedHosts:"*"` |
| `NT-SEC-09` | 🟡 | 7 blocos `catch (Exception ex)` em `NotificacoesController.cs` (linhas 46, 69, 102, 124, 143, 162, 184) | `detalhes = ex.Message` devolvido ao cliente em todos os endpoints |
| `NT-SEC-10` | 🟡 | `Program.cs:63-68` | `UseSwagger()`/`UseSwaggerUI()` sem guarda `if (app.Environment.IsDevelopment())` |
| `NT-SEC-11` | 🟠 | `NotificacaoService.cs:127-128` (criação), `288-295` (e-mail), `317-319` (SMS) | Destinatário, assunto e corpo da mensagem logados em texto puro via `_logger.LogInformation` |

## Diagnóstico — Inconsistência de código

| ID | Sev | Local (verificado) | Problema |
|---|---|---|---|
| `NT-INC-01` | ⚪ | `NotificacoesController.cs:159-160` | `ListarTemplates` devolve a entidade EF `TemplateNotificacao` crua; os demais endpoints usam `NotificacaoResponse` |
| `NT-INC-02` | 🔴 | `NotificacaoService.cs:113` (grava sem normalizar) vs. `239-249` (compara com `==` exato) | `Canal` salvo como veio do request; `TentarEnviar` só reconhece `"email"`/`"sms"` literalmente — `"EMAIL"` cai no `else` e lança `NotSupportedException` |
| `NT-INC-03` | 🟡 | `NotificacaoService.cs:47` (`Tipo`, sem normalização), `50` (`.ToUpper()`), `53` (`.ToLower()`) | Normalização de filtro assimétrica entre campos |
| `NT-INC-04` | 🔴 | `NotificacaoService.cs:91-108` | Se nenhum template casa `Tipo`+`Canal`, usa `request.Mensagem`, que pode ser `""` — notificação persiste e é marcada `ENVIADO` com corpo vazio |
| `NT-INC-05` | 🟡 | `NotificacoesController.cs` (7 blocos genéricos), `HealthController.cs:89-107` (sempre `Ok()`, mesmo com `status:"DEGRADED"`) | Tratamento de exceção inconsistente entre controllers e dentro do próprio `NotificacoesController` (só `Criar` diferencia `ArgumentException` → 400; os outros 5 endpoints devolvem 500 para qualquer erro, incluindo erros de validação) |
| `NT-INC-06` | 🟡 | Todos os métodos `async` de `Controllers/` e `Services/` | Nenhuma assinatura recebe `CancellationToken` |
| `NT-INC-07` | ⚪ | `HealthController.cs:16,37` | `[HttpGet("/api/health")]` com rota absoluta sobrescreve o `[Route("api/[controller]")]` da classe |
| `NT-INC-08` | 🟡 | `Notificacao.cs:25,30,48` | `Tipo`/`Canal`/`Status` como `string` livre; o próprio FIXME da classe (linha 11) admite que deveria ser enum |

## Diagnóstico — Dependências e infraestrutura

| ID | Sev | Problema (verificado em `api-notificacoes.csproj`) |
|---|---|---|
| `NT-DEP-01` | 🟠 | `net6.0` — suporte encerrado em 12/11/2024 |
| `NT-DEP-02` | 🟠 | `Npgsql.EntityFrameworkCore.PostgreSQL 6.0.8`, anterior à correção `CVE-2024-32655` |
| `NT-DEP-03` | 🟡 | EF Core / `.Design` em `6.0.12` (dez/2022) |
| `NT-DEP-04` | ⚪ | `MediatR 11.1.0` + extensão DI — dependências mortas (ver `NT-ARQ-01`) |
| `NT-DEP-05` | 🟡 | `Swashbuckle.AspNetCore 6.5.0` desatualizado, relevante porque Swagger é servido em produção (`NT-SEC-10`) |
| `NT-DEP-06` | 🟡 | Sem pacote de health check, Serilog, FluentValidation ou Polly — confirmado pelos comentários `<!-- FIXME -->`/`<!-- TODO -->` no próprio `.csproj` (linhas 17-21) |

| ID | Sev | Problema (verificado) |
|---|---|---|
| `NT-INF-01` | 🟠 | Zero projeto de teste no repositório; agravado por `new Random()` embutido no envio (`NotificacaoService.cs:301,325`) — testes seriam não-determinísticos por construção mesmo se o projeto existisse |
| `NT-INF-02` | 🔴 | `Program.cs:87` usa `EnsureCreated()`; falha de conexão só é logada via `Console.WriteLine` (linha 92), app continua subindo |
| `NT-INF-03` | 🟠 | `NotificacoesDbContext.OnModelCreating` (linhas 33-39, só comentários `TODO`) não define nenhum índice — `Models/Notificacao.cs` também não usa Fluent API |
| `NT-INF-04` | 🟠 | `HealthController` abre conexão nova a cada request para 3 bancos, sem cache/timeout |
| `NT-INF-05` | 🟡 | `Program.cs:88,92,97-112` usa `Console.WriteLine`; `Services`/`Controllers` usam `ILogger<T>` — dois mecanismos de log no mesmo processo |
| `NT-INF-06` | 🟡 | Sem rate limiting; `POST /api/notificacoes` e `POST /api/notificacoes/processar` são anônimos e sem limite |
| `NT-INF-07` | 🟡 | Sem Dockerfile/CI no repositório |
| `NT-INF-08` | ⚪ | `obj/Debug/net6.0/*.cs` rastreados no git apesar de `.gitignore` conter `obj/` (confirmado: 4 arquivos gerados aparecem no `Glob` deste repositório) |
| `NT-INF-09` | 🟠 | `NotificacaoService.TentarEnviar` (linhas 231-273) sem backoff exponencial nem circuit breaker; `ProcessarPendentes` (linhas 206-223) sem lock distribuído — duas chamadas concorrentes a `POST /processar` processam o mesmo lote de até 50 notificações e enviam duplicado |

## Achados adicionais (fora dos IDs já catalogados)

| # | Local | Observação |
|---|---|---|
| A | `Models/Notificacao.cs:54` | `MaxTentativas` tem *default* `3` fixado na entidade EF, não em configuração — mudar a política de retentativa (inclusive por canal, já que SMS falha "20%" e e-mail "10%" nas simulações) exige alterar a entidade e recompilar |
| B | `NotificacaoService.cs:148-151` (`ReenviarNotificacao`) | `NovoCanal` do corpo da requisição é gravado sem validação contra os únicos dois canais realmente suportados (`"email"`/`"sms"`, hardcoded em `TentarEnviar:239-249`) — um `reenvio` pode introduzir um canal inválido que `Criar` também não bloqueia, e a falha só aparece depois, como `NotSupportedException` capturada silenciosamente dentro de `TentarEnviar` |
| C | `NotificacoesController.cs:80` (XML doc) | O próprio comentário do endpoint mais sensível ("chamado pelo api-frotas e api-entregas") documenta um contrato de confiança que o código não aplica — reforça a leitura de `NT-SEC-05` como risco real, não teórico |
| D | `EnviarEmail`/`EnviarSms` (`NotificacaoService.cs:280-329`) | Falha simulada por `new Random()` (10% e-mail, 20% SMS) está hardcoded no meio do "envio"; qualquer integração real de SMTP/SMS vai precisar remover essa simulação do mesmo lugar onde a lógica de negócio de retry vive — acoplamento a mais para desfazer quando o envio real for implementado |

## Decisão proposta (não implementada)

**Segurança** (prioridade mais alta, por concentrar 4 dos 5 itens 🔴 do repositório):
- Mover connection string, senha SMTP e API key de SMS para *secrets manager*/variáveis de ambiente — nunca versionadas.
- Adicionar autenticação real (ex.: o mesmo esquema que vier a ser adotado em `rotalog-api-entregas`, ver ADR irmã) e `[Authorize]` em todos os controllers, com uma exceção deliberada e documentada para `GET /api/health`.
- Restringir CORS a origens conhecidas.
- Guardar `UseSwagger()`/`UseSwaggerUI()` atrás de `IsDevelopment()`.
- Reativar `UseHttpsRedirection()`.
- Parar de devolver `ex.Message` ao cliente; padronizar um envelope de erro genérico nos 7 endpoints.
- `HealthController`: parar de abrir conexão direta aos schemas de `frotas`/`entregas` — substituir por chamada HTTP aos health checks desses serviços (com timeout) ou remover a checagem cross-serviço, mantendo apenas o banco próprio.

**Arquitetura / decomposição de `NotificacaoService`**:
- Extrair uma interface (`INotificacaoService`) — pré-requisito técnico para testes, resolve `NT-ARQ-02` e viabiliza `NT-INF-01`.
- Separar em: `NotificacaoService` (CRUD/orquestração — `ListarNotificacoes`, `BuscarPorId`, `CriarNotificacao`, `ReenviarNotificacao`), `TemplateService` (`AplicarTemplate`, `ListarTemplates`), um `ICanalSender` por canal (`EmailSender`, `SmsSender`) implementando uma interface comum — isso também é o ponto de extensão natural para trocar a simulação (achado D) por integração real de SMTP/SMS sem tocar no restante da classe.
- Resolver `ConfiguracaoNotificacao` morta (`NT-ARQ-07`): ou passa a ser consultada antes de enviar (respeitando opt-out), ou é removida do `DbContext` — hoje sugere uma funcionalidade que não existe.
- Introduzir enums para `Tipo`/`Status`/`Canal` (`NT-INC-08`), resolvendo de raiz `NT-INC-02`/`NT-INC-03`.
- Mover `MaxTentativas` (achado A) para configuração, possivelmente por canal.

**Infraestrutura**:
- Substituir `EnsureCreated()` por migrations reais do EF Core.
- Adicionar índices via Fluent API (`Status`, `Tipo`, `DataCriacao`) e unique constraint em `Template(Tipo, Canal)`.
- Implementar `IHealthCheck` nativo do ASP.NET Core em vez do controller artesanal.
- Adicionar lock distribuído (ou mover `ProcessarPendentes` para um job agendado fora do request HTTP, como o próprio FIXME do controller já sugere) antes de expor `/processar` sem proteção contra concorrência.
- Criar projeto `api-notificacoes.Tests` (xUnit + Moq, conforme convenção do agente `dotnet-notificacoes`) — depende da extração de interface acima para ser eficaz.

## Alternativas consideradas

1. **Corrigir só a segurança (`NT-SEC-*`) e adiar a decomposição da god class** — considerada como *fatiamento* de execução, não como alternativa definitiva: dá o maior ganho de risco por menor esforço, mas não resolve a testabilidade (`NT-ARQ-02`/`NT-INF-01`), que depende da extração de interface de qualquer forma.
2. **Adotar MediatR de verdade** (já que está registrado) para separar `Command`/`Query` em vez de uma camada de serviço decomposta — rejeitada por ora: adicionaria uma segunda mudança arquitetural grande simultânea à correção de segurança, sem necessidade imediata; a decisão registrada aqui é remover a dependência morta (`NT-DEP-04`) ou adotá-la deliberadamente, não deixá-la registrada e inerte.
3. **Implementar envio real de e-mail/SMS já nesta iniciativa** — rejeitada como parte desta ADR: é uma decisão de produto/custo (provedor, orçamento de SMS) fora do escopo de uma refatoração de dívida técnica; a decisão aqui é apenas preparar a extensibilidade (achado D) para quando isso for decidido.

## Consequências

**Positivas**: fecha o open relay documentado como item #4 do Top 10 crítico do workspace; elimina exposição de credenciais de 3 sistemas externos (banco, SMTP, SMS); torna `NotificacaoService` testável e extensível para envio real; reduz o acoplamento de `HealthController` aos schemas de `frotas`/`entregas`.

**Negativas / riscos**:
- Adicionar autenticação real quebra os dois únicos consumidores conhecidos e documentados (`api-frotas`, `api-entregas`, conforme achado C) — a implementação precisa incluir a atualização desses dois clientes na mesma janela de deploy, não depois.
- Trocar o acesso direto ao schema de `HealthController` por chamada HTTP introduz uma nova dependência de rede síncrona — se implementada sem timeout, reproduz o mesmo problema já registrado em `FR-ARQ-03`/`EN-ARQ-04` nos outros dois serviços.
- Extrair `ICanalSender` sem ainda ligar um provedor real mantém o comportamento "fake" — importante comunicar que esta ADR resolve arquitetura e segurança, não implementa envio de e-mail/SMS de verdade.

**Nota sobre o caráter didático do repositório**: `NT-SEC-05` (open relay) é o item #4 do Top 10 crítico cross-repo — junto com o `auth.js` de `api-entregas` (itens #1/#2) e as credenciais hardcoded (item #3, presentes também aqui), são provavelmente os três exemplos mais usados no curso para ensinar falhas de autenticação/segredo. Mesma ressalva já registrada nas duas ADRs irmãs: antes de executar qualquer item desta ADR, vale decidir se o estado atual deve ser preservado (tag/branch) para uso futuro no curso.

## Referências

- `DIVIDAS-TECNICAS.md`, seção 4 (rotalog-api-notificacoes) — `NT-ARQ-01` a `07`, `NT-INC-01` a `08`, `NT-SEC-01` a `11`, `NT-DEP-01` a `06`, `NT-INF-01` a `09`.
- `rotalog-api-notificacoes/Program.cs`, `Services/NotificacaoService.cs`, `Controllers/NotificacoesController.cs`, `Controllers/HealthController.cs`, `Models/Notificacao.cs`, `Data/NotificacoesDbContext.cs`, `appsettings.json`, `api-notificacoes.csproj` — lidos integralmente para esta análise.
- `.claude/agents/dotnet-notificacoes.md` — convenções de stack (`ILogger<T>`, xUnit+Moq, `Controllers -> Services -> Data/Models`) usadas como referência para a arquitetura alvo proposta.
- ADRs irmãs no mesmo formato: `rotalog-api-frotas/docs/adr/0001-refatoracao-veiculoservice.md`, `rotalog-api-entregas/docs/adr/0001-hardening-auth-e-refatoracao-entregas.md`.
