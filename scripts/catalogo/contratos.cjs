const assert = require('node:assert/strict');

function validarCategorias(dados) {
  assert.ok(Array.isArray(dados), 'Categorias devem ser um array direto, sem envelope categoria.');
  for (const categoria of dados) {
    assert.ok(Number.isSafeInteger(categoria.id) && categoria.id > 0, 'ID de categoria inválido.');
    assert.equal(typeof categoria.nome, 'string', 'Categoria sem nome.');
    assert.equal(categoria.status, 'ativa', 'Categoria indisponível na resposta pública.');
    assert.ok(categoria.foto_url === null || typeof categoria.foto_url === 'string', 'foto_url inválida.');
  }
  return dados;
}

function validarPrestador(dados) {
  assert.ok(dados && Number.isSafeInteger(dados.id) && dados.id > 0, 'ID de prestador inválido.');
  assert.ok(typeof dados.nomeEstab === 'string' && dados.nomeEstab.trim(), 'Esperado nomeEstab no contrato atual.');
  assert.match(dados.perfilUsuarioId, /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i, 'Perfil deve ser UUID.');
  assert.equal(dados.status, 'ativo', 'Prestador não aprovado na resposta pública.');
  assert.equal(dados.ativo, true, 'Prestador inativo na resposta pública.');
  assert.ok(dados.fotoUrl === null || typeof dados.fotoUrl === 'string', 'Esperado fotoUrl no contrato atual.');
  return dados;
}

function validarHorarios(dados, prestadorId) {
  assert.ok(Array.isArray(dados), 'Horários devem ser um array.');
  const formatoHora = /^(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d$/;
  for (const horario of dados) {
    assert.ok(Number.isSafeInteger(horario.id) && horario.id > 0, 'ID de horário inválido.');
    assert.equal(horario.prestador_id, prestadorId, 'Horário vinculado a outro prestador.');
    assert.ok(Number.isInteger(horario.dia_semana) && horario.dia_semana >= 1 && horario.dia_semana <= 7);
    assert.match(horario.hora_inicio, formatoHora);
    assert.match(horario.hora_fim, formatoHora);
    assert.ok(horario.hora_inicio < horario.hora_fim, 'Intervalo de atendimento inválido.');
  }
  return dados;
}

function validarServicos(dados, prestadorId) {
  assert.ok(Array.isArray(dados), 'Serviços devem ser um array.');
  for (const servico of dados) {
    assert.ok(Number.isSafeInteger(servico.id) && servico.id > 0, 'ID de serviço inválido.');
    assert.equal(servico.prestador_id, prestadorId);
    assert.equal(typeof servico.nome, 'string');
    assert.ok(typeof servico.preco === 'number' && Number.isFinite(servico.preco) && servico.preco >= 0);
    assert.ok(Number.isInteger(servico.duracao_min) && servico.duracao_min > 0);
    assert.equal(servico.status, 'ativo');
  }
  return dados;
}

function rotaPrestadores({ nome, categoriaId } = {}) {
  const parametros = new URLSearchParams();
  if (nome?.trim()) parametros.set('nome', nome.trim());
  if (categoriaId !== undefined && categoriaId !== null) parametros.set('categoria_id', String(categoriaId));
  const consulta = parametros.toString();
  return `/catalogo/prestadores${consulta ? `?${consulta}` : ''}`;
}

// Monta dados de apresentação para as três telas, sem depender de React Native ou de autenticação.
async function carregarTelas(buscarJson, { filtros, rotasSimuladas = [] } = {}) {
  const categorias = validarCategorias(await buscarJson('/catalogo/categorias'));
  const prestadores = await buscarJson(rotaPrestadores(filtros));
  assert.ok(Array.isArray(prestadores), 'Prestadores devem ser um array.');
  const cards = [];
  for (const dados of prestadores) {
    const prestador = validarPrestador(dados);
    const caminho = `/catalogo/prestadores/${prestador.id}`;
    const categoriasPrestador = validarCategorias(await buscarJson(`${caminho}/categorias`));
    const horarios = validarHorarios(await buscarJson(`${caminho}/horarios`), prestador.id);
    cards.push({
      id: prestador.id,
      titulo: prestador.nomeEstab,
      fotoUrl: prestador.fotoUrl,
      categorias: categoriasPrestador.map((categoria) => categoria.nome),
      horarios: horarios.map((horario) => ({
        diaSemana: horario.dia_semana,
        inicio: horario.hora_inicio.slice(0, 5),
        fim: horario.hora_fim.slice(0, 5),
      })),
    });
  }

  let detalhe = null;
  const pendencias = [];
  if (cards.length) {
    const card = cards[0];
    const caminho = `/catalogo/prestadores/${card.id}`;
    const prestador = validarPrestador(await buscarJson(caminho));
    assert.equal(prestador.id, card.id, 'Detalhe não corresponde ao prestador selecionado.');
    const rotaServicos = `${caminho}/servicos`;
    let servicos;
    try {
      const itens = validarServicos(await buscarJson(rotaServicos), card.id);
      const simulado = rotasSimuladas.includes(rotaServicos);
      servicos = { situacao: simulado ? 'simulado' : 'consumido', itens };
      if (simulado) pendencias.push('Serviços consumidos de fixture: endpoint JN-008 ainda pendente nesta base.');
    } catch (erro) {
      if (erro.status !== 404) throw erro;
      servicos = { situacao: 'pendente', itens: [] };
      pendencias.push('Consulta de serviços retornou 404; o detalhe completo depende da JN-008.');
    }
    detalhe = {
      ...card,
      perfilUsuarioId: prestador.perfilUsuarioId,
      endereco: [prestador.endereco, prestador.numero, prestador.bairro, prestador.cidade, prestador.estado]
        .filter(Boolean).join(', '),
      servicos,
    };
  }

  return {
    telas: {
      categorias: categorias.map(({ id, nome, foto_url }) => ({ id, titulo: nome, fotoUrl: foto_url })),
      busca: cards,
      detalhe,
    },
    pendencias,
  };
}

module.exports = { validarCategorias, validarPrestador, validarHorarios, rotaPrestadores, carregarTelas };
