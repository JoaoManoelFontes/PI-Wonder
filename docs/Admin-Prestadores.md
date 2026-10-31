# Administração de prestadores

As rotas administrativas exigem JWT com `ROLE_ADMIN`. O contrato usa nomes em `snake_case`, conforme o aplicativo administrativo.

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
