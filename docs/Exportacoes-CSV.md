# Exportações CSV

O core prepara um snapshot filtrado e o worker gera CSV, armazena no MinIO privado e
publica a conclusão no RabbitMQ. O notification consome a conclusão e envia um e-mail
confirmando o sucesso, com o link para download do CSV e sua validade em horário de Brasília.

## Solicitação e contrato

As rotas administrativas existentes exigem ADMIN e mantêm os filtros das listagens:

- `POST /admin/usuarios/exportacoes`: `busca`, `papel`.
- `POST /admin/prestadores/exportacoes`: filtros da listagem de prestadores.
- `POST /admin/dashboard/agendamentos/exportacoes`: período do dashboard.

A resposta fornece `exportacao_id`. O core publica JSON no exchange direto
`wonder.events`, routing key `report.generation.requested`, fila durável
`wonder.report.generation`:

```json
{
  "exportacaoId": "f7cce9df-37bf-4ed0-8189-3d00eb540a13",
  "tipo": "USUARIOS",
  "email": "ana@example.com",
  "nome": "Ana Silva",
  "dados": {"usuarios": []}
}
```

Tipos: `USUARIOS`, `PRESTADORES`, `AGENDAMENTOS`. O worker usa somente esse snapshot,
sem consultar banco ou Keycloak e sem depender do header `__TypeId__` do core.
Lista vazia é válida; lista ausente, envelope incompleto ou estrutura inválida vão à DLQ.
Exemplos completos dos três JSONs estão em `worker/src/test/resources/contratos`;
os testes do core também verificam que sua publicação coincide com esses contratos.

## Formato dos arquivos

UTF-8 com BOM, separador `;`, registros CRLF e todas as células entre aspas. Aspas
internas são duplicadas; delimitadores e quebras internas permanecem na célula.
Nulos opcionais produzem células vazias. Textos externos iniciados por `=`, `+`, `-`,
`@`, tabulação ou quebra de linha, inclusive após espaços, recebem apóstrofo inicial.
Números tipados permanecem em dígitos sem agrupamento. A ordem do snapshot é preservada.
O cabeçalho é a primeira linha, sem título ou `sep=;`.

Usuários: `usuarios-<UUID>.csv`, com as seis colunas abaixo. O ID é UUID textual;
`ativo` vira Ativo/Inativo. Nome e sobrenome permanecem separados.

```csv
"ID do usuário";"Usuário";"Nome";"Sobrenome";"E-mail";"Situação"
"14128674-d423-4b67-91f5-dca790dc3940";"ana";"Ana São José";"";"ana@example.com";"Ativo"
```

Prestadores: `prestadores-<UUID>.csv`. Situações: Rascunho, Pendente, Ativo,
Rejeitado e Suspenso. Cadastro em `dd/MM/yyyy HH:mm:ss XXX`, America/Sao_Paulo;
`2026-11-01T02:30:00Z` aparece no dia anterior com offset -03:00. `foto_url` e
`usuario_id` não são exportados. O ID numérico identifica o prestador.

```csv
"ID do prestador";"Estabelecimento";"Responsável";"Situação";"Data de cadastro"
"42";"Salão ""Ana""; Centro";"";"Ativo";"31/10/2026 23:30:00 -03:00"
```

Agendamentos: `agendamentos-<data_inicio-ISO>-a-<data_fim-ISO>-<UUID>.csv`.
Datas em `dd/MM/yyyy`. Mantém cada dia recebido, inclusive dias zerados, e acrescenta
TOTAL com os totais recebidos, sem recalcular. Não há dados individuais, coluna/linha
de média (`media_por_dia`) nem metadados repetidos. Período sem movimento mantém os
dias com zeros e TOTAL zerado. Usuários/prestadores vazios geram somente cabeçalho.

```csv
"Data";"Total";"Pendentes";"Confirmados";"Cancelados";"Concluídos"
"01/11/2026";"2";"1";"1";"0";"0"
"02/11/2026";"0";"0";"0";"0";"0"
"TOTAL";"2";"1";"1";"0";"0"
```

## MinIO e ambiente

| Variável | Uso/padrão |
|---|---|
| `MINIO_ENDPOINT` | Upload interno, padrão `http://localhost:9000`; Compose: `http://minio:9000` |
| `MINIO_PUBLIC_ENDPOINT` | Endereço acessível pelo destinatário, padrão `http://localhost:9000` |
| `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` | Credenciais externalizadas |
| `MINIO_EXPORT_BUCKET` | Bucket privado dedicado, padrão `wonder-exportacoes` |
| `EXPORTACAO_LINK_TTL_HORAS` | Padrão 24, permitido de 1 a 168 |

O bucket é criado sem política pública. Use um bucket dedicado privado, sem conceder
leitura anônima. O upload usa stream de arquivo temporário, removido em sucesso ou falha.
Chave: `exportacoes/<tipo-minusculo>/<exportacaoId>.csv`. Reprocessamento sobrescreve a
mesma chave; nome/e-mail não integram o caminho.

O download tem `Content-Type: text/csv; charset=UTF-8` e
`Content-Disposition: attachment; filename="<nomeArquivo>"` no objeto e na URL assinada.
O link GET é assinado pelo cliente configurado com o endpoint público; nunca se troca o
host após assinar. Ajuste o endereço público para demonstração em outra máquina.
GET sem assinatura é negado. A expiração UTC acompanha a data da assinatura S3.
Expirar o link não apaga o objeto. Não há limpeza automática nem renovação de link;
para link expirado, solicite nova exportação. Quem possui o link pode utilizá-lo até expirar.

O Compose completo espera MinIO saudável antes de iniciar o worker. Para aplicações
na IDE, use a infraestrutura do `compose.dev.yaml` e configure as variáveis de
`.env.dev.example` na execução (o Spring não carrega esse arquivo automaticamente):

```powershell
docker compose --env-file .env.dev -f compose.dev.yaml up -d rabbitmq minio
```

O SDK MinIO é 9.0.3, com o mesmo suporte OkHttp JVM 5.3.2 usado no core. O JSON usa
Jackson gerenciado pelo Spring Boot, sem versão própria. Não há biblioteca CSV adicional.

## Evento de conclusão

Após upload e assinatura, o worker publica JSON (`application/json`, persistente) no
mesmo exchange, routing key `report.generation.completed`, fila durável exclusiva
`wonder.notification.exportacao`, com DLQ `wonder.notification.exportacao.dlq`:

```json
{
  "exportacaoId": "f7cce9df-37bf-4ed0-8189-3d00eb540a13",
  "tipo": "USUARIOS",
  "email": "ana@example.com",
  "nome": "Ana Silva",
  "nomeArquivo": "usuarios-f7cce9df-37bf-4ed0-8189-3d00eb540a13.csv",
  "arquivoUrl": "http://localhost:9000/wonder-exportacoes/exportacoes/usuarios/...?...",
  "expiraEm": "2026-11-21T12:00:00Z"
}
```

São exatamente sete campos; nome/e-mail são os recebidos do core. Sem bytes do CSV,
credenciais ou template. O notification declara a mesma topologia e
reutiliza seu EmailService para saudação, assunto, link e validade. As filas existentes
de cadastro/revisão de prestadores não recebem esse evento.

O ACK de entrada só ocorre após confirmação positiva do broker e ausência de retorno.
Timeout de confirmação: 5s. NACK, timeout e ausência de rota falham o processamento.
Há três tentativas totais para falhas transitórias, com esperas de 1s e 2s. Payload
inválido vai diretamente à `wonder.report.generation.dlq`; falha persistente também,
sem requeue infinito. As DLQs usam o exchange padrão e sua própria chave de roteamento.
Logs identificam ID/tipo/etapa e classe da causa, sem payload, destinatário ou URL assinada.

Não há transação entre armazenamento, publicação e ACK. O objeto permanece após falha
de publicação. Falha após publicar e antes do ACK pode duplicar evento e
e-mail. Não há deduplicação persistente nem garantia exactly-once. O snapshot continua
em memória e no RabbitMQ; este fluxo não se destina a exportação massiva.

## Migração e operação

1. Pare produtores e consumidores antes de mudar os argumentos da fila de entrada.
2. No painel RabbitMQ, confira as mensagens pendentes. Drene pela versão compatível
   ou preserve os payloads/propriedades para republicar; não exclua fila com pedidos.
3. Se a fila antiga não tiver dead lettering, recrie **somente a fila vazia** com
   `x-dead-letter-exchange=""` e `x-dead-letter-routing-key=wonder.report.generation.dlq`.
   Core e worker declaram esses mesmos argumentos. Não há exclusão automática no startup.
4. Suba core, worker e notification alinhados. Faça os três pedidos, confira os e-mails
   e baixe os CSVs pelos links recebidos. No ambiente local, os e-mails ficam no MailHog.

O notification escuta `wonder.notification.exportacao`, vinculada à chave
`report.generation.completed`. O link assinado é enviado sem alteração. Mensagens
inválidas e falhas de SMTP seguem para `wonder.notification.exportacao.dlq`, conforme
a política existente do módulo, sem requeue automático. Antes de reprocessar uma
conclusão, confira se o link ainda é válido; se expirou, solicite nova exportação.

Para inspecionar DLQ pelo painel (`http://localhost:15672` no ambiente padrão), use
Get messages com requeue para preservar a mensagem durante a análise. Corrija a causa
antes de republicar o JSON original em `wonder.events` / `report.generation.requested`,
preservando `exportacaoId`. Confirme a nova conclusão antes de remover a cópia da DLQ.
A mesma chave de objeto será reutilizada; considere possíveis conclusões anteriores.
A fila de conclusão pode acumular eventos e seus links podem expirar antes do consumo;
nesse caso solicite novamente pelo core. Não publique o conteúdo das filas ou links em logs.

Rollback: pare o worker, preserve filas/objetos e reverta core/worker coordenadamente
se mudar argumentos. Não reative o listener antigo enquanto houver pedidos a preservar:
ele apenas imprimia e confirmava as mensagens.

## Verificação

```powershell
mvn -pl worker verify
mvn -pl core '-Dtest=RabbitProviderTest,ExportacaoServiceTest,AdminExportacaoControllerTest,AdminListagensIntegrationTest,AdminDashboardAgendamentosIntegrationTest' test
mvn -pl notification verify
openspec validate add-worker-csv-exports --strict
```

Os testes do worker usam RabbitMQ e MinIO reais via Testcontainers, inclusive endpoints
interno e público distintos, download assinado, privacidade, três tipos, reentrega,
falha transitória, DLQ e ausência de rota. Não exigem notification. Há testes unitários
de conteúdo, limites do TTL, limpeza dos temporários e confirmação negativa/timeout.
Requer Docker acessível e imagens disponíveis. Para conferir visualmente em planilha,
importe o CSV como UTF-8, delimitador `;`, texto delimitado por aspas e coluna UUID como
texto; confira os acentos, as seis/cinco colunas e a data convertida de São Paulo.

### Resultado da verificação em 20/11/2026

- `mvn -pl worker verify`: 64 testes, sem falhas, erros ou testes ignorados.
- Regressão indicada do core: 64 testes, sem falhas, erros ou testes ignorados.
- `mvn -pl notification verify`: 50 testes, sem falhas, erros ou testes ignorados.
- `openspec validate add-worker-csv-exports --strict`: válido; `git diff --check`: sem erros.
- Ambos os arquivos Compose passaram em `config --quiet` com seus exemplos de ambiente.

A demonstração automatizada utilizou containers isolados de RabbitMQ e MinIO reais:
os três pedidos foram consumidos, os arquivos baixados pelo endpoint público distinto
do interno coincidiram byte a byte com os CSVs esperados e o acesso anônimo foi negado.
As conclusões persistentes ficaram na fila nova, sem notification, até a leitura explícita
pelo teste. Expiração de 24h, destinatário, headers, reentrega na mesma chave, recuperação
transitória, esgotamento e ausência de rota foram conferidos. Não houve envio de e-mail.
Os testes removem seus containers ao terminar; não deixam uma demonstração em execução.

As amostras efetivamente baixadas ficaram em `worker/target/demonstracao/*.csv`
(artefatos locais de build, sem versionamento). Os três CSVs foram importados pelo
LibreOffice Calc em modo headless, preservando cabeçalhos e acentos na conversão para
XLSX. A imagem renderizada do relatório de prestadores foi inspecionada visualmente:
cinco colunas, responsável vazio, `Salão "Ana"; Centro`, quebra interna de linha e
`31/10/2026 23:30:00 -03:00` corretos. As larguras das colunas foram ajustadas somente
na cópia XLSX de conferência para facilitar a leitura; o CSV original foi preservado.

O Docker exigiu acesso fora do sandbox, autorizado para executar os testes. A verificação
visual usou renderização headless, sem sessão interativa de planilha. Não foi necessária
alteração de filas ou dados do ambiente existente. Antes da implantação nesse ambiente,
aplique o procedimento de migração acima caso a fila antiga ainda esteja sem DLQ.

### Revalidação da retomada em 20/11/2026

A retomada encontrou as 19 tarefas concluídas. Foram reexecutados `mvn -pl worker test`
e a regressão do core indicada acima: 64 testes em cada módulo, sem falhas, erros ou
testes ignorados, incluindo os testes de integração com containers reais. A validação
estrita do OpenSpec e `git diff --check` também passaram. O notification não foi
reexecutado nesta retomada; seu resultado acima pertence à verificação anterior.
Não foi identificada pendência de implementação nesta mudança. O consumidor de
conclusão e o envio de e-mail continuam fora do escopo; o próximo passo do fluxo
OpenSpec é o arquivamento da mudança.
