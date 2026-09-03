# Wonder App

Plataforma de agendamento online de serviços de beleza, desenvolvida como Projeto Integrador da disciplina **Seminário de Orientação ao Projeto de Desenvolvimento de Sistema Distribuído** — IFRN, 5º período ADS Vespertino, campus Pau dos Ferros/RN.

O sistema conecta **clientes** e **prestadores** de serviços de beleza: prestadores mantêm uma vitrine digital com categorias, serviços e horários; clientes buscam, filtram e agendam diretamente pela plataforma. Um **administrador** aprova prestadores, gerencia categorias e acompanha auditoria, monitoramento e alertas do sistema.

O Wonder evoluiu de uma arquitetura monolítica para um **ecossistema de microsserviços distribuídos**, com mensageria assíncrona, auditoria automática via triggers de banco e integração com LLM para recomendações e relatórios.

**Autores:** João Roque Pereira Neto e Lwkas Lwhan Gonçalves Carvalho

---

## Navegação

### Requisitos e processo
- [Requisitos do sistema](Requisitos)
- [Processo de software](Processo-de-Software)

### Visões arquiteturais (Rozanski & Woods)
- [Visão Funcional](Visao-Funcional)
- [Visão de Desenvolvimento](Visao-de-Desenvolvimento)

### Modelo C4
- [C4 — Contexto](C4-Contexto)
- [C4 — Container](C4-Container)
- [C4 — Componentes](C4-Componentes)

### Modelagem complementar
- [Casos de Uso](Casos-de-Uso)
- [Diagrama de Classes](Diagrama-de-Classes)
- [Modelo Lógico de Dados](Modelo-Logico)

### Operação
- [Auditoria, Monitoramento e Backup](Auditoria-Monitoramento-e-Backup)

---

## Stack tecnológica

| Camada | Tecnologia |
|---|---|
| Backend | Python + FastAPI (um serviço por domínio) |
| Frontend mobile | React Native + Expo |
| Banco de dados | PostgreSQL 16 (um banco isolado por microsserviço) |
| Mensageria | RabbitMQ (fila `wonder.eventos`) |
| IA | LLM via OpenRouter (recomendações e relatórios operacionais) |
| Autenticação | Google OAuth2 |
| Orquestração | Docker + Docker Compose |
| CI/CD | GitHub Actions (lint, build, health check) |
| Backup | Job dedicado com `pg_dump` para os quatro bancos |

## Visão geral da arquitetura

O sistema é composto por seis microsserviços (**Auth**, **Catálogo**, **Agendamentos**, **Notificações**, **IA**, **Admin**), um **API Gateway** como ponto único de entrada, um **app mobile** unificado para os três perfis de usuário, e quatro bancos PostgreSQL isolados. A comunicação síncrona acontece via REST/JSON roteado pelo Gateway; a comunicação assíncrona (criação e cancelamento de agendamento, notificações) acontece via RabbitMQ.

Cada banco possui uma tabela `logs_auditoria` alimentada por triggers nativos do PostgreSQL, cobrindo inserções, atualizações e exclusões com timestamp e identidade do usuário — sem exigir código adicional na aplicação.

Para o detalhamento de cada corte da arquitetura, use os links de navegação acima.

## Deploy em EC2

O ambiente de demonstração foi executado em uma instância EC2 Ubuntu, utilizando Docker Compose.

Fluxo utilizado:
1. Clonar o repositório.
2. Configurar o arquivo `.env`.
3. Executar `docker compose build`.
4. Executar `docker compose up -d`.
5. Validar o Gateway em `GET /health`.

O acesso externo é feito pela porta `8000` do Gateway.
