# Escopo e Funcionalidades

Esta página resume as funcionalidades do Wonder App organizadas por ator, com base no [Diagrama de Casos de Uso](Diagramas), e os requisitos não funcionais que orientam decisões de arquitetura e infraestrutura.

## Atores do Sistema

O Wonder possui três atores principais, além de integrações com sistemas externos do Google.

### Cliente

- **Autenticar via Google OAuth2** — login seguro sem necessidade de senha própria.
- **Buscar/Filtrar Prestadores** — localizar profissionais por nome e categoria de serviço.
- **Agendar Serviço** — selecionar serviço, data e horário disponível junto a um prestador.
- **Cancelar Agendamento** — desmarcar um horário já reservado, disparando notificação assíncrona.
- **Visualizar Notificações** — acompanhar confirmações, cancelamentos e lembretes.
- **Avaliar Prestador** — deixar uma avaliação vinculada a um agendamento concluído.
- **Consultar Assistente de IA** — pedir sugestões de serviços/prestadores a um assistente baseado em LLM (Google Gemini).

### Prestador

*(herda também as ações do Cliente, podendo agendar e ser agendado no mesmo ecossistema)*

- **Completar Perfil / Cadastrar Estabelecimento** — dados do salão/profissional exibidos na vitrine digital.
- **Gerenciar Serviços** — criar, editar e remover os serviços oferecidos, com preços.
- **Definir Horários Disponíveis** — configurar a agenda de funcionamento consumida pelos clientes na busca.
- **Sincronizar Google Calendar** — manter a agenda de atendimentos alinhada ao Google Calendar (integração externa).

### Administrador

- **Gerenciar Usuários e Categorias** — alterar papéis de usuário (`cliente`/`prestador`/`admin`) e manter a taxonomia de categorias do catálogo.
- **Consultar Relatórios de Auditoria** — visualizar logs de INSERT/UPDATE/DELETE agregados dos quatro bancos do sistema.
- **Dashboard de Monitoramento** — acompanhar métricas de saúde do banco (queries lentas, conexões ativas, dead tuples) via `pg_stat_statements`.

### Sistemas Externos

| Sistema | Papel no Wonder |
|---|---|
| Google Auth API | Autenticação OAuth2 de clientes e prestadores |
| Google Calendar API | Sincronização da agenda do prestador |
| Google Gemini API (via OpenRouter) | Motor de linguagem natural do assistente de IA |

## Requisitos Funcionais

Extraídos do documento de Especificação de Requisitos, na forma de histórias de usuário. Cada um está mapeado a uma ou mais issues do backlog.

### Cliente

| Código | Título | Prioridade |
|---|---|---|
| RF001 | Autenticação e Cadastro via Google OAuth2 | Alta |
| RF002 | Buscar e Filtrar Prestadores de Serviço (Catálogo) | Alta |
| RF003 | Agendar Serviço com Controle de Concorrência | Alta |
| RF004 | Consultar Assistente de IA Integrado (LLM) | Média/Alta |
| RF005 | Cancelar Serviço | Alta |
| RF006 | Avaliar Prestador de Serviço | Alta |
| RF007 | Receber Notificações e Lembretes | Alta |

### Prestador

| Código | Título | Prioridade |
|---|---|---|
| RF008 | Cadastro e Perfil Profissional | Alta |
| RF009 | Gerenciar Serviços Oferecidos | Alta |
| RF010 | Definir Horários Disponíveis | Alta |
| RF011 | Cancelar ou Propor Reagendamento de Cliente | Alta |
| RF012 | Sincronizar Agendamento com Google Calendar | Média |

### Administrador

| Código | Título | Prioridade |
|---|---|---|
| RF013 | Gerenciar Usuários, Categorias e Cadastros | Alta |
| RF014 | Consultar Relatórios de Auditoria | Alta |
| RF015 | Dashboard de Monitoramento, Automação e Alertas | Média/Alta |

> RF007 é compartilhado entre Cliente e Prestador (notificações para ambos os atores).

Cada requisito segue o formato **Como [ator], quero [ação], para [objetivo]**, com critérios de aceitação no formato Dado/Quando/Então. O detalhamento completo de cada história está no documento de Especificação de Requisitos do projeto.

## Requisitos Não Funcionais

| Código | Título | Prioridade | Detalhe |
|---|---|---|---|
| RNF001 | Arquitetura Distribuída e Virtualização | Alta | Serviços desacoplados, orquestrados em containers via Docker e Docker Compose. |
| RNF002 | Multi-linguagem | Alta | Pelo menos duas linguagens: Python (FastAPI no backend) e JavaScript (React Native no frontend). |
| RNF003 | Mensageria e Assincronismo | Alta | Fila no RabbitMQ para desacoplamento, comunicação indireta e processamento assíncrono. |
| RNF004 | Auditoria e Logs Nativos | Alta | PostgreSQL configurado nativamente para registrar inserções, atualizações, exclusões e leituras, com timestamp e identidade do usuário. |
| RNF005 | Design Responsivo e Mobile-First | Alta | Interface com foco mobile-first, funcionando bem também em desktop. |
| RNF006 | Disponibilidade | Alta | Sistema disponível 96% do tempo. |
| RNF007 | Automação e Backup | Alta | Backup automático da base de dados e scripts automatizados de manutenção. |
| RNF008 | Integrações de Inteligência Distribuída | Alta | Integração de modelos de linguagem (LLMs) via API, com inferência delegada a nós remotos. |
