# Medições de mutação dos status de agendamentos

Primeira medição: 25 de novembro de 2026. Atualização e comparação: 27 de novembro de 2026.

Código de produção: `7b58c4f47a423799a5f8bee4c986f3a3e1c3bb1a` (`develop`). A configuração adicional está no perfil Maven `pit-status` do módulo `core`. Nenhuma regra de negócio ou teste existente foi alterado nesta medição.

## Objetivo e escopo

Verificar a capacidade do conjunto unitário selecionado de detectar alterações incorretas na atualização de status. Foram selecionados `AgendamentoService.atualizarStatus`, `interpretarStatus` e a função gerada pelo compilador para o tratamento de agendamento inexistente.

O conjunto executado foi `AgendamentoStatusServiceTest`, com um método parametrizado e três entradas: 899, 900 e 901 segundos de antecedência para cancelar. O PIT verificou inicialmente os testes contra o código original antes de executar as mutações.

Criação, listagem, disponibilidade, validação de horários e implementação da publicação de notificações ficaram fora do alvo de mutação. A chamada de notificação feita por `atualizarStatus` continua dentro do alvo. Os testes de integração existentes não foram selecionados. Portanto, esta medição não representa a eficácia da suíte completa de agendamentos nem de todo o backend.

## Ambiente e reprodução

- Java 21.
- PIT Maven 1.30.2 e plugin JUnit 1.2.3, com operadores padrão da versão.
- Uma thread e limite de 384 MB para a JVM dos testes.
- Testes unitários com Mockito, sem Docker ou serviços externos.
- A configuração JaCoCo existente foi preservada. A medição de mutação não substitui o relatório JaCoCo.

Na raiz do backend, com Java 21 configurado:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl core -Ppit-status test-compile org.pitest:pitest-maven:mutationCoverage
```

Em Linux/macOS, utilizar `./mvnw` no lugar de `.\mvnw.cmd`.

O comando gera `core/target/pit-reports/index.html` e `mutations.xml`. Ele executa o conjunto selecionado pelo PIT; não executa a suíte completa de integração nem a fase Maven `verify`. O perfil é opcional e não inclui uma nova barreira de aprovação na CI.

## Resultado da primeira medição — 25/11/2026

| Categoria | Quantidade |
| --- | ---: |
| Mutantes gerados | 13 |
| Eliminados | 5 |
| Sobreviventes | 5 |
| Sem cobertura no conjunto selecionado | 3 |
| Timeout e demais erros | 0 |

O relatório apresenta **38% de cobertura de mutação**, correspondente a 5/13 (38,46% antes do arredondamento), e **50% de força dos testes**, correspondente a 5/10 entre os mutantes cobertos. O tempo total Maven da execução final foi de 54,429 segundos; a análise PIT levou 34 segundos.

O PIT também apresenta 48/115 linhas (42%) para a classe analisada. Essa contagem inclui linhas de métodos fora do alvo de mutação e não deve ser apresentada como cobertura do recorte de status, nem como cobertura JaCoCo do backend.

As 18 execuções de testes informadas pelo PIT incluem repetições contra mutantes; não são 18 casos únicos acrescentados ao projeto.

`BUILD SUCCESS` significa que a ferramenta concluiu a análise. Não significa que todos os mutantes foram detectados ou que uma meta de qualidade foi atingida. Nenhuma meta de mutação foi definida nesta primeira medição.

## Exemplos e interpretação

| Alteração introduzida pelo PIT | Resultado | Interpretação |
| --- | --- | --- |
| Remover a chamada `setStatus`, na linha 98 | Eliminado | A asserção do status retornado identificou que a alteração esperada não ocorreu. |
| Substituir o retorno de `atualizarStatus` por `null`, na linha 109 | Eliminado | O teste exige um agendamento com o status esperado. |
| Remover a chamada `notificarAposCommit`, na linha 107 | Sobrevivente | O conjunto selecionado verifica o estado, mas não exige essa chamada ou seu evento. |
| Inverter condições de autorização e de identificação do autor, nas linhas 69, 70, 71 e 108 | Quatro sobreviventes, dois eliminados e dois sem cobertura | Os três casos selecionados usam o cliente proprietário cancelando; não abrangem as demais combinações de papéis e autoria. Cada mutante deve ser analisado individualmente antes de uma conclusão sobre sua equivalência. |
| Alterar o retorno da função usada quando o agendamento não existe, na linha 66 | Sem cobertura | Não há cenário de agendamento inexistente nesse conjunto unitário selecionado. |

Os sobreviventes evidenciam limitações deste conjunto selecionado. Não comprovam, isoladamente, que o comportamento está sem testes no restante do projeto ou que há um defeito no código de produção.

## Ajuste do escopo durante a preparação

A primeira configuração incluiu quatro mutantes de funções geradas para `validarHorarioDeAtendimento`, embora esse fluxo estivesse fora do recorte declarado. O filtro foi corrigido para excluir essas funções e a análise foi repetida. O relatório anterior foi preservado como evidência de preparação. A diferença entre os totais das duas configurações não representa melhoria dos testes: o código de produção e os testes permaneceram iguais.

## Próximos cenários a avaliar

Na primeira medição, ficou indicada a ampliação da seleção unitária para incluir autorização de prestador e administrador, rejeição de terceiros, transições válidas e inválidas, agendamento inexistente e verificação do evento de notificação. A execução dessas melhorias está registrada a seguir.

A primeira medição fornece uma evidência concreta para o relatório de qualidade: testes que verificam um limite de tempo podem proteger a mudança de estado e ainda deixar outros efeitos e condições sem verificação no conjunto selecionado.

## Ampliação dos testes e segunda medição — 27/11/2026

A `develop` foi conferida novamente antes do trabalho e permaneceu em `7b58c4f`. Nenhum código de produção foi alterado. `AgendamentoStatusServiceTest` passou de três para 46 casos, preservando os valores-limite originais e acrescentando:

- Onze combinações válidas de estado e autor, verificando resultado, histórico e conteúdo do evento de notificação.
- Onze tentativas sem permissão, incluindo terceiros com papel de prestador e o proprietário sem o papel exigido.
- Doze transições inválidas, inclusive tentativas administrativas de reabrir estados finais.
- Quatro entradas de motivo ausente ou em branco para cancelamento.
- Três status desconhecidos, um agendamento inexistente e um cenário de normalização de status com confirmação sem motivo.

Além das respostas de erro, os cenários rejeitados verificam a preservação do estado e a ausência de gravação de histórico e emissão de evento. O teste de notificação verifica a chamada ao colaborador simulado e o conteúdo do evento; não comprova entrega real pelo RabbitMQ ou recebimento pelo aplicativo.

Primeiro foram executados os testes no código original:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl core -Dtest=AgendamentoStatusServiceTest test
```

Resultado: **46 aprovados, zero falhas, erros ou ignorados**. A execução local utilizou limite de memória de 384 MB na JVM dos testes e levou 25,082 segundos no Maven.

Depois foi repetido o comando PIT registrado anteriormente, sem alterar versão, operadores, classes ou filtros. A identidade dos 13 mutantes foi conferida nos XMLs das duas medições.

| Medida | 25/11 — conjunto inicial | 27/11 — conjunto ampliado |
| --- | ---: | ---: |
| Casos unitários selecionados | 3 | 46 |
| Mutantes gerados | 13 | 13 |
| Eliminados | 5 | 13 |
| Sobreviventes | 5 | 0 |
| Sem cobertura | 3 | 0 |
| Cobertura de mutação no relatório PIT | 38% | 100% |
| Força dos testes | 50% | 100% |
| Timeout e demais erros de mutação | 0 | 0 |

A segunda execução PIT concluiu com `BUILD SUCCESS`, tempo Maven de 17,510 segundos e análise PIT de dez segundos. As 20 execuções informadas pelo PIT são escolhas e repetições internas contra mutantes; não substituem a contagem de 46 casos unitários executados antes da análise.

Exemplos de impacto: a remoção da chamada de notificação, antes sobrevivente, passou a falhar diante da verificação do evento. As inversões de condições de autorização e autoria passaram a ser detectadas pelos diferentes papéis e vínculos. O cenário de registro inexistente exercitou a função antes sem cobertura.

Os 100% se referem somente aos 13 mutantes gerados neste recorte e com esses operadores. Não representam cobertura total de linhas, todas as formas possíveis de defeito, toda a classe ou o backend. A suíte completa de integração não foi executada localmente nesta atividade; essa validação permanece com a CI do PR.

As evidências das duas medições foram preservadas separadamente em relatórios HTML/XML e logs. O perfil PIT continua opcional, sem alterar os mínimos JaCoCo existentes ou os fluxos de produção.
