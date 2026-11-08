# Notificação do resultado da solicitação de prestador

O `notification` declara e consome a fila durável `provider_request_reviewed`, vinculada à exchange direta
`wonder.events` pela routing key `provider_request_reviewed`. A fila também é declarada no `core`.

Contrato JSON (`ProviderRequestReviewed` nos dois módulos):

```json
{
  "prestadorId": 42,
  "destinatario": "prestador@example.com",
  "nomeEstabelecimento": "Salão São José",
  "resultado": "REJEITADO",
  "motivoRejeicao": "Informe o endereço completo."
}
```

`resultado` aceita `APROVADO` ou `REJEITADO`. Na aprovação, `motivoRejeicao` pode ser nulo ou omitido.
Na recusa, deve conter texto. O identificador deve ser positivo e o destinatário e nome devem conter texto.
O consumer seleciona o template de texto simples e envia um email ao destinatário informado.
Mensagens inválidas e falhas de SMTP seguem para `provider_request_reviewed.dlq`, sem requeue automático,
conforme a configuração existente do módulo.

O `PrestadorService` chama `RabbitProvider.sendProviderRequestReviewed` após o commit nas transições
`PENDENTE → ATIVO` e `PENDENTE → REJEITADO`. Correção, suspensão e reativação não geram esse evento.
O email é consultado pelo `keycloakId` após o commit, via `KeycloakAdminService`; a autenticação,
consulta HTTP e validação do email ficam no `KeycloakClient`. Na recusa, o evento leva o motivo já normalizado.
Falhas de consulta (incluindo email ausente) e de publicação são registradas em log e preservam a decisão.
A publicação após commit, isoladamente, não garante recuperação de eventos que falhem antes de chegar ao RabbitMQ.

Os testes unitários cobrem templates, validação, propagação de falhas e publicação JSON.
`EmailServiceIntegrationTest` usa RabbitMQ e MailHog via Testcontainers para validar consumo, SMTP e DLQ.
