# Requisitos

Esta página consolida os requisitos funcionais e não funcionais do Wonder App, organizados por ator. Fonte: documento de requisitos do projeto.

## Propósito e escopo

O objetivo é especificar a evolução do sistema Wonder de uma arquitetura monolítica para um ecossistema de **microsserviços distribuídos**, cobrindo o desacoplamento de serviços, integração com LLMs, mensageria assíncrona e registro automático de operações de banco de dados.

---

## Requisitos funcionais — Cliente

| ID | Título | Prioridade |
|---|---|---|
| RF001 | Autenticação e Cadastro via Google OAuth2 | Alta |
| RF002 | Buscar e Filtrar Prestadores de Serviço (Catálogo) | Alta |
| RF003 | Agendar Serviço com Controle de Concorrência | Alta |
| RF004 | Consultar Assistente de IA Integrado (LLM) | Média/Alta |
| RF005 | Cancelar Serviço | Alta |
| RF006 | Receber Notificações e Lembretes | Alta |

**RF001 — Autenticação e Cadastro via Google OAuth2**
Como cliente ou prestador, quero acessar o sistema pela API do Google (OAuth2), para autenticar de forma segura delegando a um sistema externo. Critério: ao escolher "entrar com Google", o sistema processa a autenticação via API externa e redireciona ao painel correspondente.

**RF002 — Buscar e Filtrar Prestadores**
Como cliente, quero buscar e filtrar profissionais por categoria consumindo o microsserviço de Catálogo, para ver só as opções relevantes.

**RF003 — Agendar Serviço com Controle de Concorrência**
Como cliente, quero escolher prestador, horário e confirmar o agendamento, impedindo que dois clientes agendem o mesmo horário com o mesmo prestador. Critério: o agendamento é registrado com controle de conflitos e um evento é publicado no RabbitMQ para processamento assíncrono.

**RF004 — Consultar Assistente de IA**
Como cliente, quero consultar um assistente de IA integrado, para receber recomendações de serviços de beleza. Critério: o sistema chama a API de LLM externa e devolve sugestão contextualizada.

**RF005 — Cancelar Serviço**
Como cliente, quero cancelar um atendimento confirmado. Critério: o sistema atualiza o status e publica evento assíncrono para os serviços interessados.

**RF006 — Receber Notificações e Lembretes**
Como usuário, quero receber notificações automáticas de confirmação, cancelamento ou proximidade de agendamentos. Critério: o serviço de Notificação captura o evento em background e envia o alerta.

---

## Requisitos funcionais — Prestador

| ID | Título | Prioridade |
|---|---|---|
| RF007 | Cadastro e Perfil Profissional | Alta |
| RF008 | Gerenciar Serviços Oferecidos | Alta |
| RF009 | Definir Horários Disponíveis | Alta |
| RF010 | Cancelar agendamento de Cliente | Alta |

**RF007 — Cadastro e Perfil Profissional**
Como prestador, quero criar meu perfil com foto, endereço e serviços. Critério: após autenticar via Google Auth, ao informar os dados obrigatórios, o perfil é persistido no microsserviço de Catálogo.

**RF008 — Gerenciar Serviços Oferecidos**
Como prestador, quero cadastrar, editar e excluir serviços. Critério: em "Meus Serviços", posso adicionar/editar com nome, descrição e preço.

**RF009 — Definir Horários Disponíveis**
Como prestador, quero definir dias e horários de atendimento. Critério: o sistema só permite agendamentos dentro desses períodos.

**RF010 — Cancelar agendamento de Cliente**
Como prestador, quero cancelar ou propor reagendamento informando o motivo. Critério: o sistema atualiza o status e publica no RabbitMQ para o cliente ser notificado.

---

## Requisitos funcionais — Administrador

| ID | Título | Prioridade |
|---|---|---|
| RF011 | Gerenciar Categorias e Cadastros | Alta |
| RF011* | Consultar Relatórios de Auditoria | Alta |
| RF012 | Dashboard de Monitoramento, Automação e Alertas | Média/Alta |

**RF011 — Gerenciar Categorias e Cadastros**
Como administrador, quero um dashboard para gerenciar clientes, prestadores e categorias.

**RF011\* — Consultar Relatórios de Auditoria**
Como administrador, quero acessar relatórios de auditoria gerados automaticamente. Critério: posso visualizar logs de inserções, atualizações, exclusões e consultas com timestamp e identidade do usuário.

**RF012 — Dashboard de Monitoramento, Automação e Alertas**
Como administrador, quero monitorar backups, saúde do banco e receber alertas. Critério: o painel exibe status de backup, métricas nativas do PostgreSQL, permite disparar scripts de manutenção e exibe alertas para situações críticas.


---

## Requisitos não funcionais

| ID | Título | Prioridade | Detalhe |
|---|---|---|---|
| RNF001 | Arquitetura Distribuída e Virtualização | Alta | Serviços desacoplados, orquestrados em containers Docker/Docker Compose |
| RNF002 | Multi-linguagem | Alta | Python (FastAPI) no backend e JavaScript (React Native) no frontend |
| RNF003 | Mensageria e Assincronismo | Alta | Fila RabbitMQ para desacoplamento e processamento assíncrono |
| RNF004 | Auditoria e Logs Nativos | Alta | PostgreSQL configurado para registrar inserções, atualizações, exclusões e leituras com timestamp e identidade |
| RNF005 | Design Responsivo e Mobile-First | Alta | Interface mobile-first, funcional em mobile e desktop |
| RNF006 | Automação e Backup | Alta | Backup automático da base de dados e scripts de manutenção |
| RNF007 | Integrações de Inteligência Distribuída | Alta | Integração de LLMs via API, com inferência distribuída para nós remotos |

Veja como cada requisito se materializa em serviço/componente na [Visão Funcional](Visao-Funcional) e no [C4 — Container](C4-Container).
