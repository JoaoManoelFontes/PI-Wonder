const { carregarTelas } = require('./contratos.cjs');
const fixtures = require('../../core/src/test/resources/catalogo-frontend.json');

function criarFonteSimulada(dados = fixtures) {
  return async (rota) => {
    if (!Object.hasOwn(dados.respostas, rota)) {
      const erro = new Error(`Rota sem fixture: ${rota}`);
      erro.status = 404;
      throw erro;
    }
    return structuredClone(dados.respostas[rota]);
  };
}

function criarFonteApi(baseUrl) {
  const url = new URL(baseUrl);
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Informe uma URL HTTP ou HTTPS.');
  return async (rota) => {
    const resposta = await fetch(`${baseUrl.replace(/\/$/, '')}${rota}`, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(10000),
    });
    if (!resposta.ok) {
      const erro = new Error(`GET ${rota}: HTTP ${resposta.status}`);
      erro.status = resposta.status;
      throw erro;
    }
    return resposta.json();
  };
}

async function executar() {
  const argumentos = process.argv.slice(2);
  if (argumentos.length && (argumentos.length !== 2 || argumentos[0] !== '--api')) {
    throw new Error('Uso: node scripts/catalogo/simular.cjs [--api http://localhost:8080]');
  }
  const api = argumentos[0] === '--api';
  const resultado = await carregarTelas(api ? criarFonteApi(argumentos[1]) : criarFonteSimulada(), {
    rotasSimuladas: api ? [] : fixtures.rotasSimuladasSemEndpoint,
  });
  console.log(JSON.stringify({ origem: api ? 'API real' : 'fixtures de simulação', ...resultado }, null, 2));
}

if (require.main === module) {
  executar().catch((erro) => {
    console.error(`Falha na validação do catálogo: ${erro.message}`);
    process.exitCode = 1;
  });
}

module.exports = { criarFonteSimulada, criarFonteApi };
