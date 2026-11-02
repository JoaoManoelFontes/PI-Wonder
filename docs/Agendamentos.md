# Criação e listagem de agendamentos

Os endpoints de criação e listagem exigem um JWT válido. As consultas públicas de dias e horários disponíveis continuam sem exigir autenticação.

## Criar agendamento

`POST /agendamentos` recebe:

```json
{
  "prestador_id": 1,
  "servico_id": 1,
  "inicio": "2099-01-05T09:00:00"
}
```

A criação retorna `201 Created` e inicia o agendamento com status `pendente`. O horário deve ser futuro, pertencer à grade de atendimento do prestador e corresponder a um serviço ativo do próprio prestador.

O fluxo bloqueia o registro do prestador durante a validação e verifica sobreposição com agendamentos pendentes ou confirmados. Assim, duas requisições concorrentes não podem reservar o mesmo intervalo.

Respostas relevantes:

- `400 Bad Request`: corpo inválido, identificadores não positivos ou horário no passado;
- `404 Not Found`: prestador ou serviço ativo não encontrado;
- `409 Conflict`: horário fora da grade ou já ocupado.

## Listar agendamentos

`GET /agendamentos` retorna os registros em ordem decrescente de início:

- cliente: somente os próprios agendamentos;
- prestador: somente os agendamentos do estabelecimento vinculado ao usuário;
- administrador: todos os agendamentos.

O parâmetro opcional `data`, no formato `AAAA-MM-DD`, restringe o resultado ao dia informado:

```http
GET /agendamentos?data=2099-01-05
```

A resposta mantém o contrato consumido pelo aplicativo:

```json
[
  {
    "id": 10,
    "cliente_id": "10000000-0000-0000-0000-000000000002",
    "prestador_id": 1,
    "servico_id": 1,
    "inicio": "2099-01-05T09:00:00",
    "status": "pendente",
    "cliente_nome": null,
    "cliente_foto_url": null
  }
]
```

## Alterar status e cancelar

`PATCH /agendamentos/{agendamento_id}/status` exige JWT e mantém o corpo utilizado pelo aplicativo:

```json
{
  "status": "cancelado",
  "motivo": "Não poderei comparecer"
}
```

| De | Para | Quem pode |
| --- | --- | --- |
| `pendente` | `confirmado` | Prestador dono com papel `PROVIDER` ou administrador. |
| `pendente` ou `confirmado` | `cancelado` | Cliente dono, prestador dono com papel `PROVIDER` ou administrador. |
| `confirmado` | `concluido` | Prestador dono com papel `PROVIDER` ou administrador. |

As regras seguem o [plano de migração](https://app.notion.com/p/Plano-migra-o-7271ec66440c82b08ed381dccd9fae3f). Não é permitida conclusão manual diretamente de `pendente`, reabertura de estados finais nem repetição da mesma transição. A listagem continua sem alterar status automaticamente.

Cancelamento exige motivo não vazio (até 1.000 caracteres) e pelo menos 15 minutos de antecedência: exatamente 15 minutos são aceitos; abaixo disso, o retorno é `409`. O motivo é salvo sem espaços nas extremidades.

O sucesso retorna `200` com o mesmo `AgendamentoResponse` da criação/listagem, incluindo o novo status em minúsculas. Corpo ou motivo inválido retorna `400`; JWT ausente/inválido, `401`; usuário sem permissão, `403`; agendamento inexistente, `404`; transição inválida ou cancelamento sem antecedência, `409`.

Cada mudança registra status anterior, novo status, motivo, instante e UUID Keycloak do autor em `historico_agendamentos`. A migration V8 cria essa tabela. Status e histórico são gravados na mesma transação, com bloqueio do agendamento para serializar alterações concorrentes. Um cancelamento remove a reserva dos intervalos que bloqueiam disponibilidade, preservando o registro e o histórico.

A publicação de eventos e o job de conclusão automática pertencem às próximas tarefas; esta rota realiza somente as transições explícitas.

Com Java 21 e Docker em execução:

```bash
./mvnw -pl core -Dtest=AgendamentoStatusServiceTest,AgendamentoStatusIntegrationTest,AgendamentoCriacaoListagemIntegrationTest test
```

Os testes cobrem permissões, transições, motivo, antecedência, histórico, liberação de disponibilidade e dois cancelamentos concorrentes. Os casos de limite temporal usam relógio simulado; a integração utiliza PostgreSQL via Testcontainers e JWT simulado via MockMvc, sem login real no Keycloak ou navegação no aplicativo.

## Validar conflitos na criação

Com Java 21 e Docker em execução:

```bash
./mvnw -pl core -Dtest=AgendamentoCriacaoListagemIntegrationTest,AgendamentoDisponibilidadeIntegrationTest,AgendamentoDiasDisponiveisIntegrationTest test
```

O teste de criação consulta um horário livre, realiza uma reserva e verifica que a segunda tentativa recebe `409`, mesmo que a consulta anterior tenha mostrado disponibilidade. Uma nova consulta já exclui o horário ocupado.

Os cenários concorrentes disparam duas requisições com clientes diferentes e transações independentes contra o PostgreSQL do Testcontainers. Para serviços de 60 e 30 minutos do mesmo prestador, verificam início igual, sobreposição parcial e horários adjacentes. Nos dois primeiros casos, apenas uma reserva é salva; no último, ambas são permitidas. Também são conferidos cliente, serviço, início, fim e status dos registros persistidos.

As requisições passam pelos controllers e regras reais via MockMvc, com autenticação JWT simulada pelo Spring Security Test. Essa validação não inclui login real no Keycloak nem navegação no aplicativo.
