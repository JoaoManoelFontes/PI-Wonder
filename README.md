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

Neste modo, PostgreSQL e RabbitMQ executam pelo Docker, enquanto `core`, `notification` e `worker` executam diretamente pela IDE. Por isso, os hosts do arquivo `.env.dev` devem ser `localhost`.

### 6.1. Configurar o `.env.dev`

Crie o arquivo local a partir do exemplo:

```bash
cp .env.example .env.dev
```

Se atente aos hosts. Rodando local, troque os valores das váriaveis `POSTGRES_HOST` e `RABBITMQ_HOST` para `localhost`

### 6.2. Subir a infraestrutura

Use o `.env.dev` para iniciar apenas PostgreSQL e RabbitMQ:

```bash
docker compose --env-file .env.dev up -d postgres rabbitmq
```

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

Para encerrar somente a infraestrutura:

```bash
docker compose --env-file .env.dev down
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

> `docker compose down -v` remove os dados locais do PostgreSQL e do RabbitMQ. Use esse comando apenas quando desejar reiniciar completamente o ambiente.

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

## 9. Documentação

A pasta [`docs`](docs/) reúne o documento do Projeto Integrador 2026.2 e a documentação da versão anterior do Wonder. O material antigo serve como referência de domínio e histórico; ele não representa automaticamente a arquitetura desta nova implementação.

Projeto desenvolvido no Instituto Federal de Educação, Ciência e Tecnologia do Rio Grande do Norte — Campus Pau dos Ferros, para o curso de Tecnologia em Análise e Desenvolvimento de Sistemas.
