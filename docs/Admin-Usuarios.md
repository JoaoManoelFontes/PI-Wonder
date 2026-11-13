# Administração de usuários

A identidade permanece no Keycloak. O Core não duplica nome, e-mail ou estado da conta no PostgreSQL. Foto e telefone continuam no perfil local e não são alterados por estas operações. O cadastro permanece no autorregistro do Keycloak; este fluxo administrativo não cria contas nem altera senhas ou roles.

Todas as rotas abaixo exigem a realm role `ADMIN`. `id` é o UUID do usuário no Keycloak, não o UUID do perfil local.

| Método e rota | Operação |
|---|---|
| `GET /admin/usuarios?busca=&inicio=0&limite=20` | Busca por nome, username ou e-mail; início >= 0, limite entre 1 e 100 |
| `GET /admin/usuarios/{id}` | Consulta a identidade atual |
| `PUT /admin/usuarios/{id}` | Atualiza `nome`, `sobrenome` e `email` |
| `PATCH /admin/usuarios/{id}/status` | Recebe `{"ativo":false}` ou `{"ativo":true}` |

A listagem retorna `usuarios`, `inicio`, `limite` e `temProxima`. Cada usuário contém `id`, `username`, `nome`, `sobrenome`, `email` e `ativo`. Contas de serviço não podem ser gerenciadas por estas rotas.

Na edição, nome e e-mail são obrigatórios; sobrenome pode ficar vazio. Username, atributos e demais informações existentes são preservados. A troca do e-mail remove sua marcação de verificado. E-mail duplicado retorna `409`; dados inválidos, `400`; usuário inexistente, `404`. Não é permitido desativar a própria conta (`409`).

## Desativação e sessões

Desativar atualiza `enabled=false` no Keycloak e chama o logout administrativo para encerrar as sessões do usuário. Não há exclusão física: perfil, agendamentos e histórico permanecem no banco.

O Spring Security consulta o endpoint de introspecção do Keycloak a cada requisição com Bearer token, sem cache, inclusive quando o access token é JWT. Assim, uma sessão encerrada deixa de dar acesso à API antes de o token expirar. As roles de `realm_access` continuam mapeadas para `ROLE_CUSTOMER`, `ROLE_PROVIDER` e `ROLE_ADMIN`.

O aplicativo detecta o bloqueio na próxima requisição protegida: recebe `401`, tenta renovar a sessão e, se o refresh foi revogado, limpa a sessão e retorna ao login. Não há comunicação push que feche uma tela ociosa instantaneamente. Reativar permite novo login, sem restaurar as sessões encerradas.

Se a atualização no Keycloak ocorrer e o logout falhar, a conta permanece desativada e a API responde `503`, sem anunciar sucesso completo. Consulte a conta e repita a desativação para tentar encerrar novamente as sessões. Não há rollback que reative uma conta devido a uma falha de comunicação.

## Configuração

A introspecção reutiliza `KEYCLOAK_SERVER_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID` e `KEYCLOAK_CLIENT_SECRET` da integração administrativa. O client confidencial `wonder-core` já possui `manage-users`; o frontend continua usando o client público `wonder-web`, sem receber o secret.

`KEYCLOAK_SERVER_URL` precisa ser acessível **pelo processo do Core**: por exemplo `http://localhost:8081` quando o Core roda no computador e Keycloak no Compose, ou o endereço interno do Keycloak quando ambos rodam em containers na mesma rede. O endereço público de login do frontend continua configurado separadamente.

Os limites `KEYCLOAK_CONNECT_TIMEOUT` (3s) e `KEYCLOAK_READ_TIMEOUT` (5s) também se aplicam à introspecção. Requisições autenticadas dependem da disponibilidade do Keycloak. Falhas de introspecção devem recusar acesso; token inativo retorna `401`, indisponibilidade retorna `503` para não apagar uma sessão válida devido a uma falha temporária.

## Validação

`AdminUsuarioIntegrationTest` inicia Keycloak real com o realm do projeto, PostgreSQL por Testcontainers. Um client com grant de senha é acrescentado apenas ao realm descartável do teste, sem habilitar esse fluxo no realm de produção. Os testes emitem tokens e sessões reais e verificam edição, preservação do perfil, busca/paginação, permissões, duplicidade, desativação, rejeição do access token e refresh e reativação com novo login.

Testes unitários cobrem ausência de cache, mapeamento de roles, indisponibilidade e recuperação de logout parcialmente concluído. No aplicativo, testes executam a tela e os serviços com HTTP simulado e verificam confirmação, payload, erros e preservação da sessão em `503`.
