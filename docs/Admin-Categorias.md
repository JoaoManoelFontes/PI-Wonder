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

## Validação

`AdminCategoriasIntegrationTest` usa MockMvc e PostgreSQL real via Testcontainers para validar autorização, contrato, ordenação, inclusão de categorias inativas, lista vazia, preservação dos registros e continuidade do filtro público.

Esta entrega não altera migrations nem seeds. Cadastro, edição, ativação/inativação e upload de foto serão implementados em tasks separadas.
