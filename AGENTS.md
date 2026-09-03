# AGENTS.md

## Projeto

O **Wonder** é uma plataforma de agendamento de serviços de beleza desenvolvida como Projeto Integrador do curso de ADS.

O sistema conecta:

* clientes;
* prestadores de serviços;
* administradores.

O foco deste projeto é entregar um MVP funcional, tecnicamente consistente e compatível com os requisitos acadêmicos, evitando complexidade desnecessária.

---

## Documentação do projeto

A pasta `/docs` concentra a documentação disponível para consulta.

Ela contém:

* a documentação da **versão anterior do Wonder**, incluindo a antiga Wiki, requisitos, diagramas, modelagem e decisões do projeto anterior;
* o documento **V0 do novo Projeto Integrador 2026.2**, que define os requisitos e expectativas acadêmicas para a nova versão do sistema.

A documentação antiga deve ser utilizada principalmente como referência para:

* domínio do sistema;
* regras de negócio já existentes;
* atores e funcionalidades;
* modelagem previamente utilizada;
* contexto histórico das decisões.

Ela **não deve ser considerada automaticamente como a arquitetura atual**, pois o projeto está sendo reconstruído com uma nova stack e uma nova organização.

O documento V0 do Projeto Integrador deve ser utilizado como referência para os requisitos acadêmicos da nova implementação.

Quando houver conflito entre documentação antiga, decisões atuais e código existente, priorize:

1. decisões atuais registradas no projeto;
2. requisitos do novo Projeto Integrador;
3. código atual;
4. documentação antiga como referência histórica.

Não copie decisões da arquitetura anterior sem verificar se continuam válidas.

---

## Princípios para o agente

Ao trabalhar neste projeto:

1. **Priorize simplicidade.**

   * Escolha a solução mais simples que atenda corretamente ao requisito.
   * Não introduza abstrações, padrões ou infraestrutura sem necessidade concreta.

2. **Evite overengineering.**

   * Não crie camadas extras, interfaces, factories, eventos ou padrões apenas por "boa prática".
   * SOLID e padrões devem ser usados quando resolverem um problema real.

3. **Respeite decisões já tomadas.**

   * Não altere arquitetura, stack ou organização estrutural sem necessidade explícita.
   * Quando houver mais de uma solução válida, prefira a que melhor se encaixa na estrutura existente.

4. **Faça mudanças pequenas e objetivas.**

   * Não refatore partes não relacionadas à tarefa atual.
   * Preserve código existente que já funciona.
   * Evite ampliar o escopo solicitado.

5. **Não invente requisitos.**

   * Implemente apenas comportamento definido na documentação, código existente ou solicitação atual.
   * Caso algo ainda não esteja decidido, trate como sugestão e não como decisão arquitetural.

---

## Arquitetura atual

O backend é um projeto **Java 21 + Spring Boot 4** organizado como um projeto Maven multi-module.

Módulos principais:

* `core`
* `notification`
* `worker`

### core

É a aplicação principal e concentra as funcionalidades síncronas do sistema.

Responsabilidades conhecidas incluem:

* autenticação e usuários;
* catálogo;
* agendamentos;
* administração;
* métricas e observabilidade;
* demais regras de negócio principais.

Dentro do `core`, organize funcionalidades por domínio/package coeso.

Não transforme cada domínio interno em um serviço separado sem uma necessidade real.

### notification

Responsável pelo processamento assíncrono de notificações.

Recebe eventos através do RabbitMQ e executa tarefas de notificação sem bloquear o fluxo principal da aplicação.

### worker

Responsável por processamentos assíncronos mais custosos, especialmente geração/exportação de dados e relatórios.

Os trabalhos devem ser disparados pelo `core` através do RabbitMQ.

---

## Comunicação

Use comunicação síncrona quando o resultado for necessário para concluir imediatamente o fluxo atual.

Use RabbitMQ para processos que possam ocorrer de forma assíncrona.

Fluxos já definidos como assíncronos:

* notificações;
* geração/exportação de dados pelo worker.

Não introduza mensageria em operações simples apenas para desacoplar código.

---

## Stack

Tecnologias já adotadas ou previstas para o projeto:

* Java 21
* Spring Boot 4
* Maven
* PostgreSQL
* Spring Data JPA / Hibernate
* Flyway
* Bean Validation
* RabbitMQ
* MinIO
* Spring Boot Actuator
* Micrometer
* Prometheus
* Grafana
* JUnit 5
* Mockito
* Testcontainers
* Docker
* Docker Compose
* GitHub Actions

Não adicione novas tecnologias ou dependências sem necessidade concreta.

---

## Backend

Prefira a estrutura convencional do Spring:

`Controller -> Service -> Repository`

Use DTOs na fronteira HTTP.

Não exponha entidades JPA diretamente pela API.

Mantenha regras de negócio no domínio/service apropriado, não nos controllers.

Use `@Transactional` de forma consciente nos fluxos que realmente precisam de transação.

Centralize tratamento de erros HTTP quando aplicável.

Prefira código explícito e fácil de entender a abstrações genéricas.

---

## Banco de dados

O banco principal é PostgreSQL.

Alterações de schema devem ser feitas através de migrations do Flyway.

Evite criar tabelas, relacionamentos ou campos que não sejam necessários para os requisitos atuais.

Garanta integridade e concorrência principalmente nos fluxos de agendamento.

---

## Testes

Ao implementar uma funcionalidade:

* teste regras de negócio relevantes;
* use testes de integração quando houver interação real com banco, mensageria ou infraestrutura;
* utilize Testcontainers quando a infraestrutura real fizer diferença para o comportamento testado.

Não crie testes triviais apenas para aumentar cobertura.

Priorize testes que protejam regras e fluxos importantes.

---

## Configuração e infraestrutura

Configurações devem ser externalizadas.

Nunca versione:

* senhas;
* tokens;
* chaves;
* secrets.

Use profiles e variáveis de ambiente quando necessário.

O ambiente local deve continuar executável através do Docker Compose.

---

## Estilo de implementação

Antes de criar algo novo, procure por uma implementação ou padrão semelhante no projeto.

Siga nomes, estrutura e convenções já existentes.

Prefira:

* classes pequenas e coesas;
* métodos simples;
* nomes explícitos;
* pouca indireção;
* dependências mínimas.

Evite:

* arquitetura especulativa;
* abstrações para uso único;
* interfaces com apenas uma implementação sem motivo;
* hierarquias complexas;
* duplicação prematura de infraestrutura;
* refatorações fora do escopo da tarefa.

---

## Ao tomar decisões

Siga esta ordem de prioridade:

1. requisitos do Projeto Integrador;
2. decisões arquiteturais registradas no projeto;
3. documentação atual do Wonder;
4. código existente;
5. solução mais simples que preserve as anteriores.

Se uma decisão importante ainda não estiver definida, apresente a alternativa como sugestão em vez de assumir silenciosamente que ela foi adotada.

O objetivo é construir **um sistema correto, compreensível e demonstrável**, não a arquitetura mais sofisticada possível.
