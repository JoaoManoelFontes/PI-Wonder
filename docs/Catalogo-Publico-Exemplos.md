# Catálogo público: exemplos de request e response

Guia consolidado na LW-014, incluindo os exemplos de horários e categorias do prestador documentados na LW-012.

Os cinco endpoints abaixo permitem consulta sem login. Envie os comandos sem header `Authorization` para validar o acesso público. As listagens retornam arrays JSON; o detalhe retorna um objeto. O prestador precisa ter `status = ATIVO` e `ativo = true` para aparecer no catálogo público.

| Método e rota | Resposta de sucesso |
| --- | --- |
| `GET /catalogo/categorias` | Array de categorias ativas, ordenadas por nome. |
| `GET /catalogo/prestadores` | Array de prestadores publicáveis, ordenados por `nomeEstab`, com filtros opcionais. |
| `GET /catalogo/prestadores/{prestador_id}` | Objeto com o detalhe público do prestador. |
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

O detalhe usa o mesmo DTO da listagem, retornado como objeto. Horários e categorias são consultados pelas rotas específicas abaixo. Prestador inexistente ou indisponível retorna `404`; ID não numérico ou fora do intervalo aceito por `Long` retorna `400`.

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

Para as duas listagens vinculadas ao prestador:

| Situação | Horários | Categorias |
| --- | --- | --- |
| Prestador público com dados disponíveis | `200`, array de horários. | `200`, array de categorias. |
| Prestador público sem horários cadastrados | `200`, `[]`. | Depende dos serviços e categorias cadastrados. |
| Prestador público sem categorias publicáveis | Depende dos horários cadastrados. | `200`, `[]`. |
| Prestador inexistente | `404`. | `404`. |
| Prestador com `ativo = false` ou status diferente de `ATIVO` | `404`. | `404`. |
| ID não numérico na URL | `400`. | `400`. |

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
```

Para conferir o `400`:

```powershell
curl.exe -i 'http://localhost:8080/catalogo/prestadores/invalido'
curl.exe -i 'http://localhost:8080/catalogo/prestadores/invalido/horarios'
curl.exe -i 'http://localhost:8080/catalogo/prestadores/invalido/categorias'
```

Os critérios de erro deste guia fixam o status HTTP. A task não estabelece um novo formato global de corpo de erro; esse corpo segue o tratamento e a configuração atuais do Spring.

## Critérios para revisão nas issues

- As cinco rotas existentes respondem sem token para dados públicos.
- Categorias gerais exibem somente categorias ativas, ordenadas por nome.
- Prestadores exibem somente registros com `status = ATIVO` e `ativo = true`, ordenados por nome do estabelecimento.
- Os filtros `nome` e `categoria_id` funcionam isoladamente e em conjunto, preservando os critérios de publicação.
- Detalhe retorna um objeto com o mesmo contrato do item da listagem.
- Horários pertencem ao prestador consultado e são ordenados por dia e hora.
- Categorias do prestador pertencem a serviços ativos, são ativas e aparecem uma única vez.
- Listas vazias e erros seguem os status documentados acima.

## Pendências fora dos contratos implementados

Na base da LW-014, `GET /catalogo/prestadores/{prestador_id}/servicos` ainda depende da JN-008. O DTO existente e a fixture usada na LW-013 não equivalem à entrega desse endpoint.

Os endpoints de dias e horários disponíveis para agendamento também dependem das entregas de agendamentos. Este guia documenta horários de atendimento, sem afirmar que esses intervalos estão livres de conflitos.

## Validação automatizada

Os testes `CatalogoCategoriasIntegrationTest`, `CatalogoPrestadoresIntegrationTest` e `CatalogoPrestadorIntegrationTest` cobrem as listagens gerais, filtros e detalhe. O `CatalogoFrontendContractIntegrationTest` confere os formatos das fixtures de consumo com os cinco endpoints existentes.

Os testes `CatalogoHorariosIntegrationTest` e `CatalogoCategoriasPrestadorIntegrationTest` cobrem acesso sem autenticação, contrato JSON, ordenação, vínculo com o prestador, listas vazias e retorno `404`. A listagem de categorias também cobre filtros de status e ausência de duplicações.

Com o Docker disponível, execute na raiz do projeto:

```powershell
.\mvnw.cmd -pl core test '-Dtest=CatalogoHorariosIntegrationTest,CatalogoCategoriasPrestadorIntegrationTest'
```

Para executar toda a suíte do `core`:

```powershell
.\mvnw.cmd -pl core test
```
