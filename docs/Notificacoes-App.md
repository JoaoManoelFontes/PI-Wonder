# Notificações no aplicativo

O Wonder gera notificações assíncronas para cliente e prestador a partir dos eventos de agendamento.
O `core` publica o evento na exchange direta `wonder.events`, usando a routing key
`appointment.notification`, somente depois que a transação principal é confirmada. Falhas na publicação
são registradas sem desfazer o agendamento.

O módulo `notification` consome a fila durável `appointment.notifications`, transforma o evento em mensagens
para os participantes e persiste os avisos na tabela `notificacoes`. A chave do evento, o usuário e o tipo
formam uma restrição única para que uma repetição da mensagem no RabbitMQ não duplique o aviso. Eventos
inválidos ou falhas no processamento seguem para `appointment.notifications.dlq`, sem requeue automático.

## Situações notificadas

- criação: cliente e prestador;
- confirmação: cliente;
- cancelamento pelo cliente: cliente e prestador;
- cancelamento pelo prestador ou administrador: cliente;
- conclusão: cliente;
- lembrete de atendimento confirmado nas próximas 24 horas: cliente e prestador.

A rotina de lembretes executa a cada hora por padrão. O cron pode ser alterado por
`wonder.notifications.reminder-cron`.

## API autenticada

### Listar notificações

```http
GET /notificacoes
GET /notificacoes?status=pendente
GET /notificacoes?status=lida
```

A consulta usa exclusivamente o `sub` do JWT e nunca aceita o identificador de outro usuário como parâmetro.
Os resultados são retornados do mais recente para o mais antigo.

```json
[
  {
    "id": 10,
    "usuario_id": "427942e9-346d-4d74-bb06-808a87d5311f",
    "tipo": "agendamento_confirmado",
    "mensagem": "Seu agendamento de Corte em Studio Wonder foi confirmado pelo prestador.",
    "status": "pendente",
    "criado_em": "2026-11-15T18:00:00Z"
  }
]
```

Status desconhecido retorna `400`. Sem autenticação, a resposta é `401`.

### Marcar como lida

```http
PATCH /notificacoes/10/lida
```

A operação é idempotente e retorna o aviso atualizado. Uma notificação inexistente ou pertencente a outro
usuário retorna `404`, evitando expor dados de terceiros.
