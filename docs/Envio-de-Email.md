# Envio de email no notification

O `EmailService` envia por SMTP usando o `JavaMailSender` configurado pelo Spring Boot.
O evento `provider_request_created` substitui a rota genérica `notification.requested`.

## Solicitação de prestador

O `core` publica JSON no exchange direto `wonder.events`, com routing key
`provider_request_created`. A fila durável tem o mesmo nome.

```json
{
  "destinatarios": ["admin1@example.com", "admin2@example.com"],
  "prestador": {
    "id": 42,
    "nomeEstab": "Salão São José",
    "documento": "12345678900",
    "cidade": "Natal",
    "estado": "RN"
  }
}
```

O publicador `RabbitProvider.sendProviderRequestCreated(ProviderRequestCreated)` recebe
esse contrato. Os DTOs locais dos dois módulos têm o mesmo formato JSON, sem dependência
entre aplicações. O `PrestadorService` dispara o evento após o commit do cadastro inicial
ou do reenvio para aprovação, quando o prestador entra em `PENDENTE`. O `core` consulta
os usuários da role de realm `ADMIN` no Keycloak, com paginação, usando o service account
existente. Emails ausentes ou em branco são ignorados; espaços nas extremidades e
endereços repetidos são removidos antes de montar o payload.

Sem destinatários, nenhum evento é publicado e um aviso é registrado. Falhas na consulta
ao Keycloak ou na publicação RabbitMQ são registradas após o commit, preservando o cadastro.
Não há reenvio automático nem garantia de atomicidade entre banco e RabbitMQ; uma falha
após o commit pode deixar a solicitação sem notificação. A consulta e a publicação ainda
ocorrem na thread da requisição; o envio SMTP é assíncrono no `notification`.

O documento é opcional no cadastro HTTP e no evento. Quando ausente, nulo, vazio ou
composto apenas por espaços em branco, o email apresenta `Documento: Não informado`.
A ausência de documento não rejeita a notificação. Cidade e estado continuam obrigatórios,
conforme a validação atual do cadastro HTTP.

O `NotificationListener` converte o JSON, renderiza
`notification/src/main/resources/templates/provider_request_created.txt` e chama
`EmailService.enviarEmLote`. Cada destinatário recebe um email separado; endereços idênticos
repetidos são enviados uma única vez. A lista deve conter pelo menos um endereço; os dados
do prestador são obrigatórios, exceto o documento. O template é texto simples em UTF-8 e informa que
uma nova solicitação foi criada, mostra os dados do prestador e orienta a acessar o aplicativo
para analisar, aprovar ou rejeitar o cadastro.

Erros de conversão, validação ou SMTP rejeitam o evento e o encaminham para a fila durável
`provider_request_created.dlq`. Não há reenvio automático. Antes de republicar uma mensagem,
confira o erro: falhas parciais de SMTP podem ter entregado alguns emails.
A antiga fila `wonder.notification`, se existir no broker, não é apagada automaticamente
e deixa de ser consumida por esta aplicação. O fluxo de relatórios do worker mantém o contrato em texto.

## Conteúdo e templates

Internamente, o serviço SMTP recebe conteúdo **já renderizado** em `EmailRequest`:

```json
{
  "destinatario": "cliente@example.com",
  "assunto": "Confirmação de agendamento",
  "texto": "Olá, Ana! Seu agendamento foi confirmado.",
  "html": "<p>Olá, Ana! Seu agendamento foi <strong>confirmado</strong>.</p>"
}
```

`destinatario`, `assunto` e `texto` são obrigatórios. `html` é opcional.
O serviço envia UTF-8 e, quando há HTML, inclui texto simples como alternativa MIME.
Não interpreta identificadores de template ou placeholders. Quem preparar o conteúdo
deve substituir variáveis e escapar dados dinâmicos antes de gerar HTML.
O exemplo JSON descreve o DTO; não define um novo evento de mensageria.

```java
emailService.enviar(new EmailRequest(
        "cliente@example.com", "Confirmação", "Seu horário foi confirmado.", null));

emailService.enviarEmLote(List.of(
        new EmailRequest("ana@example.com", "Confirmação", "Olá, Ana!", null),
        new EmailRequest("bia@example.com", "Confirmação", "Olá, Bia!", "<p>Olá, Bia!</p>")));
```

O lote usa uma única chamada `send(MimeMessage...)`, que permite ao `JavaMailSenderImpl`
reutilizar a conexão SMTP. Cada item vira um email separado com um único destinatário.
Todas as mensagens são preparadas antes do envio; uma entrada inválida impede o envio do lote.
Lista vazia não envia nada. O lote é mantido em memória: o chamador deve fornecer lotes finitos
de tamanho compatível com os limites do provedor SMTP.

Erros de preparação são propagados como `IllegalArgumentException` ou `MailParseException`.
Falhas de envio são propagadas como `MailException`. O envio SMTP não é transacional:
um erro durante o lote pode ocorrer depois de alguns emails terem sido aceitos.
`MailSendException.getFailedMessages()` identifica mensagens com falha quando disponível.
Não há retry automático; reenviar o lote inteiro pode duplicar mensagens já aceitas.

## MailHog local

No desenvolvimento com aplicações na IDE:

```powershell
docker compose --env-file .env.dev -f compose.dev.yaml up -d mailhog
```

Execute `notification` com `MAIL_HOST=localhost`, `MAIL_PORT=1025` (valores padrão).
O MailHog captura as mensagens; elas ficam visíveis em **http://localhost:8025**.
No Compose completo, `notification` usa `mailhog:1025` por padrão.
`MAILHOG_SMTP_PORT` e `MAILHOG_HTTP_PORT` alteram as portas publicadas no host;
se mudar a porta SMTP publicada, ajuste `MAIL_PORT` da aplicação executada na IDE.
Dentro do Compose, a porta SMTP do MailHog continua sendo 1025.

As variáveis estão documentadas em `.env.example` e `.env.dev.example`.
`MAIL_FROM` define o remetente. Para SMTP real, configure `MAIL_HOST`, `MAIL_PORT`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS_ENABLE`
e `MAIL_SMTP_STARTTLS_REQUIRED` conforme o provedor. Não versione credenciais.
Os timeouts de conexão, leitura e escrita são 5000 ms e podem ser alterados por
`MAIL_CONNECTION_TIMEOUT`, `MAIL_READ_TIMEOUT` e `MAIL_WRITE_TIMEOUT`.

## Testes

```powershell
# Unitários de publicação, template, listener e SMTP, sem Docker
mvn -pl core,notification '-Dtest=RabbitProviderTest,NotificationListenerTest,EmailServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test

# Consulta de administradores e disparo após commit
mvn -pl core '-Dtest=KeycloakClientTest,KeycloakAdminServiceTest,PrestadorServiceTest' test

# Consulta real de usuários ADMIN no Keycloak (requer Docker)
mvn -pl core '-Dtest=KeycloakAdminIntegrationTest' test

# Integração RabbitMQ -> template -> SMTP com containers isolados (requer Docker)
mvn -pl notification '-Dtest=EmailServiceIntegrationTest' test

# Todos os testes do módulo, incluindo o contexto existente com RabbitMQ
./mvnw.cmd -pl notification verify
```

Os testes de integração iniciam o contexto de `notification` com RabbitMQ e MailHog isolados.
Verificam a entrega individual e em lote por SMTP, o consumo do JSON publicado pelo core,
o conteúdo do template, a privacidade dos destinatários e o encaminhamento de eventos inválidos para a DLQ.
