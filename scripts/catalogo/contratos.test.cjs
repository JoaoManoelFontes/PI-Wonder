const { test } = require('node:test');
const assert = require('node:assert/strict');
const { carregarTelas, validarCategorias, validarPrestador, validarHorarios, rotaPrestadores } = require('./contratos.cjs');
const { criarFonteSimulada, criarFonteApi } = require('./simular.cjs');
const fixtures = require('../../core/src/test/resources/catalogo-frontend.json');

test('monta categorias, busca e detalhe com o contrato atual e identifica serviços simulados', async () => {
  const resultado = await carregarTelas(criarFonteSimulada(), {
    rotasSimuladas: fixtures.rotasSimuladasSemEndpoint,
  });
  assert.equal(resultado.telas.categorias[0].titulo, 'Barba');
  assert.equal(resultado.telas.busca[0].titulo, 'Studio Wonder');
  assert.equal(resultado.telas.busca[0].fotoUrl, null);
  assert.deepEqual(resultado.telas.busca[0].categorias, ['Cabelo', 'Sobrancelhas', 'Unhas']);
  assert.equal(resultado.telas.detalhe.horarios[0].inicio, '09:00');
  assert.equal(resultado.telas.detalhe.servicos.situacao, 'simulado');
  assert.equal(resultado.telas.detalhe.servicos.itens[0].preco, 50);
  assert.ok(resultado.pendencias.some((pendencia) => pendencia.includes('JN-008')));
  assert.equal(Object.hasOwn(resultado.telas.detalhe, 'usuario_id'), false);
});

test('gera os parâmetros nome e categoria_id usados pela busca', () => {
  const url = new URL(rotaPrestadores({ nome: '  Studio & Beleza  ', categoriaId: 4 }), 'http://localhost');
  assert.equal(url.pathname, '/catalogo/prestadores');
  assert.equal(url.searchParams.get('nome'), 'Studio & Beleza');
  assert.equal(url.searchParams.get('categoria_id'), '4');
  assert.equal(rotaPrestadores({ nome: '   ' }), '/catalogo/prestadores');
});

test('rejeita o nome_estab antigo para impedir nome indefinido na apresentação', () => {
  const antigo = structuredClone(fixtures.respostas['/catalogo/prestadores/1']);
  antigo.nome_estab = antigo.nomeEstab;
  delete antigo.nomeEstab;
  assert.throws(() => validarPrestador(antigo), /nomeEstab/);
});

test('rejeita o envelope antigo das categorias do prestador', () => {
  const categoria = fixtures.respostas['/catalogo/prestadores/1/categorias'][0];
  assert.throws(() => validarCategorias([{ prestador_id: 1, categoria }]));
});

test('monta estados vazios sem tentar abrir detalhe inexistente', async () => {
  const dados = structuredClone(fixtures);
  dados.respostas['/catalogo/categorias'] = [];
  dados.respostas['/catalogo/prestadores'] = [];
  const resultado = await carregarTelas(criarFonteSimulada(dados));
  assert.deepEqual(resultado.telas, { categorias: [], busca: [], detalhe: null });
});

test('mantém o detalhe consultável quando o endpoint de serviços retorna 404', async () => {
  const dados = structuredClone(fixtures);
  delete dados.respostas['/catalogo/prestadores/1/servicos'];
  const resultado = await carregarTelas(criarFonteSimulada(dados));
  assert.equal(resultado.telas.detalhe.titulo, 'Studio Wonder');
  assert.equal(resultado.telas.detalhe.servicos.situacao, 'pendente');
  assert.equal(resultado.pendencias.length, 1);
});

test('não converte erro HTTP 500 em lista vazia de serviços', async () => {
  const fonte = criarFonteSimulada();
  await assert.rejects(() => carregarTelas(async (rota) => {
    if (rota.endsWith('/servicos')) {
      const erro = new Error('HTTP 500');
      erro.status = 500;
      throw erro;
    }
    return fonte(rota);
  }), /HTTP 500/);
});

test('propaga 404 no detalhe e rejeita horários de outro prestador', async () => {
  const dados = structuredClone(fixtures);
  delete dados.respostas['/catalogo/prestadores/1'];
  await assert.rejects(() => carregarTelas(criarFonteSimulada(dados)), { status: 404 });
  const horarios = structuredClone(fixtures.respostas['/catalogo/prestadores/1/horarios']);
  horarios[0].prestador_id = 2;
  assert.throws(() => validarHorarios(horarios, 1), /outro prestador/);
});

test('cliente HTTP consulta sem token e preserva o status de erro, sem fallback para fixtures', async (contexto) => {
  const fonte = criarFonteApi('http://localhost:8080/');
  const interceptacao = contexto.mock.method(globalThis, 'fetch', async (url, opcoes) => {
    assert.equal(url, 'http://localhost:8080/catalogo/categorias');
    assert.deepEqual(opcoes.headers, { Accept: 'application/json' });
    return new Response(JSON.stringify([]), { status: 200 });
  });
  assert.deepEqual(await fonte('/catalogo/categorias'), []);
  interceptacao.mock.mockImplementation(async () => new Response('', { status: 404 }));
  await assert.rejects(() => fonte('/catalogo/categorias'), { status: 404 });
});
