# Categorias na administração

## Listagem

`GET /admin/categorias` exige JWT com role `ADMIN`, seguindo a proteção já usada na administração de prestadores.

Retorna `200` com todas as categorias, ativas e inativas, ordenadas por nome. Sem categorias cadastradas, retorna `[]`.

Cada item reutiliza o contrato de categoria existente: `id`, `nome`, `descricao`, `status` e `foto_url`. O status é `ativa` ou `inativa`; descrição e foto podem ser `null`. Exemplo ilustrativo:

```json
[
  {
    "id": 10,
    "nome": "Categoria de exemplo",
    "descricao": null,
    "status": "inativa",
    "foto_url": null
  }
]
```

No PowerShell, com um token real de administrador:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/admin/categorias' `
  -Headers @{ Authorization = "Bearer $token" }
```

Sem autenticação, retorna `401`. Usuário autenticado sem a role `ADMIN` recebe `403`, inclusive cliente ou prestador. Administrador com outros papéis também possui acesso.

A consulta é somente leitura e não altera categorias ou serviços. O `GET /catalogo/categorias` continua público e retorna apenas categorias ativas. A rota antiga `/catalogo/admin/categorias` não é adicionada.

## Cadastro

`POST /admin/categorias` exige a mesma role `ADMIN`. O corpo recebe `nome` obrigatório e `descricao` opcional:

```json
{
  "nome": "Maquiagem",
  "descricao": "Serviços de maquiagem"
}
```

A resposta `201` usa o mesmo contrato da listagem. A categoria nasce com `status` igual a `ativa` e `foto_url` igual a `null`. Espaços nas extremidades são removidos; descrição vazia é armazenada como `null`.

```powershell
$corpo = @{ nome = 'Maquiagem'; descricao = 'Serviços de maquiagem' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/admin/categorias' `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType 'application/json' -Body $corpo
```

Nome ausente, vazio ou maior que 100 caracteres retorna `400`. Nome já cadastrado retorna `409`, sem diferenciar letras maiúsculas de minúsculas. Sem autenticação, retorna `401`; usuário sem a role `ADMIN` recebe `403`.

## Validação

`AdminCategoriasIntegrationTest` usa MockMvc e PostgreSQL real via Testcontainers para validar autorização, contrato, ordenação, inclusão de categorias inativas, lista vazia, cadastro, normalização, duplicidade, persistência e continuidade do filtro público.

Esta entrega não altera migrations nem seeds. Edição, ativação/inativação e upload de foto serão implementados em tasks separadas.
