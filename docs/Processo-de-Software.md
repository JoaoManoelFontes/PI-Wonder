# Processo de Software

Resumo do Relatório Final do Processo de Software do Wonder App — como o processo híbrido **Scrum + Kanban** foi conduzido da Entrega 01 (fundação arquitetural) até a Entrega 02 (fechamento de funcionalidades, frontend mobile, infraestrutura de produção e documentação final).

**Equipe:** João Roque Pereira Neto e Lwkas Lwhan Gonçalves Carvalho — 5º período ADS, IFRN campus Pau dos Ferros/RN.

## Visão geral do processo

Ciclo de vida incremental combinando **Scrum** (sprints semanais de 7 dias, planejamento toda segunda-feira) e **Kanban** (fluxo visual no GitHub Projects). Cada sprint entregou uma fatia funcional testável de forma independente. Responsabilidades divididas por domínio técnico (backend, frontend, integração, infraestrutura), com **revisão cruzada obrigatória via Pull Request**.

## Sprints

| Sprint | Objetivo | Principais entregas |
|---|---|---|
| 1 | Estrutura e Documentação | Histórias de usuário, casos de uso, diagramas C4 (Contexto/Container), modelo lógico com tabelas de auditoria |
| 2 | Fundação e Autenticação | Docker Compose completo, API Gateway, Auth Service com Google OAuth2 |
| 3 | Core do Negócio e Mensageria | Catálogo e Agendamentos, integração inicial com RabbitMQ |
| 4 | IA e Auditoria | Conexão com IA (Gemini), validação dos triggers PL/pgSQL de auditoria |
| 5 | Buffer de Integração (Entrega 01) | Correção de bugs de integração, revisão de documentação, ensaio de apresentação |
| 6 | Regras de Negócio Faltantes e Base do Mobile | Horário de funcionamento e avaliação no Catálogo, busca/filtro, evento de cancelamento, endpoints admin de usuários, base do app React Native/Expo |
| 7 | IA, Relatórios, Backup e Fluxo do Cliente | Endpoint `/ai/sugestoes` ponta a ponta, relatórios de auditoria via serviço admin, backup automático (`pg_dump`) dos 4 bancos, scripts de manutenção (Vacuum/Reindex), login Google + JWT no mobile, fluxo Catálogo/Busca/Agendamento no app |
| 8 | Monitoramento, Alertas, CI/CD e Fluxo do Prestador | Dashboard com `pg_stat_statements`, alertas de conexões/locks/dead tuples, pipeline CI/CD (GitHub Actions), Wiki do projeto, fluxo do Prestador (portfólio, horários, agenda), telas de notificações e chat com IA |
| 9 | Documentação Final e Visões Arquiteturais | Relatório final, contrato de endpoints, README reformulado, C4 de Componentes, fluxogramas, diagrama de pacotes |
| Semanas 5–6 | Buffer Final | Testes end-to-end, correção de bugs de integração mobile/Docker, ensaio final |

## Impedimentos por sprint (resumo)

- **Sprint 1** — realinhamento entre requisitos funcionais e arquitetura de microsserviços antes do modelo lógico final.
- **Sprint 2** — complexidade na integração do fluxo OAuth2 com o Gateway e na rede interna do Docker Compose.
- **Sprint 3** — garantir que falhas no RabbitMQ não comprometessem a persistência da operação principal.
- **Sprint 4** — cobertura dos triggers de auditoria para todos os fluxos de INSERT/UPDATE/DELETE sem código adicional na aplicação.
- **Sprint 6** — coordenação entre fechamento dos contratos de API do backend e início do consumo pelo frontend mobile.
- **Sprint 7** — isolamento do serviço admin, que conecta simultaneamente aos quatro bancos, evitando acoplamento indevido.
- **Sprint 8** — calibração de thresholds de alerta realistas para um ambiente de demonstração acadêmica.
- **Sprint 9** — manter a documentação sincronizada com o código real após múltiplas sprints de mudança rápida.

## Kanban no GitHub Projects

Fluxo: **Ready → In Progress → In Review → Done**

- **Ready** — tarefas detalhadas, prontas para a sprint corrente.
- **In Progress** — trabalho em desenvolvimento ativo.
- **In Review** — código aguardando aprovação via Pull Request do outro integrante; nenhuma tarefa avança sem revisão cruzada.
- **Done** — só recebe itens que atendem à Definição de Pronto: funcionalidade íntegra, PR aprovado, execução correta no Docker Compose, testes manuais via Postman/Insomnia e geração dos registros de auditoria esperados.

## Desafios técnicos enfrentados

- **Integração entre microsserviços** — manter contratos de API estáveis entre cinco serviços independentes mais Gateway e Admin, especialmente contratos internos como a consulta de Agendamentos ao histórico do usuário para o endpoint de IA.
- **Configuração do Docker Compose** — orquestrar frontend, backend, quatro bancos, RabbitMQ, backup e monitoramento em um único ambiente, com atenção às variáveis de ambiente, rede interna e `depends_on`.
- **Comunicação assíncrona via RabbitMQ** — publicar eventos após o commit no banco (criação e cancelamento de agendamento), garantindo que uma indisponibilidade do RabbitMQ não comprometesse a operação principal.

## Lições aprendidas

- Revisão cruzada obrigatória acelerou o entendimento compartilhado da base de código, mitigando o risco de conhecimento concentrado em uma única pessoa.
- Processos leves funcionam melhor em equipes reduzidas: Scrum+Kanban híbrido permitiu adaptar o ritmo semanal à realidade acadêmica sem sacrificar rastreabilidade.
- O que a equipe faria diferente: reservar mais tempo, desde as sprints iniciais, para definir o design e mapear regras e situações de borda — evitando a "corrida contra o tempo" no fim do projeto.
