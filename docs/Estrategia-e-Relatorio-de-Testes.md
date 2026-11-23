# Estratégia e Relatório de Testes do Wonder

## 1. Objetivo

Este documento descreve como os testes automatizados do Wonder foram definidos, quais níveis e técnicas foram adotados, os critérios usados para selecionar os casos e a situação quantitativa da suíte em 19 de novembro de 2026.

O escopo deste relatório é o backend atual, implementado em Java 21 e Spring Boot 4 e dividido nos módulos `core`, `notification` e `worker`. Testes de carga e testes completos da interface do usuário não fazem parte desta medição.

A estratégia foi orientada pelos requisitos do Projeto Integrador, pelas regras de negócio do Wonder e pelos riscos técnicos da aplicação. O objetivo não foi criar testes apenas para aumentar um percentual de cobertura, mas proteger os comportamentos que podem afetar agendamentos, segurança, persistência e comunicação assíncrona.

## 2. Como os testes foram classificados

O Wonder utiliza duas dimensões de classificação:

- **nível de teste**, que indica quanto do sistema participa da verificação;
- **técnica de teste**, que indica como os casos foram derivados.

Por isso, "unitário" não é sinônimo de "estrutural", assim como "integração" não é sinônimo de "funcional". Um teste unitário pode ser funcional, estrutural ou combinar as duas técnicas. A mesma relação vale para um teste de integração.

| Dimensão | Classificações usadas |
|---|---|
| Nível | unitário e integração |
| Técnica | funcional (caixa-preta) e estrutural (caixa-branca) |

## 3. Níveis de teste

### 3.1. Testes unitários

Os testes unitários verificam uma unidade pequena de código de forma isolada, normalmente um service, listener, client, conversor ou componente de segurança. As dependências são substituídas por mocks quando a unidade não precisa da infraestrutura real para demonstrar seu comportamento.

No Wonder, esses testes utilizam principalmente:

- JUnit 5 para execução e parametrização;
- AssertJ para as verificações;
- Mockito para mocks, captura de argumentos e verificação de interações.

Foram considerados unitários, nesta medição, os testes que não carregam contexto Spring nem iniciam infraestrutura por Testcontainers.

Exemplos:

- `AgendamentoStatusServiceTest` isola o service e verifica o limite de antecedência para cancelamento;
- `SecurityConfigTests` verifica tokens, scopes e roles sem iniciar a aplicação;
- `NotificationListenerTest` verifica validação, montagem de mensagens e propagação de falhas;
- `EmailServiceTest` verifica validações e interações com o remetente de e-mail.

### 3.2. Testes de integração

Os testes de integração verificam a colaboração entre duas ou mais partes reais do sistema. Dependendo do objetivo, eles carregam toda a aplicação ou apenas uma fatia web do Spring.

No Wonder, eles podem envolver:

- controllers, validação, segurança, services e repositories;
- contratos HTTP executados com MockMvc ou servidor HTTP real;
- PostgreSQL e migrations Flyway;
- RabbitMQ e processamento de mensagens;
- Keycloak e seu realm de teste;
- MinIO para armazenamento de arquivos;
- MailHog para verificação do e-mail efetivamente enviado.

PostgreSQL, RabbitMQ, Keycloak, MinIO e MailHog são iniciados em contêineres descartáveis por Testcontainers quando o comportamento real da infraestrutura é relevante. A massa de dados é criada pelo próprio teste ou por migrations conhecidas, evitando dependência do ambiente de desenvolvimento.

Para esta medição, também foram classificados como integração os testes de fatia web com `@WebMvcTest`: embora não carreguem toda a aplicação, eles integram controller, serialização, validação e filtros de segurança do Spring.

Exemplos:

- `AgendamentoCriacaoListagemIntegrationTest` percorre HTTP, segurança, service, repository e PostgreSQL;
- `KeycloakAdminIntegrationTest` executa operações contra um Keycloak 26 real e temporário;
- `CatalogoServicoFotoIntegrationTest` verifica o fluxo HTTP com PostgreSQL e MinIO;
- `EmailServiceIntegrationTest` utiliza RabbitMQ e MailHog reais em contêineres;
- `AdminDashboardControllerTest` verifica o contrato HTTP e as regras de acesso da camada web.

### 3.3. Pirâmide de testes

A direção adotada para novos testes é:

1. concentrar regras puras e ramificações nos testes unitários, que são mais rápidos;
2. usar testes de integração para contratos HTTP, persistência, segurança, concorrência e infraestrutura;
3. manter poucos testes de sistema completos, reservados aos fluxos mais importantes.

A suíte atual ainda é mais pesada em integração: dos 795 casos gerados na execução analisada, 596 (75,0%) foram classificados como integração e 199 (25,0%) como unitários. Essa distribuição decorre da quantidade de contratos HTTP, combinações de autorização e cenários com infraestrutura real. Ela não representa ainda uma pirâmide clássica e deve ser acompanhada para que regras simples não dependam desnecessariamente de testes caros.

## 4. Técnicas de teste

### 4.1. Testes funcionais ou de caixa-preta

Os testes funcionais foram derivados dos requisitos, casos de uso, critérios de aceitação e contratos externos. Eles verificam o comportamento observável sem exigir conhecimento da implementação interna.

As principais evidências verificadas são:

- código de resposta HTTP;
- formato e conteúdo do JSON;
- dados persistidos;
- mensagem publicada ou consumida;
- e-mail produzido;
- permissão ou bloqueio conforme o perfil;
- estado final do recurso;
- resposta produzida diante de uma entrada inválida ou conflito.

Exemplos funcionais do Wonder:

| Cenário | Resultado esperado |
|---|---|
| Cliente cria um agendamento em horário livre | HTTP `201`, status `PENDENTE` e horário persistido |
| Segundo cliente tenta ocupar um período já reservado | HTTP `409` e apenas uma reserva persistida |
| Requisição protegida é feita sem autenticação | HTTP `401` |
| Usuário autenticado não possui a role exigida | HTTP `403` |
| Catálogo público é consultado | somente prestadores, categorias e serviços publicáveis são retornados |
| Evento válido de notificação é consumido | e-mail é produzido para os destinatários sem duplicidade |
| Foto válida é enviada | metadados são persistidos e arquivo é armazenado no MinIO |

### 4.2. Testes estruturais ou de caixa-branca

Os testes estruturais foram derivados das condições e caminhos existentes no código. Eles verificam se as ramificações relevantes, os fluxos de exceção e as interações internas foram exercitados.

Os principais elementos observados são:

- caminhos verdadeiro e falso de uma condição;
- limites usados em comparações;
- retorno antecipado após erro;
- exceções lançadas ou propagadas;
- quantidade e ordem relevante das chamadas;
- ausência de chamada a uma dependência quando o fluxo é rejeitado;
- tratamento de valores ausentes ou estruturas malformadas.

Exemplos estruturais do Wonder:

- o cancelamento é exercitado imediatamente antes, exatamente no limite e depois do limite de 15 minutos;
- o introspector de token percorre os caminhos de token válido, inativo, `sub` inválido e roles malformadas;
- quando um cancelamento é rejeitado, o teste garante que o histórico não seja gravado;
- quando o SMTP falha, o teste verifica que a exceção é propagada para que o listener possa rejeitar a mensagem;
- quando uma integração externa falha, verifica-se se o fluxo local deve ser interrompido ou preservado conforme a regra definida.

As duas técnicas podem estar presentes no mesmo caso. O teste do limite de cancelamento, por exemplo, demonstra uma regra funcional e também percorre os dois ramos da condição interna.

## 5. Critérios usados para definir os casos

### 5.1. Rastreabilidade com os requisitos

Cada teste funcional parte de um requisito, regra de negócio, contrato ou critério de aceitação. Para cada comportamento relevante, foram selecionados ao menos o caminho esperado e os principais caminhos de rejeição.

Exemplos de rastreabilidade:

| Requisito ou risco | Evidência selecionada |
|---|---|
| impedir dois agendamentos incompatíveis | testes sequenciais e concorrentes com PostgreSQL real |
| respeitar horários do prestador | testes dentro, fora e nos limites do período de atendimento |
| restringir funções administrativas | acessos sem token, com role incorreta e com role `ADMIN` |
| processar notificações de forma assíncrona | publicação, consumo, template, destinatários e falha de envio |
| armazenar fotos | tipo, tamanho, propriedade, persistência e falha do MinIO |
| preservar integridade nas mudanças de status | transições permitidas, proibidas, histórico e concorrência |

### 5.2. Particionamento de equivalência

As entradas foram divididas em grupos que devem produzir o mesmo comportamento, selecionando representantes de cada grupo.

Exemplos:

- usuário autenticado e não autenticado;
- usuário autorizado e sem a role necessária;
- serviço ativo, inativo ou pertencente a outro prestador;
- mensagem válida, nula, vazia ou malformada;
- recurso existente e inexistente;
- foto válida, tipo não permitido e tamanho excedido.

### 5.3. Análise de valores-limite

Valores imediatamente anteriores, iguais e posteriores ao limite foram priorizados, pois erros de comparação costumam ocorrer nessas posições.

Um exemplo concreto está em `AgendamentoStatusServiceTest`: para a antecedência mínima de 15 minutos, equivalente a 900 segundos, são testados:

| Entrada | Resultado esperado |
|---:|---|
| 899 segundos | cancelamento rejeitado |
| 900 segundos | cancelamento permitido |
| 901 segundos | cancelamento permitido |

### 5.4. Tabelas de decisão

Foram usadas combinações de condições quando o resultado depende de mais de um fator. Os principais fatores são:

- identidade do usuário;
- role do usuário;
- propriedade do recurso;
- estado atual;
- estado solicitado;
- validade dos dados;
- disponibilidade da dependência externa.

Essa técnica é especialmente relevante para autorização, administração de prestadores e alteração do estado dos agendamentos.

### 5.5. Transição de estados

Os testes verificam o estado inicial, a operação realizada, o estado final e os efeitos colaterais. Essa técnica foi aplicada a agendamentos, prestadores, serviços e categorias.

Foram considerados:

- transições permitidas;
- transições proibidas;
- repetição da mesma operação;
- motivo obrigatório quando aplicável;
- gravação de histórico;
- liberação ou ocupação da disponibilidade.

### 5.6. Concorrência e integridade

O agendamento foi classificado como fluxo crítico. Não foi suficiente testar apenas chamadas sequenciais, pois duas requisições podem observar o mesmo horário como livre.

`AgendamentoCriacaoListagemIntegrationTest` dispara duas requisições concorrentes e parametriza o deslocamento do segundo início:

| Deslocamento | Resultado esperado |
|---:|---|
| 0 minutos | uma criação e um conflito; 1 reserva persistida |
| 30 minutos | uma criação e um conflito; 1 reserva persistida |
| 60 minutos | duas criações; 2 reservas persistidas |

Esse teste usa transações reais do PostgreSQL e confirma o estado persistido depois das duas requisições.

### 5.7. Cenários negativos e falhas externas

Além do caminho de sucesso, foram selecionados cenários de:

- entrada ausente, vazia, inválida ou fora do limite;
- recurso inexistente;
- conflito de estado ou de agenda;
- falta de autenticação ou autorização;
- resposta externa inválida;
- indisponibilidade de Keycloak, RabbitMQ, MinIO ou SMTP;
- falha parcial depois de uma alteração local.

O resultado esperado de uma falha externa depende da regra do fluxo. Em alguns casos a operação deve ser interrompida; em outros, uma decisão local já confirmada deve ser preservada e a falha apenas registrada.

### 5.8. Risco e criticidade

A profundidade dos testes foi definida pelo impacto de uma falha. Receberam maior prioridade:

1. concorrência e integridade dos agendamentos;
2. autenticação e autorização;
3. transições de estado;
4. persistência e migrations;
5. contratos HTTP consumidos pelo frontend;
6. publicação e consumo de mensagens;
7. integrações com Keycloak, MinIO e serviço de e-mail.

## 6. Critérios de entrada e saída

### 6.1. Critérios de entrada

Um comportamento está pronto para receber testes quando:

- o requisito ou regra de negócio está definido;
- entradas, resultados e erros esperados são conhecidos;
- o código compila;
- a massa necessária pode ser criada de forma controlada;
- as imagens Docker necessárias estão disponíveis para testes de integração.

### 6.2. Critérios de saída

Uma funcionalidade é considerada aprovada pela suíte quando:

- todos os casos planejados passam;
- não existem falhas ou erros de execução;
- não existem testes ignorados sem justificativa;
- o caminho de sucesso e os principais caminhos negativos foram cobertos;
- o estado persistido e os efeitos colaterais correspondem à regra;
- o teste pode ser repetido sem depender de dados deixados por outra execução;
- os fluxos críticos possuem teste de integração com a infraestrutura que influencia seu comportamento.

Uma exceção registrada propositalmente pelo sistema durante um teste negativo não representa falha da suíte. O que define a aprovação é o resultado informado pelo JUnit/Surefire.

## 7. Números da suíte

### 7.1. Metodologia da contagem

Foram usadas duas contagens diferentes:

- **métodos declarados**: ocorrências de `@Test`, `@ParameterizedTest` e anotações equivalentes no código-fonte;
- **casos executados**: invocações reportadas pelo Maven Surefire.

Os números são diferentes porque um único `@ParameterizedTest` gera vários casos. Na data da medição, havia 111 métodos parametrizados.

### 7.2. Inventário por módulo

| Módulo | Classes com testes | Métodos declarados | Casos gerados |
|---|---:|---:|---:|
| `core` | 62 | 432 | 744 |
| `notification` | 5 | 25 | 50 |
| `worker` | 1 | 1 | 1 |
| **Total** | **68** | **458** | **795** |

### 7.3. Distribuição por nível

| Nível | Classes | Casos gerados | Participação |
|---|---:|---:|---:|
| Unitário | 21 | 199 | 25,0% |
| Integração | 47 | 596 | 75,0% |
| **Total** | **68** | **795** | **100%** |

A quantidade de casos não mede sozinha a qualidade da suíte. Um teste parametrizado pode gerar dezenas de casos baratos, enquanto um único teste de integração pode iniciar vários contêineres e validar um fluxo completo.

### 7.4. Resultado da execução de 19/11/2026 após a correção

Ambiente utilizado:

- Java 21.0.13;
- Maven em modo offline para as dependências Java;
- Docker 29.10.0;
- PostgreSQL, RabbitMQ, Keycloak, MinIO e MailHog por Testcontainers, conforme cada classe;
- comando de validação final: `./mvnw -o test` na raiz do projeto.

| Módulo | Casos | Aprovados | Falhas de asserção | Erros | Ignorados |
|---|---:|---:|---:|---:|---:|
| `core` | 744 | 744 | 0 | 0 | 0 |
| `notification` | 50 | 50 | 0 | 0 | 0 |
| `worker` | 1 | 1 | 0 | 0 | 0 |
| **Total** | **795** | **795** | **0** | **0** | **0** |

Todos os 199 casos classificados como unitários e os 596 casos classificados como integração foram aprovados. A execução completa levou 6 minutos e 50 segundos e terminou com `BUILD SUCCESS` nos três módulos. A suíte atende aos critérios de saída definidos na seção 6.2.

### 7.5. Erro detectado e impacto no projeto

Antes da correção, 25 erros estavam concentrados em duas classes de fatia web:

| Classe | Casos com erro |
|---|---:|
| `AdminDashboardControllerTest` | 5 |
| `AdminDashboardAgendamentosControllerTest` | 20 |

A causa raiz registrada pelo Spring foi a ausência de um bean de `ExportacaoService` ao carregar `AdminDashboardController` no contexto restrito de `@WebMvcTest`. O controller passou a exigir esse service no construtor após a inclusão da exportação de agendamentos, mas os dois testes que carregavam o controller isoladamente ainda não forneciam a nova dependência. Como o contexto não iniciou, os resultados foram classificados como **erros**, e não como falhas de asserção.

Os testes detectaram uma regressão na configuração das fatias web. O impacto imediato era impedir a execução de 25 verificações dos contratos do dashboard, incluindo respostas HTTP, validação de parâmetros e autorização administrativa, além de manter o build da suíte em estado reprovado. Não foi identificada falha equivalente na inicialização da aplicação completa, pois nela existe uma implementação real de `ExportacaoService`; portanto, o defeito estava no isolamento dos testes, mas reduzia a capacidade do projeto de detectar regressões nessas rotas e impediria a aprovação do pipeline de testes.

A correção foi adicionar um mock de `ExportacaoService` às duas classes `@WebMvcTest`, preservando o escopo restrito da fatia e sem alterar o comportamento de produção. Primeiro, os 25 casos afetados foram executados isoladamente e aprovados. Depois, a suíte completa confirmou 795 casos aprovados, sem falhas, erros ou testes ignorados.

## 8. Cobertura estrutural

O documento V0 do Projeto Integrador prevê o uso do JaCoCo para cobertura. Na data deste relatório, o plugin ainda não estava configurado nos arquivos Maven do projeto. Por isso, não existe um percentual verificável de cobertura de linhas ou ramificações, e este documento não atribui um valor estimado.

Atualmente, a seleção estrutural é demonstrada pelos casos que percorrem condições, limites e exceções. A medição formal deve ser incorporada posteriormente com JaCoCo. Um percentual mínimo só deve ser tratado como critério adotado depois de ser acordado pela equipe e registrado no plano de testes.

Mesmo depois da adoção do JaCoCo, cobertura não substituirá os critérios funcionais. Executar uma linha não demonstra, por si só, que a regra foi corretamente verificada.

## 9. Limitações e próximos passos

Na situação atual:

- a medição formal de cobertura estrutural ainda depende da configuração do JaCoCo;
- a suíte possui uma baseline aprovada de 795 casos; os 25 erros de contexto web foram corrigidos e revalidados na execução completa;
- o módulo `worker` possui apenas um teste de inicialização do contexto e ainda precisa de testes das regras de geração e exportação quando esses fluxos forem implementados;
- os testes de carga exigidos pelo Projeto Integrador constituem uma atividade separada desta suíte;
- testes end-to-end da interface do usuário não estão representados pelos testes HTTP do backend.

Os próximos passos recomendados são manter a suíte aprovada no CI, configurar o relatório JaCoCo e relacionar os casos críticos diretamente aos identificadores dos requisitos.

## 10. Execução

Toda a suíte pode ser executada na raiz do projeto:

```bash
./mvnw clean verify
```

Para executar um módulo e construir suas dependências:

```bash
./mvnw -pl core -am test
./mvnw -pl notification -am test
./mvnw -pl worker -am test
```

Os testes de integração dependem de um daemon Docker acessível. Os relatórios detalhados do Surefire são produzidos em `target/surefire-reports` dentro de cada módulo.

