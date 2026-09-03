# Auditoria, Monitoramento e Backup

Esta página reúne os três pilares de operação do Wonder App: rastreabilidade de dados, observabilidade da infraestrutura e resiliência via backup — atendendo RNF004, RNF006, RF011\* e RF012.

## Auditoria (RNF004, RF011\*)

Cada um dos quatro bancos PostgreSQL (`db_auth`, `db_catalogo`, `db_agendamentos`, `db_notificacoes`) possui uma tabela **`logs_auditoria`**, alimentada por **triggers PL/pgSQL nativos** — sem exigir código adicional na aplicação. Cada log registra:

- `usuario_id` (referência externa)
- `operacao` (INSERT/UPDATE/DELETE)
- `tabela_afetada`
- `dados_antigos` e `dados_novos` (JSONB)
- `data_hora`

Validado na **Sprint 4**, com cobertura confirmada para todos os fluxos de INSERT/UPDATE/DELETE. O **Admin Service** expõe um endpoint unificado de relatórios de auditoria (adicionado na **Sprint 7**), consultando os quatro bancos via HTTP Client sem quebrar o isolamento de cada microsserviço.

## Monitoramento e Alertas (RF012)

Implementado na **Sprint 8**:

- Dashboard com `pg_stat_statements`, métricas nativas do PostgreSQL.
- Sistema de alertas para conexões, locks e dead tuples.
- Thresholds calibrados via variáveis de ambiente (ajustados para um cenário de demonstração acadêmica, com poucas conexões simultâneas).

No C4 Container, essa relação aparece como "Relação Secundária (Monitoramento/Backup/Eventos)" entre o **Admin Service** e os quatro bancos de dados.

## Backup (RNF006)

Implementado na **Sprint 7**:

- Container dedicado (`postgres:16-alpine`) executando `scripts/backup.sh`, que roda `pg_dump` nos quatro bancos isolados.
- Scripts de manutenção — **Vacuum** e **Reindex** — via `scripts/maintenance.sh`, mapeando as tabelas de cada banco (ver [Modelo Lógico](Modelo-Logico)).
- O Admin Service monitora o status desses backups e exibe no dashboard.

> **Nota:** o container de Backup Job aparece no [C4 — Container](C4-Container), mas ainda não está representado explicitamente na [Visão de Desenvolvimento](Visao-de-Desenvolvimento) (hoje só `sql/`, `scripts/` e `docker-compose.yml` aparecem no bloco "Dados, Scripts e Infra Local"). Sugestão para a próxima revisão: adicionar o Backup Job como elemento explícito nesse bloco.

## CI/CD

Implementado na **Sprint 8**: pipeline no GitHub Actions com jobs de lint, build e health check, cobrindo a camada `.github/workflows/` da [Visão de Desenvolvimento](Visao-de-Desenvolvimento).

## Rastreabilidade

| Tema | Requisito | Sprint de entrega |
|---|---|---|
| Logs nativos de auditoria | RNF004 | 4 |
| Relatórios de auditoria (endpoint unificado) | RF011\* | 7 |
| Backup automático | RNF006 | 7 |
| Scripts de manutenção (Vacuum/Reindex) | RNF006 | 7 |
| Dashboard de monitoramento e alertas | RF012 | 8 |
| Pipeline CI/CD | RNF001 (suporte) | 8 |
