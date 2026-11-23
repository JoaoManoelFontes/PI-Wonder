package br.edu.ifrn.wonder.worker.service;

import static org.assertj.core.api.Assertions.*;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import br.edu.ifrn.wonder.worker.Contratos;
import br.edu.ifrn.wonder.worker.dto.ExportacaoSolicitada;
import br.edu.ifrn.wonder.worker.dto.UsuariosSnapshot;
import tools.jackson.databind.json.JsonMapper;

class GeradorCsvServiceTest {
    private final ContratoExportacaoService contrato = new ContratoExportacaoService();
    private final GeradorCsvService gerador = new GeradorCsvService();

    private String gerar(String tipo) throws Exception {
        var pedido = contrato.ler(Contratos.corpo(tipo));
        var destino = new StringWriter();
        gerador.escrever(destino, pedido.tipo(), contrato.snapshot(pedido));
        return destino.toString();
    }

    @Test
    void usuariosPreservaColunasOrdemNulosAcentosESituacao() throws Exception {
        String csv = gerar("usuarios");
        assertThat(csv).isEqualTo("\uFEFF\"ID do usuário\";\"Usuário\";\"Nome\";\"Sobrenome\";\"E-mail\";\"Situação\"\r\n"
                + "\"14128674-d423-4b67-91f5-dca790dc3940\";\"ana\";\"Ana São José\";\"\";\"ana@example.com\";\"Ativo\"\r\n"
                + "\"24128674-d423-4b67-91f5-dca790dc3940\";\"'=HYPERLINK\";\"'  +Ana\";\"Silva\";\"\";\"Inativo\"\r\n");
        assertThat(csv.getBytes(StandardCharsets.UTF_8)).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    void prestadoresEscapaAspasDelimitadorQuebraEConverteCadastroComMudancaDeDia() throws Exception {
        assertThat(gerar("prestadores")).isEqualTo("\uFEFF\"ID do prestador\";\"Estabelecimento\";\"Responsável\";\"Situação\";\"Data de cadastro\"\r\n"
                + "\"42\";\"Salão \"\"Ana\"\"; Centro\";\"\";\"Ativo\";\"31/10/2026 23:30:00 -03:00\"\r\n"
                + "\"43\";\"Outro\nSalão\";\"João\";\"Pendente\";\"01/11/2026 09:30:00 -03:00\"\r\n");
    }

    @Test
    void agendamentosPreservaDiasZeradosETotaisRecebidosSemMedia() throws Exception {
        assertThat(gerar("agendamentos")).isEqualTo("\uFEFF\"Data\";\"Total\";\"Pendentes\";\"Confirmados\";\"Cancelados\";\"Concluídos\"\r\n"
                + "\"01/11/2026\";\"1224\";\"1200\";\"20\";\"4\";\"0\"\r\n"
                + "\"02/11/2026\";\"0\";\"0\";\"0\";\"0\";\"0\"\r\n"
                + "\"03/11/2026\";\"10\";\"0\";\"0\";\"0\";\"10\"\r\n"
                + "\"TOTAL\";\"1234\";\"1200\";\"20\";\"4\";\"10\"\r\n");
    }

    @Test
    void periodoSemAgendamentosMantemDiasETotalZerados() throws Exception {
        var zeros = new br.edu.ifrn.wonder.worker.dto.AgendamentosSnapshot.Totais(0L, 0L, 0L, 0L);
        var inicio = java.time.LocalDate.of(2026, 11, 1);
        var fim = inicio.plusDays(1);
        var dados = new br.edu.ifrn.wonder.worker.dto.AgendamentosSnapshot(inicio, fim, 0L,
                java.math.BigDecimal.ZERO, zeros, java.util.List.of(
                        new br.edu.ifrn.wonder.worker.dto.AgendamentosSnapshot.Dia(inicio, 0L, zeros),
                        new br.edu.ifrn.wonder.worker.dto.AgendamentosSnapshot.Dia(fim, 0L, zeros)));
        var destino = new StringWriter();
        gerador.escrever(destino, ExportacaoSolicitada.Tipo.AGENDAMENTOS, dados);
        assertThat(destino.toString()).endsWith("\"01/11/2026\";\"0\";\"0\";\"0\";\"0\";\"0\"\r\n"
                + "\"02/11/2026\";\"0\";\"0\";\"0\";\"0\";\"0\"\r\n"
                + "\"TOTAL\";\"0\";\"0\";\"0\";\"0\";\"0\"\r\n");
    }

    @Test
    void totaisSaoOsRecebidosSemRecalcularOsDias() throws Exception {
        String json = new String(Contratos.corpo("agendamentos"), StandardCharsets.UTF_8)
                .replace("\"total_agendamentos\":1234", "\"total_agendamentos\":999");
        var pedido = contrato.ler(json.getBytes(StandardCharsets.UTF_8));
        var destino = new StringWriter();
        gerador.escrever(destino, pedido.tipo(), contrato.snapshot(pedido));
        assertThat(destino.toString()).endsWith("\"TOTAL\";\"999\";\"1200\";\"20\";\"4\";\"10\"\r\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK", "+nome", "-nome", "@nome", "\tnome", "\nnome", "\rnome", "   =nome", "  \tnome"})
    void protegeFormulasDeCamposExternos(String texto) throws Exception {
        var usuario = new UsuariosSnapshot.Usuario(java.util.UUID.randomUUID(), texto, null, null, null, true);
        var destino = new StringWriter();
        gerador.escrever(destino, ExportacaoSolicitada.Tipo.USUARIOS, new UsuariosSnapshot(java.util.List.of(usuario)));
        assertThat(destino.toString()).contains(";\"'" + texto + "\";");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rascunho", "pendente", "ativo", "rejeitado", "suspenso"})
    void traduzTodasAsSituacoes(String situacao) throws Exception {
        String json = new String(Contratos.corpo("prestadores"), StandardCharsets.UTF_8).replace("\"ativo\"", "\"" + situacao + "\"");
        var pedido = contrato.ler(json.getBytes(StandardCharsets.UTF_8));
        var destino = new StringWriter();
        gerador.escrever(destino, pedido.tipo(), contrato.snapshot(pedido));
        assertThat(destino.toString()).contains("\"" + Character.toUpperCase(situacao.charAt(0)) + situacao.substring(1) + "\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuarios", "prestadores"})
    void listaVaziaGeraSomenteCabecalho(String tipo) throws Exception {
        var conversor = JsonMapper.builder().build();
        var json = (tools.jackson.databind.node.ObjectNode) conversor.readTree(Contratos.corpo(tipo));
        ((tools.jackson.databind.node.ObjectNode) json.get("dados")).putArray(tipo);
        var pedido = contrato.ler(conversor.writeValueAsBytes(json));
        var destino = new StringWriter();
        gerador.escrever(destino, pedido.tipo(), contrato.snapshot(pedido));
        assertThat(destino.toString().split("\r\n")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuarios", "prestadores", "agendamentos"})
    void nomeControladoIdentificaTipoIdEPeriodo(String tipo) throws Exception {
        var pedido = contrato.ler(Contratos.corpo(tipo));
        String prefixo = tipo.equals("agendamentos") ? "agendamentos-2026-11-01-a-2026-11-03-" : tipo + "-";
        assertThat(gerador.nomeArquivo(pedido, contrato.snapshot(pedido))).isEqualTo(prefixo + pedido.exportacaoId() + ".csv");
    }
}
