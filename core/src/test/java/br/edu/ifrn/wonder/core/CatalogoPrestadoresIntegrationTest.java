package br.edu.ifrn.wonder.core;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.PerfilUsuarioRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoPrestadoresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrestadorRepository prestadorRepository;

    @Autowired
    private PerfilUsuarioRepository perfilUsuarioRepository;

    @Test
    void deveListarSemLoginSomentePrestadoresAtivosEAprovadosOrdenadosComContratoPublico() throws Exception {
        inativarPrestadoresExistentes();
        Prestador ultimo = criarPrestador("Z Studio publico", StatusPrestador.ATIVO, true);
        Prestador primeiro = criarPrestador("A Studio publico", StatusPrestador.ATIVO, true);
        primeiro.setDocumento("00000000000191");
        primeiro.setEndereco("Rua das Flores");
        primeiro.setNumero("100");
        primeiro.setBairro("Centro");
        primeiro.setCidade("Natal");
        primeiro.setEstado("RN");
        primeiro.setComplemento("Sala 1");
        primeiro.setFotoUrl("https://cdn.wonder.dev/studio.jpg");

        for (StatusPrestador status : StatusPrestador.values()) {
            for (boolean ativo : new boolean[] {false, true}) {
                if (status == StatusPrestador.ATIVO && ativo) {
                    continue;
                }
                criarPrestador("Studio oculto " + status + ativo, status, ativo);
            }
        }
        prestadorRepository.flush();

        mockMvc.perform(get("/catalogo/prestadores"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]", aMapWithSize(13)))
                .andExpect(jsonPath("$[0].id").value(primeiro.getId().intValue()))
                .andExpect(jsonPath("$[0].perfilUsuarioId").value(primeiro.getPerfilUsuario().getId().toString()))
                .andExpect(jsonPath("$[0].nomeEstab").value("A Studio publico"))
                .andExpect(jsonPath("$[0].documento").value("00000000000191"))
                .andExpect(jsonPath("$[0].endereco").value("Rua das Flores"))
                .andExpect(jsonPath("$[0].numero").value("100"))
                .andExpect(jsonPath("$[0].bairro").value("Centro"))
                .andExpect(jsonPath("$[0].cidade").value("Natal"))
                .andExpect(jsonPath("$[0].estado").value("RN"))
                .andExpect(jsonPath("$[0].complemento").value("Sala 1"))
                .andExpect(jsonPath("$[0].status").value("ativo"))
                .andExpect(jsonPath("$[0].ativo").value(true))
                .andExpect(jsonPath("$[0].fotoUrl").value("https://cdn.wonder.dev/studio.jpg"))
                .andExpect(jsonPath("$[1].id").value(ultimo.getId().intValue()))
                .andExpect(jsonPath("$[1].nomeEstab").value("Z Studio publico"))
                .andExpect(jsonPath("$[1].status").value("ativo"))
                .andExpect(jsonPath("$[1].ativo").value(true))
                .andExpect(jsonPath("$[1].fotoUrl").isEmpty());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistemPrestadoresPublicaveis() throws Exception {
        inativarPrestadoresExistentes();

        mockMvc.perform(get("/catalogo/prestadores"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void deveManterAutenticacaoNasDemaisRotasEMetodos() throws Exception {
        mockMvc.perform(get("/perfis/eu"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/catalogo/prestadores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private void inativarPrestadoresExistentes() {
        prestadorRepository.findAll().forEach(prestador -> prestador.setAtivo(false));
        prestadorRepository.flush();
    }

    private Prestador criarPrestador(String nome, StatusPrestador status, boolean ativo) {
        PerfilUsuario perfil = perfilUsuarioRepository.save(new PerfilUsuario(UUID.randomUUID()));
        return prestadorRepository.save(Prestador.builder()
                .perfilUsuario(perfil).nomeEstab(nome).status(status).ativo(ativo).build());
    }
}
