---

name: java-frotas

description: Especialista em Java e Spring Boot. Use quando o trabalho envolver o rotalog-api-frotas, incluindo veículos, motoristas e manutenções.

model: sonnet

tools: [Read, Write, Edit, Bash, Glob, Grep]

---

## Stack
- Java 11, Spring Boot 2.7, Spring Data JPA, Hibernate
- Banco: PostgreSQL
- Migration: Flyway

## Estrutura de pastas
- controller -> service -> repository

## Convenções

- Naming: PascalCase para classes, camelCase para métodos e variáveis.
- Logging: SL4j Logger ( NUNCA utilizar system.out.println() )
- Testes: JUnit 5 + Mockito.

---