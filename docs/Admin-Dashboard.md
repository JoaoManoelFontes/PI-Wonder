# Indicadores do dashboard administrativo

`GET /admin/dashboard/indicadores` exige JWT válido com papel `ADMIN` e fornece os dados dos quatro cards em uma única chamada, sem parâmetros.

Exemplo de resposta `200 OK` (valores ilustrativos):

```json
{
  "total_usuarios": 1248,
  "total_prestadores": 86,
  "prestadores_pendentes": 7,
  "atendimentos_concluidos_mes": 342,
  "mes_referencia": "2026-11"
}
```

| Campo | Regra |
| --- | --- |
| `total_usuarios` | Contas humanas cadastradas no realm configurado do Keycloak, de todos os papéis, inclusive desabilitadas e sem perfil local. Uma conta com vários papéis entra uma vez. Contas técnicas de serviço ficam fora. |
| `total_prestadores` | Todos os registros da tabela de prestadores, inclusive rascunhos, pendentes, rejeitados, suspensos e inativos. |
| `prestadores_pendentes` | Prestadores com status `PENDENTE`, sem filtro adicional de `ativo`, como na listagem administrativa de pendências. Cadastros devolvidos para `RASCUNHO` ficam fora. |
| `atendimentos_concluidos_mes` | Agendamentos com status atual `CONCLUIDO` e início no mês atual de São Paulo. |
| `mes_referencia` | Mês usado na contagem, no formato `YYYY-MM`. |

O mês é determinado uma vez por requisição no fuso `America/Sao_Paulo`, independentemente do fuso do servidor. O intervalo inclui o primeiro dia às 00:00 e exclui o primeiro dia do mês seguinte às 00:00. O campo `inicio` dos agendamentos representa horário local, sem conversão para UTC na consulta.

O indicador mensal usa o mês do início do atendimento. Um atendimento iniciado em outubro e marcado como concluído em novembro pertence a outubro, mesmo que termine em novembro. Pendentes, confirmados e cancelados não entram. No instante `2026-12-01T01:00:00Z`, a referência ainda é `2026-11` em São Paulo.

O frontend recebe números brutos, inclusive zero quando a população correspondente está vazia, e formata os milhares. O título recomendado para o card mensal é **Atendimentos concluídos no mês**, com legenda derivada de `mes_referencia`. Tabelas e exportação CSV não fazem parte desta rota.

## Erros e disponibilidade

- `401 Unauthorized`: JWT ausente ou inválido.
- `403 Forbidden`: usuário autenticado sem papel `ADMIN`; nenhuma fonte é consultada.
- `503 Service Unavailable`: falha HTTP, timeout, configuração inválida ou contagem ausente/inválida no Keycloak. A resposta não contém indicadores parciais e não substitui falhas por zero.

A integração reutiliza o client confidencial `wonder-core`, seu secret externalizado, permissões e timeouts existentes. Não é necessário adicionar dependências ou alterar o schema. As contagens refletem leituras independentes das fontes; não representam um snapshot distribuído entre Keycloak e PostgreSQL.

## Validação

Na raiz do repositório:

```bash
./mvnw -pl core -Dtest=AdminDashboardServiceTest,AdminDashboardControllerTest,KeycloakClientTest,KeycloakAdminServiceTest,AdminDashboardIntegrationTest,KeycloakAdminIntegrationTest test
```

Os testes unitários verificam o cálculo mensal, inclusive ano bissexto e virada de mês em UTC, contrato da integração e propagação de falhas. O teste HTTP do controller verifica o contrato JSON e bloqueia acessos sem `ADMIN` antes de chamar o service.

Os testes de integração exigem Docker. `AdminDashboardIntegrationTest` exercita controller, services e repositórios reais com PostgreSQL do Testcontainers e Keycloak simulado no limite HTTP. Verifica contagens, limites mensais, estados, dados vazios e respostas de erro. `KeycloakAdminIntegrationTest` usa Keycloak 26.0 real com o realm do projeto para verificar contas sem perfil local, vários papéis, contas desabilitadas e exclusão da service account.

Validação da implementação: 72 testes da suíte acima aprovados, sem falhas ou testes ignorados. A configuração foi verificada com `docker compose config --quiet`; a verificação HTTP ocorreu nos ambientes isolados do Testcontainers, sem smoke manual sobre os dados locais do Compose.

## Detalhamento de agendamentos por período

`GET /admin/dashboard/agendamentos` exige JWT válido com papel `ADMIN`. Retorna somente agregados, sem nomes, identificadores ou registros individuais. A consulta é síncrona no `core` e usa uma única agregação no PostgreSQL, sem consultar a API administrativa do Keycloak. O contrato dos quatro cards permanece o mesmo.

| Parâmetro | Regra |
| --- | --- |
| `data_inicio` | Primeiro dia inclusivo em `YYYY-MM-DD`. |
| `data_fim` | Último dia inclusivo em `YYYY-MM-DD`. |

Enviar ambas as datas ou omitir ambas. Sem parâmetros, o período é o mês atual completo em `America/Sao_Paulo`, inclusive dias futuros. O servidor devolve as datas efetivas. Início igual ao fim é válido. Datas vazias, malformadas, inexistentes, apenas uma data ou início posterior ao fim retornam `400`, antes da consulta ao banco. Os anos seguem o formato de quatro dígitos, de `0001` a `9999`.

Exemplo de período explícito:

```http
GET /admin/dashboard/agendamentos?data_inicio=2026-11-01&data_fim=2026-11-02
```

```json
{
  "data_inicio": "2026-11-01",
  "data_fim": "2026-11-02",
  "total_agendamentos": 5,
  "media_por_dia": 2.50,
  "totais_por_status": {
    "pendente": 1,
    "confirmado": 1,
    "cancelado": 1,
    "concluido": 2
  },
  "dias": [
    {
      "data": "2026-11-01",
      "total_agendamentos": 5,
      "totais_por_status": {
        "pendente": 1,
        "confirmado": 1,
        "cancelado": 1,
        "concluido": 2
      }
    },
    {
      "data": "2026-11-02",
      "total_agendamentos": 0,
      "totais_por_status": {
        "pendente": 0,
        "confirmado": 0,
        "cancelado": 0,
        "concluido": 0
      }
    }
  ]
}
```

Cada agendamento conta uma vez pelo dia local de `inicio`, classificado no status atual. Cancelados entram no total geral. Data de criação, término e alteração de status não definem o período: um atendimento iniciado em outubro e concluído em novembro entra como concluído em outubro. A distribuição pode mudar após alterações de status; não é um retrato histórico imutável.

A consulta inclui o primeiro dia às 00:00 e exclui o dia seguinte ao último às 00:00, sem converter `inicio` para UTC. Os totais gerais e por status são calculados a partir da mesma agregação dos dias. `dias` está em ordem crescente, com todos os dias e os quatro status, inclusive zeros. A série é completa, sem paginação. Não há limite adicional de duração do intervalo; intervalos extensos produzem mais linhas na resposta.

`media_por_dia` é o total dividido pela quantidade inclusiva de dias, com duas casas decimais e arredondamento `HALF_UP`. Inclui dias vazios e futuros. Por exemplo, 15 agendamentos em novembro completo resultam em média `0.50`, mesmo antes do fim do mês. A média é um número JSON; a interface formata as casas decimais. Um período vazio retorna `200` com total, média e todos os dias zerados. Falhas de consulta não são substituídas por zeros.

Respostas relevantes: `200` para consulta válida, `400` para intervalo inválido, `401` para JWT ausente/inválido e `403` para usuário sem `ADMIN`. A autorização bloqueia o acesso antes da agregação. Não há novas tabelas, dependências ou migrations para esse detalhamento.

## Contrato da futura tela de agendamentos

A API está implementada; a interface será feita em outra mudança. Ao clicar no card **Atendimentos concluídos no mês**, abrir **Agendamentos**, com ação para voltar e todos os status separados. Usar `mes_referencia` do card para enviar explicitamente o primeiro e o último dia desse mês, preservando a referência mesmo se a navegação atravessar a virada mensal.

| Filtro | Intervalo enviado à API |
| --- | --- |
| Mês | Mês completo selecionado; seletor de mês/ano e navegação anterior/próximo. |
| 7 dias | Hoje em São Paulo menos 6 dias até hoje, ambos inclusivos. |
| 15 dias | Hoje em São Paulo menos 14 dias até hoje, ambos inclusivos. |
| Personalizado | Ambas as datas preenchidas e validadas antes de aplicar; permite o mesmo dia. |

Os atalhos móveis independem do mês selecionado e podem atravessar meses/anos. Em `2026-12-03`, **7 dias** consulta `2026-11-27` a `2026-12-03`. Em `2026-11-13`, **15 dias** consulta `2026-10-30` a `2026-11-13`. O frontend calcula hoje no fuso de São Paulo e envia as datas. Ao retornar ao modo **Mês**, preservar o último mês escolhido, inicialmente o do card. A navegação mensal atua somente nesse modo; dezembro seguido de próximo seleciona janeiro do ano seguinte. Meses futuros são permitidos.

Mostrar sempre o intervalo efetivo, o total de agendamentos, a média por dia e os indicadores informativos **Pendentes**, **Confirmados**, **Cancelados** e **Concluídos**. O total geral pode diferir do card, que conta apenas concluídos. Para o mesmo mês e dados estáveis, `totais_por_status.concluido` corresponde ao card. Os indicadores de status não aplicam filtros por clique nesta entrega.

A tabela terá uma linha por dia, inclusive dias zerados, com colunas **Data | Total | Pendentes | Confirmados | Cancelados | Concluídos** e rodapé com os totais do período. A tabela pode usar rolagem. O gráfico diário da referência pode usar `dias[].data` e `dias[].total_agendamentos`, sem outra consulta ou cálculo independente dos totais.

Resumo, status, tabela e eventual gráfico usam o mesmo filtro. Prever carregamento, período sem agendamentos, erro com ação de tentar novamente e descarte de respostas antigas após mudança de filtro. Um erro não deve aparecer como período zerado nem manter números antigos como se fossem do novo intervalo.

Frontend, CSV e as demais telas estão fora desta entrega. Não apresentar botão de exportação ainda. A exportação futura seguirá o fluxo `core -> RabbitMQ -> worker` e será tratada em mudança própria, incluindo o relatório exportável exigido pelo Projeto Integrador.

## Testes do detalhamento

Na raiz, com Java 21, Maven e Docker disponíveis:

```bash
mvn -pl core -Dtest=AdminDashboardAgendamentosServiceTest,AdminDashboardAgendamentosControllerTest,AdminDashboardAgendamentosIntegrationTest,AdminDashboardServiceTest,AdminDashboardControllerTest,AdminDashboardIntegrationTest test
```

`AdminDashboardAgendamentosServiceTest` verifica resumo, dias ausentes, média e arredondamento, intervalo de um dia, limites inválidos, fevereiro bissexto, viradas de mês/ano e propagação de falhas. `AdminDashboardAgendamentosControllerTest` usa MockMvc com controller, service e segurança reais, repositórios simulados e valida o contrato, parâmetros, `401`/`403` e ausência de consultas para entradas inválidas ou acesso negado.

`AdminDashboardAgendamentosIntegrationTest` usa aplicação completa e MockMvc com PostgreSQL real via Testcontainers. Verifica a projeção SQL, contagem pelos quatro status, limites com frações de segundo, dias zerados, conclusão em outro mês, calendário e correspondência com o card. A API administrativa do Keycloak é simulada; JWTs são fornecidos pelo Spring Security Test. Não há login real ou navegação no frontend nesses testes.

Validação em 13/11/2026: os seis testes/classes do comando acima totalizaram **73 casos aprovados**, sem falhas, erros ou testes ignorados: 48 casos do detalhamento e 25 de regressão dos indicadores. Execução com Java 21, Maven em modo offline (`-o`) usando o cache local e Docker/Testcontainers. A validação estrita da change OpenSpec também foi aprovada.
