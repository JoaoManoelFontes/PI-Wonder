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

A alteração de status, o histórico e a publicação de eventos pertencem às próximas tarefas do fluxo de agendamentos.

## Validar conflitos na criação

Com Java 21 e Docker em execução:

```bash
./mvnw -pl core -Dtest=AgendamentoCriacaoListagemIntegrationTest,AgendamentoDisponibilidadeIntegrationTest,AgendamentoDiasDisponiveisIntegrationTest test
```

O teste de criação consulta um horário livre, realiza uma reserva e verifica que a segunda tentativa recebe `409`, mesmo que a consulta anterior tenha mostrado disponibilidade. Uma nova consulta já exclui o horário ocupado.

Os cenários concorrentes disparam duas requisições com clientes diferentes e transações independentes contra o PostgreSQL do Testcontainers. Para serviços de 60 e 30 minutos do mesmo prestador, verificam início igual, sobreposição parcial e horários adjacentes. Nos dois primeiros casos, apenas uma reserva é salva; no último, ambas são permitidas. Também são conferidos cliente, serviço, início, fim e status dos registros persistidos.

As requisições passam pelos controllers e regras reais via MockMvc, com autenticação JWT simulada pelo Spring Security Test. Essa validação não inclui login real no Keycloak nem navegação no aplicativo.
