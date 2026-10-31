# Wonder App

[![CI](https://github.com/JoaoManoelFontes/PI-Wonder/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/JoaoManoelFontes/PI-Wonder/actions)

Plataforma de agendamento de serviços de beleza desenvolvida como Projeto Integrador do curso de Análise e Desenvolvimento de Sistemas do IFRN.

## 1. Descrição do sistema

O **Wonder** conecta clientes, prestadores de serviços de beleza e administradores. Prestadores disponibilizam seus serviços e horários; clientes encontram profissionais e realizam agendamentos; administradores acompanham e gerenciam a operação da plataforma.

Esta versão está sendo reconstruída como um MVP em Java e Spring Boot. A aplicação principal concentra os fluxos síncronos e utiliza RabbitMQ para delegar tarefas assíncronas de notificação e geração de relatórios.

**Funcionalidades previstas para o MVP:**

- autenticação e controle de acesso para clientes, prestadores e administradores;
- catálogo de prestadores e serviços;
- agendamentos com controle de conflito de horário;
- notificações processadas de forma assíncrona;
- geração e exportação assíncrona de relatórios;
- auditoria, métricas e observabilidade.

## 2. Tecnologias

| Camada | Tecnologia |
|---|---|
| Backend | Java 21 + Spring Boot 4 |
| Build | Maven multi-module |
| API | Spring Web MVC + Bean Validation |
| Persistência | PostgreSQL + Spring Data JPA/Hibernate |
| Versionamento do banco | Flyway |
| Mensageria | RabbitMQ |
| Segurança | Spring Security + OAuth 2.1/OIDC |
| Observabilidade | Spring Boot Actuator + Micrometer |
| Testes | JUnit 5 + Mockito + Testcontainers |
| Containerização | Docker + Docker Compose |
| CI | GitHub Actions |

## 3. Arquitetura

O backend é um projeto Maven multi-module composto por três aplicações:

| Módulo | Responsabilidade |
|---|---|
| `core` | Aplicação principal: API REST, autenticação, usuários, catálogo, agendamentos e administração |
| `notification` | Consumo de eventos e processamento assíncrono de notificações |
| `worker` | Processamentos mais custosos, especialmente geração e exportação de dados e relatórios |

O `core` se comunica de forma assíncrona com `notification` e `worker` por meio do RabbitMQ. O PostgreSQL é o banco de dados principal da aplicação.

**Serviços e portas do ambiente local:**

| Serviço | Porta | Acesso |
|---|---:|---|
| Core | `8080` | `http://localhost:8080` |
| PostgreSQL | `5432` | `localhost:5432` |
| RabbitMQ | `5672` | `localhost:5672` |
| RabbitMQ Management | `15672` | `http://localhost:15672` |
| MinIO | `9000` | `http://localhost:9000` |
| MinIO Console | `9001` | `http://localhost:9001` |

Os módulos `notification` e `worker` são consumidores de filas e não expõem portas no Docker Compose.

## 4. Pré-requisitos

- [Git](https://git-scm.com/downloads);
- JDK 21;
- IntelliJ IDEA, para execução em modo de desenvolvimento;
- [Docker](https://docs.docker.com/get-docker/) com Docker Compose.

Não é necessário instalar o Maven globalmente: o projeto inclui o Maven Wrapper (`mvnw`).

## 5. Instalação

```bash
# 1. Clonar o repositório
git clone https://github.com/JoaoManoelFontes/PI-Wonder.git
cd PI-Wonder

# 2. Validar a compilação e executar os testes
./mvnw clean verify
```

## 6. Executar pelo IntelliJ IDEA

Neste modo, PostgreSQL, RabbitMQ e MinIO executam pelo Docker, enquanto `core`, `notification` e `worker` executam diretamente pela IDE. Por isso, os hosts do arquivo `.env.dev` devem ser `localhost`.

### 6.1. Configurar o `.env.dev`

Crie o arquivo local a partir do exemplo:

```bash
cp .env.dev.example .env.dev
```

O exemplo de desenvolvimento já configura os hosts para `localhost`, incluindo o endpoint do MinIO.

### 6.2. Subir a infraestrutura

Use o `compose.dev.yaml` com o `.env.dev` para iniciar PostgreSQL, RabbitMQ, MinIO e Keycloak:

Antes da primeira inicialização, preencha `KEYCLOAK_CLIENT_SECRET` conforme a seção 6.2.1.

```bash
docker compose -f compose.dev.yaml --env-file .env.dev up -d
```

O argumento `--env-file .env.dev` é necessário para resolver as referências `${...}` do Compose, como `MINIO_ACCESS_KEY` e `MINIO_SECRET_KEY`. O `env_file` declarado nos serviços injeta variáveis nos contêineres, mas não fornece os valores para essa interpolação. Sem o argumento, o Compose usa o `.env` por padrão, o que pode causar avisos de variáveis ausentes e valores vazios.

### 6.2.1. Configurar o client administrativo do Keycloak

O realm em `infra/keycloak/wonder-realm.json` inclui o client confidencial `wonder-core`, com service account e permissões `view-realm` e `manage-users` do client `realm-management`. O login do frontend continua usando `wonder-web`.

Antes da primeira importação, gere um secret local, por exemplo no PowerShell:

```powershell
[Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
```

Copie o resultado para `KEYCLOAK_CLIENT_SECRET` no `.env.dev`, sem versionar esse arquivo. O Compose injeta essa variável no Keycloak, que substitui `${KEYCLOAK_CLIENT_SECRET}` durante a importação. Carregue o mesmo `.env.dev` na execução do `core`.

Variáveis da integração administrativa:

| Variável | Desenvolvimento | Finalidade |
|---|---|---|
| `KEYCLOAK_SERVER_URL` | `http://localhost:8081` | URL base da Admin API, sem `/realms/wonder` |
| `KEYCLOAK_REALM` | `wonder` | Realm administrado |
| `KEYCLOAK_CLIENT_ID` | `wonder-core` | Client com service account |
| `KEYCLOAK_CLIENT_SECRET` | Gerado localmente | Secret compartilhado entre Keycloak e core |
| `KEYCLOAK_CONNECT_TIMEOUT` | `3s` | Limite para estabelecer conexão |
| `KEYCLOAK_READ_TIMEOUT` | `5s` | Limite de espera pela resposta |

O `--import-realm` não atualiza realms existentes. Se o realm `wonder` já estiver criado, configure pelo Admin Console em `http://localhost:8081`: crie o client OpenID Connect `wonder-core`, habilite **Client authentication** e **Service accounts roles**, desabilite os fluxos de login e defina o secret gerado pelo Keycloak no `.env.dev`. Em **Service accounts roles**, atribua `view-realm` e `manage-users` de `realm-management`. Com **Full scope allowed** desabilitado, adicione essas mesmas roles aos scope mappings do client e mantenha o client scope `roles`. Reinicie o `core` após atualizar o secret. Não é necessário apagar volumes ou usuários.

O `KeycloakAdminService.adicionarRoleProvider(UUID usuarioId)` obtém um token via `client_credentials`, consulta a realm role `PROVIDER` e a adiciona ao usuário, preservando as demais roles. Repetir a atribuição é seguro. Falhas de comunicação, credenciais ou resposta da integração são traduzidas em HTTP `503`. O token é obtido a cada operação; não há cache nem retentativas automáticas.

O endpoint `POST /admin/prestadores/solicitacoes/{id}/aprovar` exige `ADMIN`. O `PrestadorService.aprovarSolicitacao` busca o cadastro com bloqueio, valida o status `PENDENTE`, atribui `PROVIDER` ao usuário vinculado ao prestador no Keycloak e só então ativa o cadastro e registra o administrador e a data da aprovação. A role `CUSTOMER` é preservada. Solicitações inexistentes retornam `404`; solicitações fora de `PENDENTE`, incluindo uma aprovação repetida, retornam `409` antes de acessar o Keycloak. Falhas na integração retornam `503` e revertem a transação local, mantendo a solicitação pendente. Consultar e reprovar solicitações não atribui roles.

A transação do PostgreSQL não inclui o Keycloak. Se a role for atribuída, mas sua resposta se perder ou o commit local falhar, o usuário poderá ter `PROVIDER` com o cadastro ainda pendente. Nesse caso, repita a aprovação: atribuir uma role já existente é seguro e permite concluir o cadastro. Não há remoção automática da role nem transação distribuída. O frontend deve renovar o access token para receber `PROVIDER` após a aprovação; o JWT já emitido não é alterado.

O `compose.yaml` completo ainda não inclui Keycloak: nesse modo, disponibilize-o na rede do `core` e ajuste `KEYCLOAK_SERVER_URL` e `KEYCLOAK_ISSUER_URI` para o servidor utilizado.

### 6.3. Criar as configurações de execução

No IntelliJ IDEA, acesse **Run > Edit Configurations** e crie uma configuração do tipo **Spring Boot** para cada classe:

| Nome sugerido | Classe principal | Módulo |
|---|---|---|
| `Wonder Core` | `br.edu.ifrn.wonder.core.CoreApplication` | `core` |
| `Wonder Notification` | `br.edu.ifrn.wonder.notification.NotificationApplication` | `notification` |
| `Wonder Worker` | `br.edu.ifrn.wonder.worker.WorkerApplication` | `worker` |

Em cada configuração:

1. selecione o módulo correspondente no campo **Use classpath of module**;
2. em **Active profiles**, informe `development`;
3. em **Environment variables**, carregue o arquivo `$PROJECT_DIR$/.env.dev`;
4. salve a configuração.

Inicie as três aplicações. O `core` ficará disponível em `http://localhost:8080`.

Para permitir acesso do frontend, configure `CORS_ALLOWED_ORIGINS` no `.env.dev` (IntelliJ) ou `.env` (Docker Compose), separando as origins por vírgula:

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

Cada origin deve conter protocolo, host e porta (quando necessária), sem caminho ou barra final. A lista vale para toda a API, com todos os métodos e headers, incluindo `Authorization`. Endpoints protegidos continuam exigindo JWT. Sem a variável, nenhuma origin externa é liberada. Reinicie o `core` após alterar a lista; no Compose, recrie o serviço com `docker compose up -d --force-recreate core`.

Para encerrar somente a infraestrutura:

```bash
docker compose -f compose.dev.yaml --env-file .env.dev down
```

## 7. Executar pelo Docker Compose

Neste modo, todas as aplicações e dependências executam em contêineres. Dentro da rede do Compose, os hosts devem usar os nomes `postgres` e `rabbitmq`, como definido em `.env.example`.

```bash
# 1. Criar o arquivo utilizado pelo Compose
cp .env.example .env

# 2. Construir as imagens e subir o ambiente completo
docker compose up --build -d

# 3. Acompanhar os logs
docker compose logs -f
```

Depois da inicialização, o `core` estará disponível em `http://localhost:8080` e o painel do RabbitMQ em `http://localhost:15672`.

Comandos úteis:

```bash
# Consultar o estado dos containers
docker compose ps

# Reconstruir os serviços após alterações no código
docker compose up --build -d

# Encerrar o ambiente preservando os dados
docker compose down

# Encerrar o ambiente e remover os volumes locais
docker compose down -v
```

> `docker compose down -v` remove os dados locais do PostgreSQL, RabbitMQ e MinIO. Use esse comando apenas quando desejar reiniciar completamente o ambiente.

## 8. Testes

Na raiz do repositório, execute toda a suíte:

```bash
./mvnw clean verify
```

Para testar somente um módulo e também construir suas dependências:

```bash
./mvnw -pl core -am test
./mvnw -pl notification -am test
./mvnw -pl worker -am test
```

Os testes de integração utilizam Testcontainers e, portanto, podem exigir que o Docker esteja em execução.

Para validar somente a atribuição de role no Keycloak:

```bash
./mvnw -pl core -Dtest=KeycloakClientTest,KeycloakAdminServiceTest,KeycloakAdminIntegrationTest,PrestadorServiceTest,AdminPrestadorIntegrationTest test
```

O teste de integração inicia um Keycloak 26.0 temporário com o mesmo realm de `infra/keycloak`, um secret gerado durante o teste e sem dependência do ambiente local. Verifica a atribuição de `PROVIDER`, preservação de `CUSTOMER`, repetição e erros para usuário inexistente e credencial inválida. Os testes unitários cobrem o contrato HTTP, respostas inválidas e interrupção do fluxo em caso de falha.

O `AdminPrestadorIntegrationTest` também utiliza PostgreSQL e RabbitMQ temporários e exercita a rota HTTP com os services e o client reais, verificando os dados após a transação da requisição. Cobre aprovação, preservação de roles, autorização, estados inválidos, falha do Keycloak sem aprovação local e nova tentativa após simular a perda da resposta de uma atribuição já realizada.

## 9. Documentação

Exemplos da API atual: [catálogo](docs/Catalogo-Publico-Exemplos.md), com consultas públicas, operações autenticadas do prestador, upload de foto e comandos de validação.

Administração de categorias: [consulta, cadastro, edição, alteração de status e foto protegidos por role ADMIN](docs/Admin-Categorias.md), incluindo armazenamento no MinIO, categorias ativas e inativas, validação, duplicidade e erros de autorização.

Administração de prestadores: [consulta de pendências, detalhe e alteração de status protegidos por role ADMIN](docs/Admin-Prestadores.md), incluindo aprovação, correção, rejeição, suspensão, reativação e bloqueio de autoaprovação.

Disponibilidade: [exemplos das consultas de dias e horários livres](docs/Disponibilidade-Exemplos.md), com acesso sem login, parâmetros, respostas do seed, conflitos, listas vazias e critérios de validação (LW-015 e JN-015).

Agendamentos: [criação autenticada e listagem por cliente, prestador ou administrador](docs/Agendamentos.md), com filtro por data, validação da grade de atendimento e proteção contra reservas sobrepostas.

Consumo pelo frontend: [validação e simulação dos contratos de catálogo](docs/Catalogo-Validacao-Frontend.md), com comparação ao app antigo, scripts executáveis e pendências de integração (LW-013).

A pasta [`docs`](docs/) reúne o documento do Projeto Integrador 2026.2 e a documentação da versão anterior do Wonder. O material antigo serve como referência de domínio e histórico; ele não representa automaticamente a arquitetura desta nova implementação.

Projeto desenvolvido no Instituto Federal de Educação, Ciência e Tecnologia do Rio Grande do Norte — Campus Pau dos Ferros, para o curso de Tecnologia em Análise e Desenvolvimento de Sistemas.
