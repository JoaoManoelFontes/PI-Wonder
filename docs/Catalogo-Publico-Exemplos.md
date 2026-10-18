# Catálogo público: horários e categorias do prestador

Exemplos dos endpoints implementados nas tasks LW-010 e LW-011, documentados na LW-012.

Os dois endpoints permitem consulta sem login e retornam arrays JSON. O prestador precisa ter `status = ATIVO` e `ativo = true` para aparecer no catálogo público.

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

Para conferir o `404`, use um ID que não exista no seu banco. No exemplo abaixo, confirme que o ID informado não está cadastrado:

```powershell
curl.exe -i 'http://localhost:8080/catalogo/prestadores/9223372036854775807/horarios'
curl.exe -i 'http://localhost:8080/catalogo/prestadores/9223372036854775807/categorias'
```

Para conferir o `400`:

```powershell
curl.exe -i 'http://localhost:8080/catalogo/prestadores/invalido/horarios'
curl.exe -i 'http://localhost:8080/catalogo/prestadores/invalido/categorias'
```

## Validação automatizada

Os testes `CatalogoHorariosIntegrationTest` e `CatalogoCategoriasPrestadorIntegrationTest` cobrem acesso sem autenticação, contrato JSON, ordenação, vínculo com o prestador, listas vazias e retorno `404`. A listagem de categorias também cobre filtros de status e ausência de duplicações.

Com o Docker disponível, execute na raiz do projeto:

```powershell
.\mvnw.cmd -pl core test '-Dtest=CatalogoHorariosIntegrationTest,CatalogoCategoriasPrestadorIntegrationTest'
```
