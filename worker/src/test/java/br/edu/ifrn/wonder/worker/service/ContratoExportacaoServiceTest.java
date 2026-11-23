package br.edu.ifrn.wonder.worker.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import br.edu.ifrn.wonder.worker.Contratos;

class ContratoExportacaoServiceTest {
    private final ContratoExportacaoService contrato = new ContratoExportacaoService();

    @ParameterizedTest
    @ValueSource(strings = {"usuarios", "prestadores", "agendamentos"})
    void leContratoDoCore(String tipo) throws Exception {
        var pedido = contrato.ler(Contratos.corpo(tipo));
        assertThat(pedido.tipo().name()).isEqualTo(tipo.toUpperCase(java.util.Locale.ROOT));
        assertThat(pedido.email()).isEqualTo("ana@example.com");
        assertThat(pedido.nome()).isEqualTo("Ana Silva");
    }

    @ParameterizedTest
    @ValueSource(strings = {"exportacaoId", "tipo", "email", "nome", "dados"})
    void rejeitaEnvelopeIncompleto(String campo) throws Exception {
        var conversor = tools.jackson.databind.json.JsonMapper.builder().build();
        var json = (tools.jackson.databind.node.ObjectNode) conversor.readTree(Contratos.corpo("usuarios"));
        json.remove(campo);
        assertThatThrownBy(() -> contrato.ler(conversor.writeValueAsBytes(json))).isInstanceOf(SolicitacaoInvalidaException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"usuarios\":null}", "{\"usuarios\":[null]}",
            "{\"usuarios\":[{\"id\":\"14128674-d423-4b67-91f5-dca790dc3940\"}]}", "{\"usuarios\":{}}"})
    void distingueListaAusenteDeVazia(String dados) throws Exception {
        String json = "{\"exportacaoId\":\"14128674-d423-4b67-91f5-dca790dc3940\",\"tipo\":\"USUARIOS\",\"email\":\"ana@example.com\",\"nome\":\"Ana\",\"dados\":" + dados + "}";
        assertThatThrownBy(() -> contrato.ler(json.getBytes(StandardCharsets.UTF_8))).isInstanceOf(SolicitacaoInvalidaException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prestadores", "agendamentos"})
    void rejeitaEstruturaEspecificaAusente(String tipo) throws Exception {
        var conversor = tools.jackson.databind.json.JsonMapper.builder().build();
        var json = (tools.jackson.databind.node.ObjectNode) conversor.readTree(Contratos.corpo(tipo));
        json.putObject("dados");
        assertThatThrownBy(() -> contrato.ler(conversor.writeValueAsBytes(json))).isInstanceOf(SolicitacaoInvalidaException.class);
    }
}
