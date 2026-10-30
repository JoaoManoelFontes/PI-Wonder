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

## Edição

`PUT /admin/categorias/{categoria_id}` exige role `ADMIN` e recebe o mesmo formato do cadastro:

```json
{
  "nome": "Maquiagem profissional",
  "descricao": "Produção para eventos"
}
```

Retorna `200` com a categoria atualizada. A operação preserva o identificador, o status e a foto. Espaços nas extremidades são removidos e uma descrição vazia ou `null` limpa o campo.

```powershell
$corpo = @{ nome = 'Maquiagem profissional'; descricao = 'Produção para eventos' } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri 'http://localhost:8080/admin/categorias/10' `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType 'application/json' -Body $corpo
```

Categoria inexistente retorna `404`. Nome inválido retorna `400`; nome pertencente a outra categoria retorna `409`, sem diferenciar maiúsculas de minúsculas. Manter o próprio nome, mesmo mudando sua capitalização, é permitido.

## Alteração de status

`PATCH /admin/categorias/{categoria_id}/status` exige role `ADMIN` e ativa ou inativa uma categoria:

```json
{
  "status": "inativa"
}
```

Os valores aceitos são `ativa` e `inativa`. A resposta `200` usa o mesmo contrato da listagem e preserva nome, descrição e foto. Repetir o status atual é permitido.

```powershell
$corpo = @{ status = 'inativa' } | ConvertTo-Json
Invoke-RestMethod -Method Patch -Uri 'http://localhost:8080/admin/categorias/10/status' `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType 'application/json' -Body $corpo
```

Categoria inexistente retorna `404`; status ausente, vazio ou diferente dos valores aceitos retorna `400`. Uma categoria inativa continua disponível na listagem administrativa, mas deixa de aparecer em `GET /catalogo/categorias`.

## Foto

`POST /admin/categorias/{categoria_id}/foto` exige role `ADMIN` e recebe `multipart/form-data` com a parte obrigatória `file`. JPEG, PNG e WebP são aceitos até o limite de 5 MB, com validação do tipo MIME e da assinatura do arquivo.

```powershell
curl.exe -X POST "http://localhost:8080/admin/categorias/10/foto" `
  -H "Authorization: Bearer $token" `
  -F "file=@C:\imagens\categoria.png;type=image/png"
```

A resposta `200` usa o contrato completo da categoria. Nome, descrição e status são preservados. A imagem substitui o objeto `categorias/{categoria_id}/foto` no MinIO, sem acumular arquivos, e `foto_url` passa a apontar para `GET /catalogo/categorias/{categoria_id}/foto`. Essa consulta pública entrega os bytes com o tipo original, `Cache-Control: no-store` e `X-Content-Type-Options: nosniff`.

Enquanto o aplicativo ainda não migrar seu caminho, `POST /catalogo/admin/categorias/{categoria_id}/foto` oferece o mesmo comportamento e a mesma proteção. A rota temporária não deve ser usada por novos clientes.

Categoria inexistente retorna `404`. Arquivo ausente, vazio, de tipo incompatível ou com assinatura inválida retorna `400`; arquivo acima de 5 MB retorna `413`. Falha no MinIO retorna `503` sem substituir a URL armazenada no banco.

## Validação

`AdminCategoriasIntegrationTest` usa MockMvc e PostgreSQL real via Testcontainers para validar autorização, contrato, ordenação, inclusão de categorias inativas, lista vazia, cadastro, edição, alteração de status, normalização, duplicidade, persistência e continuidade do filtro público. `AdminCategoriaFotoIntegrationTest` acrescenta MinIO real para cobrir as duas rotas de upload, substituição, leitura, formatos, limites e exposição no catálogo.

Esta entrega não altera migrations nem seeds.
