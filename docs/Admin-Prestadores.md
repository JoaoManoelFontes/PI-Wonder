# Administração de prestadores

As rotas administrativas exigem JWT com `ROLE_ADMIN`. O contrato usa nomes em `snake_case`, conforme o aplicativo administrativo.

## Listagem geral

`GET /admin/prestadores` lista cadastros de todos os status, inclusive os inativos. Parâmetros opcionais:

| Parâmetro | Regra |
|---|---|
| `busca` | Até 255 caracteres, com trim; ocorrência literal no estabelecimento sem diferenciar maiúsculas/minúsculas OU pesquisa de responsável no Keycloak por nome, username ou e-mail |
| `status` | `rascunho`, `pendente`, `ativo`, `rejeitado` ou `suspenso`; ausência inclui todos |
| `data_inicio`, `data_fim` | Ambas ausentes ou ambas em `YYYY-MM-DD`; intervalo inclusivo da criação do cadastro no calendário de São Paulo |
| `ordem` | `mais_recentes` (padrão) ou `mais_antigos`, por criação e ID na mesma direção |
| `inicio` | Deslocamento exato não negativo, padrão 0 |
| `limite` | De 1 a 100, padrão 20 |

Exemplo: `GET /admin/prestadores?busca=Ana&status=pendente&data_inicio=2026-11-01&data_fim=2026-11-30&ordem=mais_recentes&inicio=0&limite=20`:

```json
{
  "prestadores": [{
    "id": 42,
    "usuario_id": "11111111-1111-1111-1111-111111111111",
    "nome_estab": "Salão Central", "responsavel_nome": "Ana Silva",
    "status": "pendente", "foto_url": null, "criado_em": "2026-11-05T14:00:00Z"
  }],
  "inicio": 0, "limite": 20, "temProxima": false, "total": 1
}
```

Busca, status e período são combinados antes do count/offset/limit no PostgreSQL. `total` conta todo o conjunto filtrado; página além do fim retorna lista vazia, total preservado e `temProxima=false`. Sem correspondências, retorna `200` e total zero. Status não depende da habilitação da identidade nem do booleano `ativo` do estabelecimento.

O período usa `criadoEm`, nunca envio, aprovação ou atualização: início do primeiro dia inclusivo e início do dia seguinte ao último exclusivo, convertidos de `America/Sao_Paulo` para UTC. Um rascunho sem envio pode corresponder ao intervalo. Um cadastro criado em outubro e enviado em novembro não corresponde a um filtro de criação em novembro. A criação na resposta é um instante ISO-8601 UTC.

Atalhos são calculados pelo futuro frontend e enviados como datas explícitas no calendário de São Paulo: Últimos 7 dias usa hoje menos 6 dias até hoje; Este mês usa primeiro ao último dia do mês. Não existem parâmetros próprios para atalhos.

Os nomes vêm do Keycloak atual, com nome e sobrenome unidos sem espaços excedentes e fallback para username. Conta removida mantém o estabelecimento e `usuario_id`, com `responsavel_nome=null`. Os sete campos bastam para as colunas básicas; serviços, horários e dados completos continuam no detalhe.

Com busca, são lidos todos os candidatos de identidade em lotes de 100 antes de consultar o banco. Depois, resolve-se somente cada responsável distinto da página, reutilizando um token por requisição (até 100 leituras de nomes). Não há cache ou réplica de identidade e não há transação de banco envolvendo HTTP. As duas fontes podem mudar durante a leitura; não se promete snapshot distribuído.

Status/ordem vazios ou inválidos, busca longa, paginação inválida e datas vazias, impossíveis, incompletas ou invertidas retornam `400`. Sem autenticação retorna `401`; sem ADMIN, `403`. Falha técnica em busca de identidades, leitura de lote ou resolução de nome retorna `503`, sem sucesso parcial. Somente `404` da identidade é tratado como conta removida.

Esta entrega contém somente backend, sem frontend ou exportação CSV.

## Pendências

`GET /admin/prestadores/pendentes` lista somente cadastros com status `pendente`, ordenados pela data de envio. A resposta inclui a identificação do usuário, dados básicos do estabelecimento e informações da análise.

`GET /admin/prestadores/{prestador_id}` detalha um cadastro em qualquer status. Além dos dados do prestador, a resposta contém as listas `servicos` e `horarios`, necessárias para a análise administrativa.

## Alteração de status

`PATCH /admin/prestadores/{prestador_id}/status` recebe:

```json
{
  "status": "ativo",
  "motivo_rejeicao": null
}
```

As transições aceitas são:

- `pendente` para `ativo`: aprova e publica o prestador;
- `pendente` para `rascunho`: solicita correção e exige `motivo_rejeicao`;
- `pendente` para `rejeitado`: rejeita e exige `motivo_rejeicao`;
- `ativo` para `suspenso`: retira o prestador do catálogo público;
- `suspenso` para `ativo`: republica o prestador preservando os dados da aprovação original.

Qualquer outra transição retorna `409`. Status ausente ou inválido retorna `400`. O administrador não pode ativar o próprio cadastro; essa tentativa retorna `403`. Prestador inexistente retorna `404`.

As rotas parciais antigas em `/admin/prestadores/solicitacoes` foram substituídas pelo contrato acima para manter uma única regra de alteração de status.

## Validação

`AdminPrestadorStatusIntegrationTest` cobre listagem, detalhe com serviços e horários, aprovação, solicitação de correção, rejeição, suspensão, reativação, autorização, autoaprovação, transições inválidas e persistência.
