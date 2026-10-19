# Disponibilidade: exemplos de request e response — JN-015

As consultas abaixo funcionam sem login e retornam objetos JSON. Use os comandos sem header `Authorization` para conferir o acesso público.

| Método e rota | Parâmetros obrigatórios | Resposta de sucesso |
| --- | --- | --- |
| `GET /agendamentos/dias-disponiveis` | `prestador_id`, `servico_id`, `mes` | Dias do mês com pelo menos um horário livre. |
| `GET /agendamentos/disponibilidade` | `prestador_id`, `servico_id`, `data` | Intervalos livres para o serviço na data consultada. |

## Preparação

Inicie o `core` conforme o [README](../README.md). Os exemplos usam a porta padrão `8080` e os dados das migrations `V5__seed_catalogo_minimo.sql` e `V6__seed_cliente_agendamentos_minimos.sql`.

Obtenha os IDs do Studio Wonder e de seu serviço Corte de cabelo:

```powershell
$prestadores = @(Invoke-RestMethod 'http://localhost:8080/catalogo/prestadores?nome=Studio%20Wonder')
$prestadorId = ($prestadores | Where-Object { $_.perfilUsuarioId -eq '10000000-0000-0000-0000-000000000001' }).id
$servicos = @(Invoke-RestMethod "http://localhost:8080/catalogo/prestadores/$prestadorId/servicos")
$servicoId = ($servicos | Where-Object { $_.nome -eq 'Corte de cabelo' }).id
```

Os payloads usam `prestador_id = 1` e `servico_id = 1` apenas como ilustração. Os comandos usam os IDs consultados. Os resultados abaixo pressupõem o seed sem alterações:

- Corte de cabelo dura 45 minutos.
- O Studio Wonder atende de segunda a sexta das 09:00 às 18:00 e aos sábados das 09:00 às 13:00.
- Em 07/01/2030 há um agendamento `CONFIRMADO` das 10:00 às 10:45 e outro `CANCELADO` das 11:00 às 11:45.

## Parâmetros e regras

| Parâmetro | Formato e regra |
| --- | --- |
| `prestador_id` | ID numérico convertido em `Long`. O prestador precisa ter `status = ATIVO` e `ativo = true`. |
| `servico_id` | ID numérico convertido em `Long`. O serviço precisa estar `ATIVO` e pertencer ao prestador informado. |
| `mes` | Ano e mês, por exemplo `2030-01`, para a consulta mensal. |
| `data` | Data ISO `yyyy-MM-dd`, por exemplo `2030-01-07`, para a consulta diária. |

O cálculo usa a duração do serviço como tamanho e passo dos intervalos, a partir do início de cada janela de atendimento. Não retorna intervalos incompletos no fim da janela nem atravessa pausas entre janelas. O backend usa datas e horas locais, sem sufixo de fuso nas respostas.

Agendamentos do mesmo prestador bloqueiam os intervalos sobrepostos, inclusive quando pertencem a outro serviço. Somente `CANCELADO` é ignorado; `PENDENTE`, `CONFIRMADO` e `CONCLUIDO` participam do bloqueio. Agendamentos de outros prestadores não interferem.

Existe conflito quando `inicioAgendamento < fimIntervalo` e `fimAgendamento > inicioIntervalo`. Terminar exatamente no início ou começar exatamente no fim de um intervalo não gera conflito. Agendamentos que atravessam dias ou meses também são considerados pela sobreposição.

O cálculo atual não descarta datas ou horários com base no relógio atual. As consultas não criam nem reservam agendamentos; a validação na criação pertence ao fluxo de agendamento autenticado.

## Dias disponíveis — JN-013

```http
GET /agendamentos/dias-disponiveis?prestador_id=1&servico_id=1&mes=2030-01
Accept: application/json
```

```powershell
curl.exe -i "http://localhost:8080/agendamentos/dias-disponiveis?prestador_id=$prestadorId&servico_id=$servicoId&mes=2030-01"
```

Resposta `200 OK`, com `Content-Type: application/json`:

```json
{
  "prestador_id": 1,
  "servico_id": 1,
  "mes": "2030-01",
  "dias": [
    {"data": "2030-01-01", "disponivel": true},
    {"data": "2030-01-02", "disponivel": true},
    {"data": "2030-01-03", "disponivel": true},
    {"data": "2030-01-04", "disponivel": true},
    {"data": "2030-01-05", "disponivel": true},
    {"data": "2030-01-07", "disponivel": true},
    {"data": "2030-01-08", "disponivel": true},
    {"data": "2030-01-09", "disponivel": true},
    {"data": "2030-01-10", "disponivel": true},
    {"data": "2030-01-11", "disponivel": true},
    {"data": "2030-01-12", "disponivel": true},
    {"data": "2030-01-14", "disponivel": true},
    {"data": "2030-01-15", "disponivel": true},
    {"data": "2030-01-16", "disponivel": true},
    {"data": "2030-01-17", "disponivel": true},
    {"data": "2030-01-18", "disponivel": true},
    {"data": "2030-01-19", "disponivel": true},
    {"data": "2030-01-21", "disponivel": true},
    {"data": "2030-01-22", "disponivel": true},
    {"data": "2030-01-23", "disponivel": true},
    {"data": "2030-01-24", "disponivel": true},
    {"data": "2030-01-25", "disponivel": true},
    {"data": "2030-01-26", "disponivel": true},
    {"data": "2030-01-28", "disponivel": true},
    {"data": "2030-01-29", "disponivel": true},
    {"data": "2030-01-30", "disponivel": true},
    {"data": "2030-01-31", "disponivel": true}
  ]
}
```

O DTO `DiasDisponiveisResponse` retorna `prestador_id`, `servico_id`, `mes` e `dias`. Cada item contém `data` e `disponivel = true`. Dias sem intervalo livre são omitidos, não aparecem com `disponivel = false`. A lista segue a ordem das datas do mês.

O dia 07 aparece porque ainda tem horários livres. Os domingos 06, 13, 20 e 27 não aparecem porque o seed não possui atendimento aos domingos. Feriados não têm tratamento próprio no modelo atual.

## Horários disponíveis — JN-014

```http
GET /agendamentos/disponibilidade?prestador_id=1&servico_id=1&data=2030-01-07
Accept: application/json
```

```powershell
curl.exe -i "http://localhost:8080/agendamentos/disponibilidade?prestador_id=$prestadorId&servico_id=$servicoId&data=2030-01-07"
```

Resposta `200 OK`, com `Content-Type: application/json`:

```json
{
  "prestador_id": 1,
  "servico_id": 1,
  "data": "2030-01-07",
  "duracao_min": 45,
  "horarios": [
    {"inicio": "2030-01-07T09:00:00", "fim": "2030-01-07T09:45:00"},
    {"inicio": "2030-01-07T11:15:00", "fim": "2030-01-07T12:00:00"},
    {"inicio": "2030-01-07T12:00:00", "fim": "2030-01-07T12:45:00"},
    {"inicio": "2030-01-07T12:45:00", "fim": "2030-01-07T13:30:00"},
    {"inicio": "2030-01-07T13:30:00", "fim": "2030-01-07T14:15:00"},
    {"inicio": "2030-01-07T14:15:00", "fim": "2030-01-07T15:00:00"},
    {"inicio": "2030-01-07T15:00:00", "fim": "2030-01-07T15:45:00"},
    {"inicio": "2030-01-07T15:45:00", "fim": "2030-01-07T16:30:00"},
    {"inicio": "2030-01-07T16:30:00", "fim": "2030-01-07T17:15:00"},
    {"inicio": "2030-01-07T17:15:00", "fim": "2030-01-07T18:00:00"}
  ]
}
```

O DTO `DisponibilidadeResponse` retorna os IDs consultados, `data`, `duracao_min` e `horarios`. Cada item de `horarios` contém `inicio` e `fim`, como datas e horas ISO locais.

O agendamento confirmado das 10:00 às 10:45 remove os intervalos das 09:45 às 10:30 e das 10:30 às 11:15. O cálculo mantém a grade original de 45 minutos; não desloca os próximos inícios para as 10:45. O intervalo das 11:15 às 12:00 continua livre porque o agendamento das 11:00 às 11:45 está cancelado.

## Ausência de disponibilidade

Para consultar o domingo 06/01/2030, sem atendimento no seed:

```powershell
curl.exe -i "http://localhost:8080/agendamentos/disponibilidade?prestador_id=$prestadorId&servico_id=$servicoId&data=2030-01-06"
```

Resposta `200 OK`:

```json
{
  "prestador_id": 1,
  "servico_id": 1,
  "data": "2030-01-06",
  "duracao_min": 45,
  "horarios": []
}
```

A mesma estrutura com `horarios = []` vale para um dia totalmente ocupado, prestador sem horários cadastrados ou serviço cuja duração não caiba em nenhuma janela do dia.

Se nenhum dia do mês tiver intervalo livre, a consulta mensal retorna `200 OK` com a estrutura abaixo. Este cenário exige uma agenda sem disponibilidade; não corresponde ao seed padrão de janeiro:

```json
{
  "prestador_id": 1,
  "servico_id": 1,
  "mes": "2030-01",
  "dias": []
}
```

## Erros e acesso

| Situação | Resultado nas duas consultas |
| --- | --- |
| Dados válidos, com ou sem login | `200`, com objeto e lista preenchida ou vazia. |
| Parâmetro obrigatório ausente | `400`. |
| IDs que não possam ser convertidos em `Long` | `400`. |
| Mês ou data inválidos, como `2030-13` ou `2030-02-30` | `400`. |
| Prestador inexistente, inativo ou com status diferente de `ATIVO` | `404`. |
| Serviço inexistente, inativo ou pertencente a outro prestador | `404`. |

Exemplos de `400`:

```powershell
curl.exe -i "http://localhost:8080/agendamentos/dias-disponiveis?prestador_id=$prestadorId&servico_id=$servicoId&mes=2030-13"
curl.exe -i "http://localhost:8080/agendamentos/disponibilidade?prestador_id=$prestadorId&servico_id=$servicoId&data=2030-02-30"
curl.exe -i "http://localhost:8080/agendamentos/disponibilidade?prestador_id=$prestadorId&data=2030-01-07"
```

Para conferir `404`, use um ID de serviço inexistente no banco:

```powershell
curl.exe -i "http://localhost:8080/agendamentos/dias-disponiveis?prestador_id=$prestadorId&servico_id=9223372036854775807&mes=2030-01"
curl.exe -i "http://localhost:8080/agendamentos/disponibilidade?prestador_id=$prestadorId&servico_id=9223372036854775807&data=2030-01-07"
```

O contrato fixa os status HTTP; o corpo dos erros segue o tratamento atual do Spring. A liberação de acesso sem login vale somente para os dois GETs de consulta; não libera outros métodos nem a listagem de agendamentos de usuários.

## Critérios de aceite e validação — LW-015

- As duas consultas respondem sem token para prestador público e serviço ativo vinculado a ele.
- Apenas dias com pelo menos um intervalo livre aparecem na consulta mensal.
- Os intervalos respeitam duração, janelas de atendimento e conflitos do prestador.
- Cancelamentos e agendamentos de outros prestadores não bloqueiam horários.
- As consultas mensal e diária concordam sobre a existência de disponibilidade.
- Ausência de disponibilidade, parâmetros inválidos e recursos indisponíveis seguem os contratos acima.

`AgendamentoDiasDisponiveisIntegrationTest` e `AgendamentoDisponibilidadeIntegrationTest` validam os payloads básicos e o acesso com e sem JWT. `DisponibilidadePublicaIntegrationTest` cobre a revisão cruzada, incluindo parâmetros, publicação, vínculo do serviço, conflitos parciais, estados dos agendamentos, pausas, durações e sobreposição entre meses. `DisponibilidadeServiceTest` cobre as regras em testes unitários; `SecurityIntegrationTests` verifica a proteção das demais rotas e métodos.

Com Docker disponível, execute na raiz do projeto:

```powershell
.\mvnw.cmd -pl core test '-Dtest=AgendamentoDiasDisponiveisIntegrationTest,AgendamentoDisponibilidadeIntegrationTest,DisponibilidadePublicaIntegrationTest,DisponibilidadeServiceTest,SecurityIntegrationTests'
```

Para executar toda a suíte do `core`:

```powershell
.\mvnw.cmd -pl core test
```
