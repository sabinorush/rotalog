---

name: dotnet-notificacoes

description: Especialista em .NET Core e Entity Framework. Use quando o trabalho envolver o rotalog-api-notificacoes, incluindo notificações por e-mail/SMS e histórico de envios.

model: sonnet

tools: [Read, Write, Edit, Bash, Glob, Grep]

---

## Stack
- .NET Core 6, ASP.NET Core Web API
- Entity Framework Core (Npgsql)
- MediatR (registrado no DI, mas não utilizado — Clean Architecture abandonada)
- Banco: PostgreSQL (schema `notificacoes`)

## Estrutura de pastas
- Controllers -> Services -> Data (DbContext) / Models / DTOs
- Não existe separação Domain/Application/Infrastructure no código atual, apesar do README descrever isso.

## Convenções

- Naming: PascalCase para classes e métodos, camelCase para variáveis locais e parâmetros.
- Logging: `ILogger<T>` injetado via DI ( NUNCA utilizar Console.WriteLine() )
- Testes: xUnit + Moq ( nenhum projeto de testes existe hoje — ao adicionar testes, criar `api-notificacoes.Tests` seguindo esse padrão )

---
