# Catálogo público: exemplos de request e response

Guia consolidado na LW-014, incluindo os exemplos de horários e categorias do prestador documentados na LW-012.

Os seis endpoints abaixo permitem consulta sem login. Envie os comandos sem header `Authorization` para validar o acesso público. As listagens retornam arrays JSON; o detalhe retorna um objeto. O prestador precisa ter `status = ATIVO` e `ativo = true` para aparecer no catálogo público.

| Método e rota | Resposta de sucesso |
| --- | --- |
| `GET /catalogo/categorias` | Array de categorias ativas, ordenadas por nome. |
| `GET /catalogo/prestadores` | Array de prestadores publicáveis, ordenados por `nomeEstab`, com filtros opcionais. |
| `GET /catalogo/prestadores/{prestador_id}` | Objeto com o detalhe público do prestador. |
| `GET /catalogo/prestadores/{prestador_id}/servicos` | Array de serviços ativos do prestador, ordenados por nome. |
| `GET /catalogo/prestadores/{prestador_id}/horarios` | Array de intervalos, ordenados por dia e hora de início. |
| `GET /catalogo/prestadores/{prestador_id}/categorias` | Array de categorias ativas vinculadas a serviços ativos do prestador, ordenadas por nome e sem repetições. |

## Preparação

Inicie o `core` conforme o [README](../README.md). Os comandos abaixo consideram a porta padrão `8080`; ajuste a URL caso tenha configurado outra porta.

A migration `V5__seed_catalogo_minimo.sql` cadastra o Studio Wonder com serviços, categorias e horários. Consulte seu ID no PowerShell:

```powershell
Invoke-RestMethod 'http://localhost:8080/catalogo/prestadores?nome=Studio%20Wonder' |
    Select-Object id, nomeEstab
```

Defina o ID retornado para usar nos próximos comandos:

```powershell
$prestadorId = 1 # Substitua pelo ID retornado na consulta.
```

Os IDs nos payloads abaixo são ilustrativos. Os dias, horários, nomes e descrições correspondem ao seed; IDs e dados podem variar conforme o estado do banco.

## Categorias gerais — LW-007

```http
GET /catalogo/categorias
Accept: application/json
```

```powershell
curl.exe -i 'http://localhost:8080/catalogo/categorias'
```

Resposta `200 OK`, com `Content-Type: application/json`, considerando o seed sem alterações:

```json
[
  {
    "id": 2,
    "nome": "Barba",
    "descricao": "Corte, desenho e cuidados com a barba.",
    "status": "ativa",
    "foto_url": null
  },
  {
    "id": 1,
    "nome": "Cabelo",
    "descricao": "Cortes, penteados, tranças e outros cuidados com o cabelo.",
    "status": "ativa",
    "foto_url": null
  },
  {
    "id": 5,
    "nome": "Maquiagem",
    "descricao": "Maquiagem para eventos e outras ocasiões.",
    "status": "ativa",
    "foto_url": null
  },
  {
    "id": 4,
    "nome": "Sobrancelhas",
    "descricao": "Design e cuidados com as sobrancelhas.",
    "status": "ativa",
    "foto_url": null
  },
  {
    "id": 3,
    "nome": "Unhas",
    "descricao": "Manicure, pedicure e alongamento de unhas.",
    "status": "ativa",
    "foto_url": null
  }
]
```

A consulta retorna somente categorias `ATIVA`, serializadas com `status = "ativa"`. Ela lista as categorias gerais do catálogo, independentemente de estarem vinculadas a algum prestador. Sem categorias ativas, retorna `200` com `[]`.

## Prestadores e filtros — LW-008 e LW-009

```http
GET /catalogo/prestadores?nome=Studio%20Wonder&categoria_id=1
Accept: application/json
```

Os parâmetros são opcionais e podem ser usados isoladamente ou em conjunto:

| Parâmetro | Regra |
| --- | --- |
| `nome` | Trecho do nome do estabelecimento, sem diferenciar maiúsculas e minúsculas. Espaços nas extremidades são removidos; nome vazio ou composto somente de espaços é ignorado. |
| `categoria_id` | ID numérico de categoria ativa, vinculada a pelo menos um serviço ativo do prestador. |

```powershell
curl.exe -i 'http://localhost:8080/catalogo/prestadores'
curl.exe -i 'http://localhost:8080/catalogo/prestadores?nome=studio'
curl.exe -i 'http://localhost:8080/catalogo/prestadores?categoria_id=1'
curl.exe -i 'http://localhost:8080/catalogo/prestadores?nome=Studio%20Wonder&categoria_id=1'
```

O ID `1` no filtro é ilustrativo: consulte `GET /catalogo/categorias` para obter o ID de Cabelo no seu banco.

Resposta `200 OK`, com `Content-Type: application/json`, considerando o Studio Wonder como único prestador correspondente:

```json
[
  {
    "id": 1,
    "perfilUsuarioId": "10000000-0000-0000-0000-000000000001",
    "nomeEstab": "Studio Wonder",
    "documento": "00000000000191",
    "endereco": "Rua das Flores",
    "numero": "100",
    "bairro": "Centro",
    "cidade": "Natal",
    "estado": "RN",
    "complemento": "Sala 1",
    "status": "ativo",
    "ativo": true,
    "fotoUrl": null
  }
]
```

A resposta usa `PrestadorPublicResponse`. Os critérios `status = ATIVO` e `ativo = true` continuam obrigatórios com qualquer combinação de filtros. Os prestadores são ordenados por `nomeEstab` e não se repetem quando possuem vários serviços na categoria pesquisada.

Nome sem correspondência, categoria inexistente ou categoria inativa resultam em `200` com `[]`. Um `categoria_id` não numérico ou fora do intervalo aceito por `Long` resulta em `400`.

## Detalhe público do prestador — JN-007

```http
GET /catalogo/prestadores/1
Accept: application/json
```

```powershell
curl.exe -i "http://localhost:8080/catalogo/prestadores/$prestadorId"
```

Resposta `200 OK`, com `Content-Type: application/json`:

```json
{
  "id": 1,
  "perfilUsuarioId": "10000000-0000-0000-0000-000000000001",
  "nomeEstab": "Studio Wonder",
  "documento": "00000000000191",
  "endereco": "Rua das Flores",
  "numero": "100",
  "bairro": "Centro",
  "cidade": "Natal",
  "estado": "RN",
  "complemento": "Sala 1",
  "status": "ativo",
  "ativo": true,
  "fotoUrl": null
}
```

O detalhe usa o mesmo DTO da listagem, retornado como objeto. Serviços, horários e categorias são consultados pelas rotas específicas abaixo. Prestador inexistente ou indisponível retorna `404`; ID não numérico ou fora do intervalo aceito por `Long` retorna `400`.

| Campo do prestador | Significado |
| --- | --- |
| `id` | ID numérico do prestador, usado nas rotas e na navegação para o detalhe. |
| `perfilUsuarioId` | UUID do perfil local associado. Não equivale automaticamente ao antigo `usuario_id`. |
| `nomeEstab` | Nome do estabelecimento. |
| `documento` | Documento cadastrado, podendo ser `null`. |
| `endereco`, `numero`, `bairro`, `cidade`, `estado`, `complemento` | Campos do endereço, podendo ser `null`. |
| `status` | `ativo` nas respostas públicas. |
| `ativo` | `true` nas respostas públicas. |
| `fotoUrl` | URL da imagem do prestador, podendo ser `null`. |

Preserve os nomes de cada DTO: prestador usa `nomeEstab`, `perfilUsuarioId` e `fotoUrl`; categoria usa `foto_url`. A comparação com o aplicativo antigo está no [guia de validação do frontend](Catalogo-Validacao-Frontend.md).

## Serviços do prestador — JN-008

```http
GET /catalogo/prestadores/1/servicos
Accept: application/json
```

```powershell
curl.exe -i "http://localhost:8080/catalogo/prestadores/$prestadorId/servicos"
```

Resposta `200 OK`, com `Content-Type: application/json`, considerando os serviços do seed:

```json
[
  {
    "id": 1,
    "prestador_id": 1,
    "categoria_id": 1,
    "nome": "Corte de cabelo",
    "preco": 50.00,
    "duracao_min": 45,
    "status": "ativo",
    "foto_url": null
  },
  {
    "id": 2,
    "prestador_id": 1,
    "categoria_id": 4,
    "nome": "Design de sobrancelhas",
    "preco": 40.00,
    "duracao_min": 30,
    "status": "ativo",
    "foto_url": null
  },
  {
    "id": 3,
    "prestador_id": 1,
    "categoria_id": 3,
    "nome": "Manicure",
    "preco": 35.00,
    "duracao_min": 60,
    "status": "ativo",
    "foto_url": null
  }
]
```

| Campo | Significado |
| --- | --- |
| `id` | ID do serviço, usado como `servico_id` nas consultas de disponibilidade. |
| `prestador_id` | ID do prestador consultado. |
| `categoria_id` | ID da categoria associada, podendo ser `null`. |
| `nome` | Nome do serviço. |
| `preco` | Preço como número JSON. |
| `duracao_min` | Duração em minutos. |
| `status` | `ativo` nas respostas desta consulta. |
| `foto_url` | URL da imagem, podendo ser `null`. |

A resposta usa `ServicoPublicResponse`. Retorna somente serviços com `status = ATIVO` do prestador consultado, ordenados por nome. Serviços inativos e de outros prestadores são excluídos. O filtro desta listagem não exige categoria ativa: serviços sem categoria ou com categoria inativa também podem aparecer. A consulta de categorias do prestador aplica seu próprio filtro de categorias ativas.

Prestador público sem serviços ativos retorna `200` com `[]`. Prestador inexistente ou indisponível retorna `404`.

## Horários do prestador — LW-010

```http
GET /catalogo/prestadores/1/horarios
Accept: application/json
```

Consulta pelo PowerShell:

```powershell
curl.exe -i "http://localhost:8080/catalogo/prestadores/$prestadorId/horarios"
```

Resposta `200 OK`, com `Content-Type: application/json`:

```json
[
  {
    "id": 1,
    "prestador_id": 1,
    "dia_semana": 1,
    "hora_inicio": "09:00:00",
    "hora_fim": "18:00:00"
  },
  {
    "id": 2,
    "prestador_id": 1,
    "dia_semana": 2,
    "hora_inicio": "09:00:00",
    "hora_fim": "18:00:00"
  },
  {
    "id": 3,
    "prestador_id": 1,
    "dia_semana": 3,
    "hora_inicio": "09:00:00",
    "hora_fim": "18:00:00"
  },
  {
    "id": 4,
    "prestador_id": 1,
    "dia_semana": 4,
    "hora_inicio": "09:00:00",
    "hora_fim": "18:00:00"
  },
  {
    "id": 5,
    "prestador_id": 1,
    "dia_semana": 5,
    "hora_inicio": "09:00:00",
    "hora_fim": "18:00:00"
  },
  {
    "id": 6,
    "prestador_id": 1,
    "dia_semana": 6,
    "hora_inicio": "09:00:00",
    "hora_fim": "13:00:00"
  }
]
```

| Campo | Significado |
| --- | --- |
| `id` | ID do intervalo de atendimento. |
| `prestador_id` | ID do prestador consultado. |
| `dia_semana` | De 1 (segunda-feira) a 7 (domingo). |
| `hora_inicio` | Início do intervalo, em `HH:mm:ss`. |
| `hora_fim` | Fim do intervalo, em `HH:mm:ss`. |

Os intervalos são ordenados por `dia_semana` e depois por `hora_inicio`. Um dia pode ter vários intervalos, por exemplo antes e depois do almoço. O domingo não aparece no exemplo porque o seed não cadastra atendimento nesse dia.

O modelo atual não possui flag `ativo` no horário: a consulta retorna os horários cadastrados do prestador público. Esses intervalos representam a agenda de atendimento; não são os horários livres calculados a partir de serviços e agendamentos.

## Categorias do prestador — LW-011

```http
GET /catalogo/prestadores/1/categorias
Accept: application/json
```

Consulta pelo PowerShell:

```powershell
curl.exe -i "http://localhost:8080/catalogo/prestadores/$prestadorId/categorias"
```

Resposta `200 OK`, com `Content-Type: application/json`:

```json
[
  {
    "id": 1,
    "nome": "Cabelo",
    "descricao": "Cortes, penteados, tranças e outros cuidados com o cabelo.",
    "status": "ativa",
    "foto_url": null
  },
  {
    "id": 4,
    "nome": "Sobrancelhas",
    "descricao": "Design e cuidados com as sobrancelhas.",
    "status": "ativa",
    "foto_url": null
  },
  {
    "id": 3,
    "nome": "Unhas",
    "descricao": "Manicure, pedicure e alongamento de unhas.",
    "status": "ativa",
    "foto_url": null
  }
]
```

| Campo | Significado |
| --- | --- |
| `id` | ID da categoria. |
| `nome` | Nome da categoria. |
| `descricao` | Descrição, podendo ser `null`. |
| `status` | `ativa` nas respostas desta consulta. |
| `foto_url` | URL da imagem, podendo ser `null`. |

A associação usa o vínculo existente no modelo atual: `servico.prestador` e `servico.categoria`, seguindo a regra do filtro `categoria_id` da LW-009. A consulta retorna apenas categorias ativas com pelo menos um serviço ativo do prestador, sem repetições e ordenadas por nome. Serviços sem categoria são ignorados.

A resposta é um array direto de categorias, como no exemplo. Mesmo que vários serviços utilizem a mesma categoria, ela aparece uma única vez.

## Listas vazias e erros

| Consulta | Retorno sem correspondência | Entrada inválida |
| --- | --- | --- |
| Categorias gerais | `200`, `[]`, quando não há categorias ativas. | Não possui parâmetro de ID. |
| Listagem de prestadores | `200`, `[]`, quando nenhum prestador público corresponde aos filtros. | `400` para `categoria_id` que não possa ser convertido em `Long`. |
| Detalhe do prestador | `404` para prestador inexistente ou indisponível. | `400` para ID que não possa ser convertido em `Long`. |

Para as listagens de horários e categorias vinculadas ao prestador:

| Situação | Horários | Categorias |
| --- | --- | --- |
| Prestador público com dados disponíveis | `200`, array de horários. | `200`, array de categorias. |
| Prestador público sem horários cadastrados | `200`, `[]`. | Depende dos serviços e categorias cadastrados. |
| Prestador público sem categorias publicáveis | Depende dos horários cadastrados. | `200`, `[]`. |
| Prestador inexistente | `404`. | `404`. |
| Prestador com `ativo = false` ou status diferente de `ATIVO` | `404`. | `404`. |
| ID não numérico na URL | `400`. | `400`. |

A listagem de serviços retorna `200` com um array de serviços ativos, ou `[]` quando nenhum estiver disponível. Segue os mesmos retornos `404` para prestador inexistente ou indisponível e `400` para ID que não possa ser convertido em `Long`.

Exemplo de resposta `200 OK` para uma lista vazia:

```json
[]
```

Para conferir uma busca sem correspondência e um filtro inválido:

```powershell
curl.exe -i 'http://localhost:8080/catalogo/prestadores?categoria_id=9223372036854775807'
curl.exe -i 'http://localhost:8080/catalogo/prestadores?categoria_id=invalido'
```

A primeira chamada retorna `200` com `[]` se o ID de categoria não existir; a segunda retorna `400`.

Para conferir o `404`, use um ID que não exista no seu banco. No exemplo abaixo, confirme que o ID informado não está cadastrado:

```powershell
curl.exe -i 'http://localhost:8080/catalogo/prestadores/9223372036854775807'
curl.exe -i 'http://localhost:8080/catalogo/prestadores/9223372036854775807/horarios'
curl.exe -i 'http://localhost:8080/catalogo/prestadores/9223372036854775807/categorias'
curl.exe -i 'http://localhost:8080/catalogo/prestadores/9223372036854775807/servicos'
```

Para conferir o `400`:

```powershell
curl.exe -i 'http://localhost:8080/catalogo/prestadores/invalido'
curl.exe -i 'http://localhost:8080/catalogo/prestadores/invalido/horarios'
curl.exe -i 'http://localhost:8080/catalogo/prestadores/invalido/categorias'
curl.exe -i 'http://localhost:8080/catalogo/prestadores/invalido/servicos'
```

Os critérios de erro deste guia fixam o status HTTP. A task não estabelece um novo formato global de corpo de erro; esse corpo segue o tratamento e a configuração atuais do Spring.

## Critérios para revisão nas issues

- As seis rotas existentes respondem sem token para dados públicos.
- Categorias gerais exibem somente categorias ativas, ordenadas por nome.
- Prestadores exibem somente registros com `status = ATIVO` e `ativo = true`, ordenados por nome do estabelecimento.
- Os filtros `nome` e `categoria_id` funcionam isoladamente e em conjunto, preservando os critérios de publicação.
- Detalhe retorna um objeto com o mesmo contrato do item da listagem.
- Serviços pertencem ao prestador consultado, têm status ativo e são ordenados por nome.
- Horários pertencem ao prestador consultado e são ordenados por dia e hora.
- Categorias do prestador pertencem a serviços ativos, são ativas e aparecem uma única vez.
- Listas vazias e erros seguem os status documentados acima.

## Consulta autenticada do próprio prestador — AUTH-LW-001

`GET /catalogo/prestadores/me` exige JWT válido. É uma consulta do cadastro do usuário logado, separada das seis rotas públicas descritas neste guia.

O identificador vem exclusivamente do `sub` do JWT: o backend localiza o prestador pelo vínculo com o perfil local e seu `keycloakId`. Não recebe ID de usuário ou prestador como parâmetro. Não exige aprovação prévia, permitindo consultar o próprio cadastro pendente, rejeitado, suspenso ou em rascunho, além do ativo.

A resposta `200` usa os mesmos 13 campos de `PrestadorPublicResponse` do detalhe acima, com `status` e `ativo` reais do cadastro. Retorna `401` sem autenticação ou com identificador de usuário inválido no JWT, e `404` quando não existe prestador vinculado, inclusive quando não há perfil local. A consulta não cria perfil nem cadastro.

As demais consultas públicas continuam sem exigir login. `CatalogoPrestadorLogadoIntegrationTest` verifica o isolamento entre usuários, os estados do cadastro, os campos da resposta e os erros; `PrestadorServiceTest` cobre a busca no service.

## Edição autenticada do próprio prestador — AUTH-LW-003

`PUT /catalogo/prestadores/{prestador_id}` permite ao dono editar os dados do seu cadastro, usando o vínculo com o `sub` do JWT. O cadastro inicial existente continua em `POST /prestadores`; a rota de edição segue o plano de catálogo.

O corpo usa `AtualizacaoPrestadorRequest`, com os campos opcionais `nomeEstab`, `documento`, `endereco`, `numero`, `bairro`, `cidade`, `estado` e `complemento`. Campos omitidos ou enviados como `null` mantêm o valor atual. Essa operação aceita edição parcial, como o cliente antigo; não utiliza os nomes antigos em snake_case. Se informado, `nomeEstab` não pode estar vazio ou conter somente espaços.

Os limites são os mesmos do cadastro: nome, endereço e complemento com até 255 caracteres; documento com até 20; número com até 30; bairro e cidade com até 120; estado com até 2.

Exemplo de corpo para alterar nome e cidade:

```json
{
  "nomeEstab": "Studio Wonder atualizado",
  "cidade": "Parnamirim"
}
```

A resposta `200` usa `PrestadorPublicResponse`, com os dados atualizados. Proprietário, status, campo `ativo`, foto e informações de aprovação/rejeição não são editáveis por esse DTO. Campos extras no JSON não alteram esses dados. A atualização não cria perfil nem dispara aprovação automática.

Seguindo a regra do projeto antigo, cadastros `PENDENTE` não podem ser editados durante a análise. São editáveis pelo dono os cadastros `RASCUNHO`, `REJEITADO`, `ATIVO` e `SUSPENSO`, sem mudança automática de publicação.

| Situação | Retorno |
| --- | --- |
| Dono autenticado e dados válidos, em estado editável | `200`, objeto atualizado. |
| Sem autenticação | `401`. |
| Usuário diferente do dono, inclusive admin nesta rota | `403`. |
| Cadastro inexistente | `404`. |
| Cadastro pendente de análise | `409`. |
| Campos inválidos, corpo inválido/ausente ou ID não numérico | `400`. |

`CatalogoPrestadorEdicaoIntegrationTest` cobre persistência dos dados, edição parcial, propriedade, campos protegidos, limites de validação e estados do cadastro. As demais consultas públicas do catálogo permanecem sem login.

## Envio e reenvio para aprovação — AUTH-LW-004

`POST /catalogo/prestadores/{prestador_id}/enviar-aprovacao` exige JWT do dono do cadastro e não precisa de corpo. A resposta `200` usa `PrestadorPublicResponse`, com `status = "pendente"` e `ativo = false`.

O cadastro inicial em `POST /prestadores` continua nascendo `PENDENTE`, conforme o fluxo atual. Esta rota permite enviar cadastros `RASCUNHO` e reenviar cadastros `REJEITADO` após correção; não permite novo envio de cadastro já pendente, ativo ou suspenso.

Antes do envio, o cadastro precisa ter nome e documento preenchidos, ao menos um serviço ativo, uma categoria ativa vinculada a serviço ativo e um horário de atendimento. Serviços, categorias e horários de outros prestadores não atendem aos requisitos. No modelo atual, as categorias são derivadas dos serviços; não é criado um vínculo separado com o prestador.

O envio registra uma nova data em `enviadoEm`, limpa o motivo de rejeição anterior e mantém o cadastro fora do catálogo público. A decisão continua nas rotas administrativas existentes. Não há aprovação automática nem alteração do proprietário.

| Situação | Retorno |
| --- | --- |
| Dono autenticado, status permitido e cadastro completo | `200`, cadastro pendente. |
| Sem autenticação | `401`. |
| Usuário diferente do dono, inclusive admin nesta rota | `403`. |
| Cadastro inexistente | `404`. |
| Cadastro pendente, ativo ou suspenso; envio repetido | `409`. |
| Requisitos incompletos ou ID inválido | `400`. |

Exemplo de requisição autenticada:

```http
POST /catalogo/prestadores/1/enviar-aprovacao
Authorization: Bearer <token-do-dono>
Accept: application/json
```

`CatalogoPrestadorEnvioAprovacaoIntegrationTest` cobre envio, reenvio, requisitos, propriedade, persistência e integração com a listagem administrativa de solicitações pendentes.

## Categorias do prestador logado — AUTH-LW-005

`GET /catalogo/prestadores/me/categorias` exige JWT e retorna um array de `CategoriaPublicResponse`, com os mesmos cinco campos da consulta pública de categorias. O prestador é identificado exclusivamente pelo vínculo do perfil local com o `sub` do token; não recebe ID de usuário ou prestador como parâmetro.

Retorna categorias ativas vinculadas aos serviços ativos do próprio prestador, ordenadas por nome e sem duplicações. Serviços sem categoria, serviços inativos e categorias inativas são ignorados. O dono pode consultar em qualquer status do cadastro, inclusive pendente ou rejeitado, e independentemente do campo `ativo`.

Conforme a decisão adotada para o projeto novo, as categorias do prestador são derivadas dos serviços. Não há associação manual nem rotas POST/DELETE em `/me/categorias`. Alterar a categoria ou o status de um serviço reflete na consulta, sem manter uma segunda lista de vínculos.

| Situação | Retorno |
| --- | --- |
| Prestador autenticado com categorias disponíveis | `200`, array de categorias. |
| Prestador autenticado sem categorias ativas em serviços ativos | `200`, `[]`. |
| Sem autenticação | `401`. |
| Usuário sem cadastro de prestador, com ou sem perfil local | `404`, sem criar registros. |

`CatalogoCategoriasPrestadorLogadoIntegrationTest` cobre autenticação, isolamento entre usuários, filtros, ordenação, duplicação, estados do prestador e atualização das categorias a partir dos serviços. A consulta pública `GET /catalogo/prestadores/{prestador_id}/categorias` continua exigindo prestador publicável e funcionando sem login.

## Gestão de serviços do próprio prestador — AUTH-LW-006

As rotas abaixo exigem JWT do dono do prestador. São permitidas em cadastros `RASCUNHO`, `REJEITADO`, `ATIVO` e `SUSPENSO`; cadastros `PENDENTE` ficam bloqueados durante a análise, seguindo a regra de edição existente. Ter papel de admin não concede permissão para editar o cadastro de outro usuário por essas rotas.

| Método e rota | Sucesso |
| --- | --- |
| `POST /catalogo/prestadores/{prestador_id}/servicos` | `201`, `ServicoPublicResponse` com o serviço criado como ativo. |
| `PUT /catalogo/prestadores/{prestador_id}/servicos/{servico_id}` | `200`, `ServicoPublicResponse` atualizado. |
| `DELETE /catalogo/prestadores/{prestador_id}/servicos/{servico_id}` | `204`, sem corpo; serviço inativado, sem exclusão física. |

Corpo de criação:

```json
{
  "nome": "Corte de cabelo",
  "preco": 50.00,
  "duracao_min": 45,
  "categoria_id": 1
}
```

`nome`, `preco` e `duracao_min` são obrigatórios na criação. O nome não pode estar em branco e aceita até 150 caracteres. O preço deve ser não negativo, com até oito dígitos inteiros e duas casas decimais. A duração é um inteiro positivo em minutos. A categoria é opcional; quando informada, seu ID deve ser positivo e identificar uma categoria ativa. O ID `1` do exemplo é ilustrativo: consulte as categorias do seu banco.

Na edição, os mesmos campos são opcionais; valores omitidos ou `null` são preservados, inclusive a categoria. É possível trocar a categoria informando outro ID ativo. A foto, o dono e o status não são alterados pelo payload. Editar um serviço inativo não o reativa.

Exemplo de edição parcial:

```json
{
  "preco": 55.00,
  "duracao_min": 60
}
```

O DELETE mantém o serviço e seus vínculos no banco. Agendamentos `PENDENTE` ou `CONFIRMADO` desse serviço impedem a inativação, com retorno `409`. Agendamentos `CANCELADO` ou `CONCLUIDO` são preservados e não bloqueiam. Repetir o DELETE de serviço já inativo e sem agendamento aberto retorna `204`.

Inativar um serviço o remove das consultas públicas de serviços ativos e da disponibilidade. As categorias do prestador são recalculadas a partir dos serviços ativos: inativar o último serviço de uma categoria a retira da listagem; mudar a categoria de um serviço também reflete na consulta.

| Situação | Retorno |
| --- | --- |
| Sem autenticação | `401`. |
| Usuário diferente do dono | `403`. |
| Prestador inexistente, serviço inexistente ou serviço de outro prestador na URL informada | `404`. |
| Prestador pendente de análise ou tentativa de inativar serviço com agendamento aberto | `409`. |
| Campos inválidos, categoria inexistente/inativa ou ID malformado | `400`. |

`ServicoPrestadorIntegrationTest` cobre criação, edição parcial, inativação, estados do prestador, limites dos campos, categorias derivadas, isolamento entre usuários e preservação dos agendamentos. As consultas GET públicas existentes continuam funcionando sem login.

## Gestão de horários do próprio prestador — AUTH-LW-007

As duas rotas exigem JWT do dono e seguem a regra de edição do cadastro: prestador pendente de análise não pode alterar os horários. Cadastros em rascunho, rejeitados, ativos ou suspensos podem ser alterados pelo dono. O papel de admin não permite modificar o horário de outro usuário por essas rotas.

| Método e rota | Sucesso |
| --- | --- |
| `POST /catalogo/prestadores/{prestador_id}/horarios` | `201`, `HorarioAtendimentoPublicResponse` com cinco campos. |
| `DELETE /catalogo/prestadores/{prestador_id}/horarios/{horario_id}` | `204`, sem corpo. |

Exemplo de criação:

```json
{
  "dia_semana": 1,
  "hora_inicio": "09:00",
  "hora_fim": "12:00"
}
```

Os três campos são obrigatórios. `dia_semana` vai de 1 (segunda) a 7 (domingo). As horas são locais e aceitam `HH:mm` ou `HH:mm:ss`, sem frações de segundo. O início deve ser estritamente anterior ao fim, no mesmo dia. Para atendimento que atravesse a meia-noite, não é aceito um intervalo com início maior que o fim.

Não são aceitos intervalos duplicados ou sobrepostos no mesmo dia do mesmo prestador. Intervalos adjacentes, como 09:00–12:00 e 12:00–14:00, e intervalos separados por pausas são permitidos. Horários em outro dia ou de outro prestador não geram conflito. O cadastro usa bloqueio transacional na linha do prestador para impedir que duas requisições simultâneas cadastrem sobreposições.

O modelo atual não possui flag de ativação para horários. O DELETE remove apenas o intervalo de atendimento; não exclui nem cancela agendamentos existentes. As consultas públicas de horários e o cálculo de disponibilidade passam a refletir a agenda atualizada. A rota não altera os serviços nem o status do prestador.

| Situação | Retorno |
| --- | --- |
| Sem autenticação | `401`. |
| Usuário diferente do dono | `403`. |
| Prestador ou horário inexistente; horário de outro prestador na URL informada | `404`. |
| Cadastro pendente de análise ou intervalo duplicado/sobreposto | `409`. |
| Campos ausentes, dia/hora inválidos, início maior ou igual ao fim ou ID malformado | `400`. |

`HorarioPrestadorIntegrationTest` cobre os contratos, persistência, remoção, validações, permissões, limites das sobreposições, requisições concorrentes e preservação de agendamentos. O GET público de horários existente continua funcionando sem login para prestadores publicáveis.

## Consultas de disponibilidade

`GET /agendamentos/dias-disponiveis` e `GET /agendamentos/disponibilidade` estão implementados e permitem consulta sem login. A primeira recebe `prestador_id`, `servico_id` e `mes` (`yyyy-MM`); a segunda recebe `prestador_id`, `servico_id` e `data` (`yyyy-MM-dd`).

Os horários retornados por `/catalogo/prestadores/{prestador_id}/horarios` representam os intervalos de atendimento cadastrados. Para consultar horários livres conforme duração do serviço e conflitos com agendamentos, use `/agendamentos/disponibilidade`. Os parâmetros, respostas e exemplos do seed estão no [guia de disponibilidade](Disponibilidade-Exemplos.md).

## Validação automatizada

Os testes `CatalogoCategoriasIntegrationTest`, `CatalogoPrestadoresIntegrationTest` e `CatalogoPrestadorIntegrationTest` cobrem as listagens gerais, filtros e detalhe. O `CatalogoFrontendContractIntegrationTest` confere os formatos das fixtures de consumo com cinco das seis rotas: categorias gerais, prestadores, detalhe, horários e categorias do prestador. A rota de serviços ainda não faz parte dessa comparação compartilhada.

O `CatalogoServicosIntegrationTest` valida o endpoint de serviços sem login, os oito campos do DTO, vínculo com o prestador, filtro de serviços ativos, ordenação, lista vazia e `404` para prestadores inexistentes ou indisponíveis.

Os testes `CatalogoHorariosIntegrationTest` e `CatalogoCategoriasPrestadorIntegrationTest` cobrem acesso sem autenticação, contrato JSON, ordenação, vínculo com o prestador, listas vazias e retorno `404`. A listagem de categorias também cobre filtros de status e ausência de duplicações.

Com o Docker disponível, execute na raiz do projeto:

```powershell
.\mvnw.cmd -pl core test '-Dtest=CatalogoHorariosIntegrationTest,CatalogoCategoriasPrestadorIntegrationTest,CatalogoServicosIntegrationTest'
```

Para executar toda a suíte do `core`:

```powershell
.\mvnw.cmd -pl core test
```
