**Relatório de Testes de Software do Wonder**

**Projeto:** Wonder  
**Disciplina:** Teste de Software  
**Integrantes:** João Roque, João Manoel e Lwkas Lwhan  
**Última atualização:** 23 de novembro de 2026  
**Situação:** documento em elaboração

# 1 Introdução e escopo

Este relatório apresenta a estratégia, os critérios, os casos de teste, os resultados e as evidências de qualidade do Wonder, uma plataforma de agendamento de serviços de beleza.

A avaliação considera as regras de negócio, a segurança, a persistência, a comunicação assíncrona e os fluxos essenciais utilizados por clientes, prestadores e administradores.

O escopo contempla:

* Autenticação e autorização.  
* Consulta ao catálogo e aos detalhes dos prestadores.  
* Cadastro e aprovação de prestadores.  
* Criação, consulta e cancelamento de agendamentos.  
* Edição do próprio perfil.  
* Administração de usuários e cadastros.  
* Notificações e processamento assíncrono.  
* Exportação de dados.

**A complementar:** delimitação final do escopo, funcionalidades priorizadas e eventuais restrições acordadas com os professores.

**Restrições: a execução E2E web não comprova o funcionamento nativo. A equipe não pretende validar integralmente a resiliência das filas e o ambiente não está configurado para escalabilidade horizontal. Essas restrições dependem de alinhamento com os professores.**

# 2 Estratégia de testes

A estratégia utiliza duas dimensões: o **nível de teste**, que indica quais partes do sistema participam da verificação, e a **técnica de teste**, que indica como os casos foram definidos.

## 2.1 Níveis de teste e pirâmide adotada

| Nível | Finalidade | Aplicação no Wonder |
| ----- | ----- | ----- |
| Unitário | Verificar uma unidade de código isoladamente | Regras de services, validações, conversores e processamento de eventos |
| Integração | Verificar a colaboração entre componentes | Contratos HTTP, segurança, banco de dados, mensageria e armazenamento |
| Sistema e E2E | Verificar fluxos completos pela interface | Entrar no aplicativo, agendar, cancelar e aprovar prestadores |

Os testes unitários utilizam dependências simuladas quando necessário. Os testes de integração utilizam infraestrutura real por Testcontainers quando ela influencia o comportamento avaliado.

Os testes E2E serão realizados com Cypress na versão web do aplicativo Expo. A execução em diferentes tamanhos de tela permitirá avaliar os fluxos responsivos, mas não comprovará diretamente o funcionamento do aplicativo Android ou iOS nativo.

A estratégia prioriza regras isoladas em testes unitários, integração para contratos e infraestrutura e poucos E2E para jornadas críticas. Na medição histórica de 19/11/2026, 199 dos 795 casos eram unitários (25%) e 596 de integração (75%). A concentração em integração decorre dos contratos HTTP, segurança e uso de infraestrutura real. A distribuição atualizada será apresentada na seção 4\.

## 2.2 Testes funcionais e estruturais

Os testes funcionais (caixa-preta) são derivados dos requisitos, regras de negócio e contratos, verificando resultados observáveis, como respostas HTTP e dados persistidos.  
Os testes estruturais (caixa-branca) são derivados das condições, ramificações e caminhos de exceção do código. Um mesmo caso pode combinar ambas as abordagens. As técnicas e exemplos concretos são apresentados na seção 3\.

## 2.3 Ferramentas e infraestrutura

| Frente | Ferramentas e infraestrutura |
| ----- | ----- |
| Backend | Java 21 e Spring Boot 4 |
| Testes Java | JUnit, Mockito e AssertJ |
| Integração | Spring Test, MockMvc e Testcontainers |
| Infraestrutura | PostgreSQL, Keycloak, RabbitMQ, MinIO e serviço de e-mail, conforme o cenário |
| Aplicativo | Expo, React Native, testes Node e verificação TypeScript |
| E2E planejado | Cypress |
| Cobertura planejada | JaCoCo |
| Mutação planejada | PIT |
| Carga e desempenho | Ferramenta e configuração a confirmar |
| Integração contínua | GitHub Actions |

**Cada execução deverá registrar:**

* Data e ambiente.  
* Versões e commits do backend e do frontend.  
* URL utilizada.  
* Ferramentas e comandos executados.  
* Preparação e limpeza dos dados.  
* Local das evidências e dos relatórios.

Serão utilizadas contas sintéticas de cliente, prestador e administrador. Credenciais não deverão aparecer no documento, nos arquivos versionados ou nas evidências.

## 2.4 Critérios de entrada e saída

Os critérios abaixo constituem uma proposta para validação da equipe.

**Critérios de entrada**

* Regras e resultados esperados conhecidos.  
* Contratos necessários definidos.  
* Código compilando.  
* Infraestrutura necessária acessível.  
* Contas e dados de teste preparados.  
* Aplicativo e backend em versões compatíveis.  
* Possibilidade de repetir o cenário sem depender de dados deixados por outra execução.

**Critérios de saída**

* Casos críticos selecionados executados e aprovados.  
* Falhas, erros e testes ignorados analisados.  
* Ausência de defeitos críticos ou altos abertos nos fluxos avaliados.  
* Persistência e efeitos observados coerentes com as regras.  
* Evidências suficientes para reproduzir a execução.  
* Correções acompanhadas de reteste.

**A definir:** metas de cobertura e mutação, critérios específicos de desempenho e responsáveis pela aprovação de cada frente.

## 2.5 Síntese das decisões estratégicas

A tabela consolida as escolhas que orientam a estratégia do Wonder. As técnicas de seleção dos casos são detalhadas na seção 3; os resultados e evidências, na seção 4\.

| Decisão | Justificativa | Aplicação no Wonder |
| :---- | :---- | :---- |
| Combinar caixa-preta e caixa-branca | Verificar requisitos e caminhos internos. | Regras de negócio, autorização e exceções. |
| Selecionar casos por técnicas e riscos | Priorizar condições relevantes e reduzir redundâncias. | Limites, equivalência, decisões, estados e concorrência. |
| Priorizar testes unitários | Obter retorno rápido sobre regras isoladas. | JUnit, Mockito e AssertJ. |
| Integrar com infraestrutura real | Detectar incompatibilidades ocultadas por mocks. | PostgreSQL, Keycloak, RabbitMQ, MinIO e e-mail. |
| Automatizar API e E2E (planejado) | Validar contratos e jornadas completas. | Cypress nas requisições HTTP e na interface web. |
| Medir cobertura (planejado) | Identificar linhas e ramificações não exercitadas. | JaCoCo no backend. |
| Avaliar mutação (planejado) | Verificar a sensibilidade dos testes a alterações incorretas. | PIT nas regras de status dos agendamentos. |
| Avaliar regressão e desempenho (planejado) | Medir detecção de regressões e comportamento sob carga. | Alteração controlada e teste de processamento assíncrono. |

# 3 Definição dos casos de teste

**Cada caso deverá ser relacionado a uma regra ou requisito atual do Wonder.**

## 3.1 Rastreabilidade com os requisitos

**Relacionar os casos às regras de agendamento, autorização, notificações, armazenamento de fotos e mudanças de estado.**

## 3.2 Particionamento de equivalência

**Selecionar representantes de entradas com comportamento semelhante: usuários autorizados e não autorizados, recursos existentes e inexistentes, mensagens válidas e malformadas.**

## 3.3 Análise de valores-limite

**AgendamentoStatusServiceTest verifica o cancelamento com 899, 900 e 901 segundos de antecedência: rejeitado, permitido e permitido, respectivamente, quando as demais condições são atendidas.**

## 3.4 Tabelas de decisão

**Combinar identidade, perfil, propriedade do recurso, estado atual e solicitado, validade dos dados e disponibilidade das dependências.**

## 3.5 Transição de estados

**Verificar transições permitidas e proibidas, repetição de operações, justificativas, histórico e efeitos colaterais.**

## 3.6 Concorrência e integridade

**AgendamentoCriacaoListagemIntegrationTest verifica duas requisições concorrentes com PostgreSQL real. Deslocamentos de 0 e 30 minutos resultam em uma criação e um conflito; 60 minutos, em duas criações. O estado persistido é verificado.**

## 3.7 Cenários negativos e falhas externas

**Verificar entradas inválidas, conflitos, ausência de autorização e falhas de integração. Isso não equivale à validação completa de resiliência das filas.**

## 3.8 Risco e criticidade

**Priorizar integridade dos agendamentos, segurança, transições de estado, persistência, contratos HTTP, mensageria e integrações externas.**

## 3.9 Casos de teste selecionados

| Campo | Preenchimento |
| ----- | ----- |
| Identificador | Código único do caso |
| Requisito ou regra | Comportamento que será verificado |
| Nível e técnica | Unitário, integração ou E2E; funcional ou estrutural |
| Prioridade | Criticidade do cenário |
| Pré-condições | Estado necessário antes da execução |
| Dados | Contas e informações utilizadas |
| Passos | Ações para executar o caso |
| Resultado esperado | Comportamento previsto |
| Resultado obtido | Comportamento observado |
| Situação | Planejado, aprovado, falhou, com erro, ignorado ou bloqueado |
| Evidência | Referência à execução e aos registros |

### 3.9.1 Casos E2E iniciais propostos

| ID | Fluxo | Resultado esperado | Situação |
| ----- | ----- | ----- | ----- |
| E2E-01 | Entrar como cliente e criar um agendamento | Reserva criada em horário livre e apresentada corretamente na agenda | Planejado |
| E2E-02 | Cancelar um agendamento próprio elegível | Cancelamento persistido, motivo registrado e disponibilidade liberada | Planejado |
| E2E-03 | Aprovar uma solicitação de prestador | Cadastro aprovado e acesso profissional disponível após atualização da sessão | Planejado |

Após os casos iniciais, serão priorizados cenários de edição do perfil, notificações e caminhos negativos relevantes.

**A complementar:** pré-condições, dados, passos detalhados, requisitos relacionados e evidências de cada caso.

# 4 Execução e resultados

## 4.1 Testes unitários

**Na medição histórica de 19/11/2026, os 199 casos unitários foram aprovados. A preencher: distribuição atualizada, resultado, captura do relatório e análise.**

## 4.2 Testes de integração

**Na medição histórica de 19/11/2026, os 596 casos de integração foram aprovados. A preencher: distribuição atualizada, captura do relatório e evidências das integrações prioritárias.**

## 4.3 Testes de API

**Planejado: verificar contratos HTTP com Cypress. A preencher: cenários, resultados, relatório e evidências.**

## 4.4 Testes de sistema (E2E)

**Planejado: automatizar os fluxos prioritários na versão web do Expo e demonstrá-los na URL publicada. A preencher: resultados, capturas ou vídeos e análise.**

**Registro consolidado da execução de 23/11/2026**

| Conjunto | Total de casos | Aprovados | Falhas | Erros | Ignorados |
| ----- | ----- | ----- | ----- | ----- | ----- |
| Backend — core | 761 | 761 | 0 | 0 | 0 |
| Backend — notification | 63 | 63 | 0 | 0 | 0 |
| Backend — worker | 64 | 64 | 0 | 0 | 0 |
| **Total do backend** | **888** | **888** | **0** | **0** | **0** |
| Frontend — testes Node | 72 | 71 | 0 | 0 | 1 |

A verificação TypeScript e o build web também foram aprovados. O teste ignorado do frontend depende da configuração de uma API ativa.

A execução Maven do backend levou **7 minutos e 10 segundos**, utilizando o comando clean verify.

**Referências da execução:**

* Backend: commit 88c5cfb.  
* Frontend: commit 084b33f.  
* [CI do backend — tentativa 2](https://github.com/JoaoManoelFontes/PI-Wonder/actions/runs/45179849689/attempts/2).

Os **795 casos aprovados em 19/11** permanecem como medição histórica. Os testes Cypress ainda não foram implementados ou executados nesta atualização.

**Registro das próximas execuções**

**A preencher:** data, versão, ambiente, conjunto executado, resultados, duração, evidências e análise das diferenças em relação à execução anterior.

As contagens do backend, dos testes Node e do Cypress deverão permanecer separadas. Quantidade de testes não permite estimar cobertura de código ou percentual de conclusão do produto.

## 4.5 Cobertura de código (JaCoCo)

Preencher: relatório JaCoCo, linhas e ramificações medidas, pacotes avaliados, exclusões, meta acordada e resultado. Quantidade de testes não permite estimar cobertura de código.

## 4.6 Testes de mutação (PIT)

Objetivo: avaliar se os testes existentes conseguem identificar alterações incorretas nas regras de negócio. O PIT modifica temporariamente o código compilado e executa os testes selecionados contra essas versões alteradas, sem exigir alterações permanentes no código-fonte da funcionalidade.

Escopo inicial proposto: atualização de status dos agendamentos, incluindo autorização, transições permitidas e antecedência mínima de 15 minutos para cancelamento. A preparação local seleciona AgendamentoService e AgendamentoStatusServiceTest. Os métodos de criação, listagem, disponibilidade e publicação de notificações ficam fora desta primeira medição. O resultado não poderá ser apresentado como escore de todo o módulo de agendamentos ou do backend.

Execução proposta: primeiro confirmar a aprovação dos testes selecionados no código original; depois executar o PIT, analisar o relatório e avaliar os mutantes sobreviventes antes de decidir se são necessários novos cenários de teste. Este recorte usa testes unitários com dependências simuladas e não exige Docker. O uso do PIT com testes de integração teria outras necessidades de ambiente.

**Interpretação dos resultados:**

\- Eliminado: um teste detectou a alteração e falhou.

\- Sobrevivente: os testes selecionados não falharam diante da alteração. É necessário verificar se falta um cenário, se as asserções são insuficientes ou se a alteração é equivalente no comportamento observável.

\- Sem cobertura: nenhum teste selecionado exercitou o trecho alterado.

\- Outras categorias, como timeout e erro de execução: devem ser registradas e analisadas separadamente, sem serem descritas como defeitos detectados pelas asserções.

Situação: planejado, com configuração preliminar local. A tentativa inicial foi interrompida e não constitui uma medição concluída. Ainda não há escore validado nem meta de aprovação acordada pela equipe.

Preencher após a execução: data, commit, versões do PIT e do plugin JUnit, comando, classes e métodos avaliados, testes selecionados, operadores utilizados, exclusões, duração, totais por categoria, escore informado pela ferramenta e link para o relatório HTML/XML. Registrar exemplos de mutantes sobreviventes e a decisão tomada em cada análise. Se forem acrescentados testes, preservar os relatórios anterior e posterior para demonstrar o efeito da melhoria.

## 4.7 Testes de carga e desempenho

Preencher: cenário, critérios definidos previamente, volumetria, média e p95, vazão, taxa de erro, profundidade e drenagem da fila, CPU e memória. Incluir a comparação entre uma e múltiplas instâncias exigida no projeto ou registrar eventual ajuste de escopo acordado com os professores.

## 4.8 Síntese dos resultados

**Consolidar os resultados por frente de teste após as execuções. A medição de 23/11 apresentada no início da seção 4 registra 888 casos aprovados no backend e 71 dos 72 testes Node aprovados, com um ignorado. Cobertura, mutação, regressão, carga e Cypress permanecem pendentes de evidência validada.**

# 5 Impacto dos testes no desenvolvimento

Os testes e as validações automatizadas contribuíram para identificar problemas nas integrações, na configuração dos próprios testes e no ambiente de execução. Os casos abaixo apresentam a manifestação do problema, sua causa, a correção e o benefício para o grupo. Diferenciamos falhas de asserção, erros de inicialização e impedimentos de infraestrutura para atribuir corretamente o impacto observado.

## 5.1 Erros na inicialização dos testes do dashboard

Problema e detecção: após a inclusão de ExportacaoService no controller do dashboard, 25 casos de testes web apresentaram erros ao iniciar o contexto Spring: cinco em AdminDashboardControllerTest e vinte em AdminDashboardAgendamentosControllerTest. A configuração falhava antes de os casos verificarem os resultados HTTP.

Causa: os testes com @WebMvcTest carregavam uma parte restrita da aplicação e não forneciam um mock da nova dependência exigida pelo controller.

Correção e resultado: foram adicionados os mocks necessários. Os 25 casos foram executados novamente e aprovados. O relatório de 19/11 registra a aprovação posterior da suíte de 795 casos.

Contribuição para o grupo: a execução evidenciou que as verificações de respostas, parâmetros e autorização do dashboard estavam impedidas. A correção restabeleceu essas verificações e manteve os testes compatíveis com a evolução da aplicação. O incidente corresponde a erros na configuração dos testes, não a 25 defeitos de produção.

Evidência: seção 7.5 do relatório do backend.

https\://github.com/JoaoManoelFontes/PI-Wonder/blob/develop/docs/Estrategia-e-Relatorio-de-Testes.md

## 5.2 Falhas na integração com a identidade do Keycloak

Problema e detecção: na CI de 13/11, cinco casos de AdminUsuarioIntegrationTest falharam. Requisições que deveriam chegar às operações administrativas receberam HTTP 401; também houve cenários em que se esperava uma rejeição de negócio ou autorização, mas a autenticação falhou primeiro. O resumo da execução do core registrou 593 casos, cinco falhas de asserção e zero erros.

Causa: o client criado no Keycloak descartável dos testes não incluía o scope padrão basic, necessário naquele ambiente para fornecer o identificador sub usado pelo backend. Assim, o token não atendia ao contrato de identidade esperado pela aplicação.

Correção e resultado: a configuração do client de teste foi ajustada para incluir basic. Também foi acrescentada uma verificação explícita do identificador retornado pela introspecção. Os testes foram reexecutados e a CI posterior foi aprovada.

Contribuição para o grupo: os testes com Keycloak real revelaram uma incompatibilidade de configuração que simulações de usuário autenticado poderiam ocultar. A correção preservou a exigência de uma identidade válida e tornou a preparação das sessões de teste mais confiável.

Evidências:

CI com as cinco falhas: https\://github.com/JoaoManoelFontes/PI-Wonder/actions/runs/43446795071

Correção: https\://github.com/JoaoManoelFontes/PI-Wonder/commit/805dfac8bc4378f446597c0a0a951134b833c500

CI posterior aprovada: https\://github.com/JoaoManoelFontes/PI-Wonder/actions/runs/43451239911

## 5.3 Impedimento de execução identificado pela pipeline

Problema e detecção: na primeira tentativa da CI do PR 121, em 23/11, o download do Maven não foi concluído e a etapa terminou com código de saída 1\. A suíte de testes não chegou a ser executada nessa tentativa.

Causa observada: falha na obtenção da ferramenta necessária ao build. Esse resultado não demonstrava defeito funcional no Wonder nem permitia considerar seus testes aprovados.

Correção e resultado: a execução foi repetida sem alteração no código da funcionalidade. A segunda tentativa terminou com BUILD SUCCESS e 888 testes aprovados: 761 no core, 63 no notification e 64 no worker, sem falhas, erros ou ignorados.

Contribuição para o grupo: o registro da pipeline permitiu separar um impedimento de ambiente de um problema no produto. Isso evitou alterações desnecessárias na implementação e forneceu uma execução efetiva para sustentar os números do relatório.

Evidências:

Primeira tentativa: https\://github.com/JoaoManoelFontes/PI-Wonder/actions/runs/45179849689/attempts/1

Segunda tentativa: https\://github.com/JoaoManoelFontes/PI-Wonder/actions/runs/45179849689/attempts/2

## 5.4 Síntese da contribuição para a equipe

Esses episódios mostram que o valor dos testes também está no diagnóstico: identificar onde o fluxo falhou, verificar a causa e comprovar a correção. As evidências ajudaram a manter os contratos de segurança e HTTP, melhorar a configuração dos testes e distinguir problemas de infraestrutura. Novos incidentes encontrados no Cypress deverão seguir o mesmo registro: comportamento esperado, resultado observado, causa confirmada, correção, reteste e impacto para os usuários e para a equipe.

# 6 Integração contínua e automação

Descrever o que cada pipeline realmente executa e suas condições de disparo. O backend executa build e suíte Maven em PRs. Na conferência de 23/11, a CI do repositório frontend ainda validava componentes Python e o gateway legado; os testes atuais do Expo foram executados localmente.

Preencher: integração dos testes do aplicativo, Cypress, cobertura e análise estática; geração e armazenamento de relatórios; links de execução; configuração do ambiente publicado e deploy, conforme o escopo da entrega.

# 7 Conclusão

Preencher após consolidar os resultados: fluxos demonstrados, riscos remanescentes, limitações, critérios atendidos e atividades pendentes. Não apresentar um cenário planejado como aprovado.

Pendências atuais para acompanhamento: implementar e executar Cypress; configurar e medir cobertura e mutação; organizar o teste de carga, consolidar as evidências e definir a divisão dessas atividades entre os integrantes.

# REFERÊNCIAS E ANEXOS

**Estratégia e relatório do backend:**

https\://github.com/JoaoManoelFontes/PI-Wonder/blob/develop/docs/Estrategia-e-Relatorio-de-Testes.md

Backend:

https\://github.com/JoaoManoelFontes/PI-Wonder

Aplicativo:

https\://github.com/llwkascarvalho/wonder-app

Adicionar: plano de testes inicial, requisitos atuais, orientações dos professores, relatórios das ferramentas, screenshots, vídeos e registros dos incidentes.

