# Validação do consumo do catálogo — LW-013

Esta entrega simula os dados de apresentação das telas de categorias, busca de prestadores e detalhe dentro do PI-Wonder. O aplicativo antigo permanece como referência. A simulação é executada no terminal: não renderiza telas React Native e não substitui um teste do aplicativo Expo.

## Referências conferidas

No repositório antigo `wonder-app`, foram consultados estes arquivos de `wonder-mobile/src`:

- `types/catalogo.ts` e `services/catalogo.ts`: tipos, rotas e parâmetros usados pelo cliente.
- `screens/HomeScreen.tsx` e `screens/SearchScreen.tsx`: categorias, listagem e filtros.
- `screens/PrestadorProfileScreen.tsx`: carregamento de detalhe, serviços, horários e categorias.
- `utils/catalogPresentation.ts` e `components/catalog/ProviderCard.tsx`: dados necessários aos cards.
- `services/api.ts`: URL base e uso de autenticação no cliente anterior.

Os contratos adotados são os DTOs e endpoints atuais do PI-Wonder. A arquitetura e os nomes antigos não foram copiados para o backend.

## Diferenças encontradas

| Consumo antigo | Contrato atual | Tratamento na simulação |
| --- | --- | --- |
| Prestador com `nome_estab` | `nomeEstab` | Usa o campo atual para montar o título do card e do detalhe. |
| Prestador com `foto_url` | `fotoUrl` | Usa o campo atual e aceita imagem `null`. |
| `usuario_id` do sistema antigo | `perfilUsuarioId`, UUID do perfil local | Mantém o significado atual; não cria um alias de identidade para `usuario_id`. |
| Categorias do prestador em `[{ prestador_id, categoria }]` | Array direto de `CategoriaPublicResponse` | Consome a lista direta; não aplica o antigo `.map(item => item.categoria)`. |
| Categoria com `foto_url` | Continua `foto_url` | Preserva o nome do DTO de categoria. |
| Horário com `dia_semana`, `hora_inicio`, `hora_fim` | Mesmos campos, com horas em `HH:mm:ss` | Converte para `HH:mm` apenas na apresentação; os dias continuam de 1 a 7. |
| URL padrão do cliente antigo na porta 8000 | `core` usa 8080 por padrão | A validação real recebe a URL base por argumento. Um frontend integrado deverá configurar sua URL de API. |

A simulação não interpreta horário de atendimento como disponibilidade para agendamento. O backend já oferece `GET /agendamentos/dias-disponiveis` e `GET /agendamentos/disponibilidade`, ambos sem login; o consumo dessas duas rotas não faz parte dos scripts de catálogo.

## Estado por tela

| Tela de referência | Dados necessários | Estado nesta base |
| --- | --- | --- |
| Categorias | `GET /catalogo/categorias` | Contrato consumido pela simulação e formato conferido contra o HTTP real em teste. |
| Busca/cards | `GET /catalogo/prestadores`, categorias e horários de cada prestador | Consumo simulado; parâmetros `nome` e `categoria_id` conferidos no cliente de validação. O comportamento dos filtros é coberto pelos testes HTTP existentes. |
| Detalhe básico | Detalhe do prestador, categorias e horários | Consumo simulado; formato dos três endpoints conferido contra o HTTP real em teste. |
| Serviços no detalhe | `GET /catalogo/prestadores/{id}/servicos` | Endpoint implementado na JN-008, com testes HTTP próprios. No modo de fixtures, os dados continuam identificados como simulados; no modo `--api`, o script consome o endpoint real. |

No app antigo, o detalhe usa `Promise.all` para as quatro consultas; uma falha em serviços impede a tela inteira de carregar. O script mantém o tratamento de `404` na consulta de serviços: registra uma pendência e preserva os demais dados do detalhe. Esse comportamento é uma limitação do cliente de validação: um `404` também pode indicar prestador inexistente ou indisponível, e não comprova ausência do endpoint. Outros erros HTTP e contratos inválidos são propagados, não convertidos em listas vazias.

As quatro consultas necessárias ao detalhe estão implementadas no backend. Os exemplos e regras de serviços estão no [guia do catálogo público](Catalogo-Publico-Exemplos.md). A validação permanece uma simulação de consumo no terminal; a adaptação e a execução das telas do aplicativo Expo continuam fora desta entrega.

## Executar a simulação

Os scripts usam recursos nativos do Node já instalado, sem pacotes npm. Foram validados com Node 24.14.1.

Na raiz do PI-Wonder:

```powershell
node scripts/catalogo/simular.cjs
```

A saída contém `telas.categorias`, `telas.busca`, `telas.detalhe` e `pendencias`. A origem é identificada como `fixtures de simulação`, e `detalhe.servicos.situacao` indica `simulado`.

As respostas ilustrativas ficam em `core/src/test/resources/catalogo-frontend.json`, compartilhadas com o teste HTTP de formato. Os dados são um recorte do seed, não uma captura da API. IDs, quantidade de registros e valores ilustrativos não são fixados como contrato pelo teste. O campo legado `rotasSimuladasSemEndpoint` ainda marca os serviços como simulados no modo de fixtures; esse nome não representa o inventário atual de endpoints do backend.

Para consumir uma API já iniciada, sem enviar token:

```powershell
node scripts/catalogo/simular.cjs --api http://localhost:8080
```

Nesse modo, os dados vêm exclusivamente da API informada. O script seleciona o primeiro prestador retornado para montar o detalhe e consulta seus serviços no endpoint implementado. Uma resposta bem-sucedida, incluindo lista vazia, será identificada como `consumido`. Se a consulta de serviços retornar `404`, a situação será `pendente`, conforme o tratamento descrito acima. Nenhuma fixture é usada como fallback da API real.

## Verificações automatizadas

```powershell
node --test scripts/catalogo/contratos.test.cjs
```

Os testes verificam:

- montagem dos dados das três telas e identificação explícita do serviço simulado;
- nomes e codificação dos parâmetros de busca;
- rejeição de `nome_estab` e do envelope antigo de categorias;
- listas vazias e ausência de detalhe quando não há prestadores;
- tratamento da pendência de serviços sem ocultar erros HTTP 500;
- propagação de `404` no detalhe e rejeição de horários de outro prestador.

O `CatalogoFrontendContractIntegrationTest` compara nomes e tipos dos campos das fixtures com cinco das seis rotas de catálogo, via MockMvc e PostgreSQL: categorias gerais, prestadores, detalhe, horários e categorias do prestador. A rota de serviços ainda não foi incluída nessa comparação compartilhada; seu contrato e comportamento são validados separadamente pelo `CatalogoServicosIntegrationTest`. O Docker precisa estar disponível:

```powershell
.\mvnw.cmd -pl core test '-Dtest=CatalogoFrontendContractIntegrationTest,CatalogoServicosIntegrationTest'
```

Os testes HTTP anteriores continuam responsáveis pelos valores, critérios de publicação, filtros, ordenação e ausência de duplicações. A implementação desta task não altera DTOs, controllers, schema, autenticação ou arquivos do aplicativo antigo.
