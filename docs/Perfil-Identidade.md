# Edição do próprio perfil

Clientes, prestadores e administradores podem consultar e editar sua própria identidade usando as rotas autenticadas abaixo. A conta é obtida exclusivamente do `sub` da sessão; a rota não recebe o identificador de outro usuário.

| Método e rota | Contrato |
|---|---|
| `GET /perfis/eu/identidade` | Retorna `id` (Keycloak), `nome`, `sobrenome` e `email` atuais |
| `PUT /perfis/eu/identidade` | Recebe `nome`, `sobrenome` e `email`; retorna a identidade atualizada |

Nome e e-mail são obrigatórios; sobrenome pode ficar vazio. A edição reutiliza a validação e a operação de identidade já existentes na administração, sem conceder ao usuário acesso às rotas administrativas. Username, roles, estado da conta e atributos são preservados. Alterar e-mail remove a marcação de verificado; não foi acrescentado um fluxo de confirmação de e-mail nesta entrega.

Dados inválidos retornam `400`, e-mail duplicado `409` e indisponibilidade da integração `503`. As rotas antigas `GET /perfis/eu` e `PUT /perfis/eu` continuam com o mesmo contrato de foto e telefone. Não há alteração de banco ou migration.

O aplicativo utiliza o formulário de perfil existente para clientes e prestadores, preservando o redesign. Consulta a identidade atual para exibição, pois o token emitido antes da edição pode continuar contendo nome e e-mail antigos. Essa consulta não muda as permissões da sessão.

Nome/e-mail e foto/telefone são persistidos separadamente. Todos os campos são validados no aplicativo antes do envio; a identidade é enviada primeiro. Se essa etapa for concluída e a seguinte falhar, o aplicativo informa a atualização parcial. Não há transação distribuída ou promessa de rollback entre Keycloak, banco e armazenamento. A proteção de sessão impede iniciar a segunda escrita caso a conta tenha mudado durante a operação.

Integre o backend antes do frontend: o aplicativo atualizado depende das duas novas rotas para carregar o perfil.

## Verificação

`PerfilIdentidadeWebTests` verifica autenticação, papéis, validação, uso da conta da sessão, preservação de atributos e tratamento de falhas com a integração simulada. `AdminUsuarioIntegrationTest` também verifica a edição do próprio perfil de cliente e prestador contra Keycloak e PostgreSQL reais. Os testes do aplicativo verificam o contrato HTTP, exibição com token antigo, conflito de e-mail, falha parcial e troca de sessão durante a gravação.
