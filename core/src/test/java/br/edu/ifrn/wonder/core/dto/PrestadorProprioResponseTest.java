package br.edu.ifrn.wonder.core.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.json.JsonMapper;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;

class PrestadorProprioResponseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void deveExporMotivoSomenteNoContratoDoProprioPrestador() throws Exception {
        Prestador prestador = Prestador.builder()
                .perfilUsuario(new PerfilUsuario(UUID.randomUUID()))
                .nomeEstab("Studio")
                .status(StatusPrestador.RASCUNHO)
                .motivoRejeicao("Corrija o documento")
                .build();

        String respostaPropria = jsonMapper.writeValueAsString(PrestadorProprioResponse.from(prestador));
        String respostaPublica = jsonMapper.writeValueAsString(PrestadorPublicResponse.from(prestador));

        assertThat(respostaPropria).contains("\"motivo_rejeicao\":\"Corrija o documento\"");
        assertThat(respostaPublica).doesNotContain("motivo_rejeicao", "Corrija o documento");
    }
}
