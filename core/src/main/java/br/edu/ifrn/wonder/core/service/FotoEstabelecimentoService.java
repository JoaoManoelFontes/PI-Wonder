package br.edu.ifrn.wonder.core.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.FotoEstabelecimento;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.dto.FotoEstabelecimentoResponse;
import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import br.edu.ifrn.wonder.core.repository.FotoEstabelecimentoRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;

@Service
public class FotoEstabelecimentoService {

    private static final int LIMITE_FOTOS = 8;
    private final FotoEstabelecimentoRepository fotoRepository;
    private final PrestadorRepository prestadorRepository;
    private final ArmazenamentoFotoService armazenamento;
    private final String apiPublicUrl;

    public FotoEstabelecimentoService(FotoEstabelecimentoRepository fotoRepository,
            PrestadorRepository prestadorRepository, ArmazenamentoFotoService armazenamento,
            @Value("${wonder.api.public-url}") String apiPublicUrl) {
        this.fotoRepository = fotoRepository;
        this.prestadorRepository = prestadorRepository;
        this.armazenamento = armazenamento;
        this.apiPublicUrl = apiPublicUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public List<FotoEstabelecimentoResponse> listar(Long prestadorId, UUID keycloakId, boolean administrador) {
        buscarPrestadorVisivel(prestadorId, keycloakId, administrador);
        List<FotoEstabelecimento> fotos = fotoRepository.findByPrestadorIdOrderByIdAsc(prestadorId);
        return java.util.stream.IntStream.range(0, fotos.size())
                .mapToObj(indice -> FotoEstabelecimentoResponse.from(fotos.get(indice), indice + 1))
                .toList();
    }

    @Transactional
    public FotoEstabelecimentoResponse adicionar(Long prestadorId, UUID keycloakId, MultipartFile arquivo) {
        Prestador prestador = buscarPrestadorEditavel(prestadorId, keycloakId);
        if (fotoRepository.countByPrestadorId(prestadorId) >= LIMITE_FOTOS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A galeria aceita no máximo 8 fotos");
        }
        ValidadorFoto.validar(arquivo);
        String nomeObjeto = "prestadores/%d/galeria/%s".formatted(prestadorId, UUID.randomUUID());
        armazenamento.salvar(nomeObjeto, arquivo);

        FotoEstabelecimento foto = new FotoEstabelecimento();
        foto.setPrestador(prestador);
        foto.setNomeObjeto(nomeObjeto);
        foto.setCriadoEm(Instant.now());
        foto.setFotoUrl(apiPublicUrl + "/catalogo/prestadores/%d/fotos/".formatted(prestadorId));
        foto = fotoRepository.save(foto);
        foto.setFotoUrl(foto.getFotoUrl() + foto.getId());
        return FotoEstabelecimentoResponse.from(fotoRepository.save(foto),
                (int) fotoRepository.countByPrestadorId(prestadorId));
    }

    @Transactional
    public void remover(Long prestadorId, Long fotoId, UUID keycloakId) {
        buscarPrestadorEditavel(prestadorId, keycloakId);
        FotoEstabelecimento foto = fotoRepository.findByIdAndPrestadorId(fotoId, prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto não encontrada"));
        armazenamento.remover(foto.getNomeObjeto());
        fotoRepository.delete(foto);
    }

    @Transactional(readOnly = true)
    public FotoPerfilConteudo buscarFoto(Long prestadorId, Long fotoId, UUID keycloakId, boolean administrador) {
        buscarPrestadorVisivel(prestadorId, keycloakId, administrador);
        FotoEstabelecimento foto = fotoRepository.findByIdAndPrestadorId(fotoId, prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto não encontrada"));
        return armazenamento.buscar(foto.getNomeObjeto());
    }

    private Prestador buscarPrestadorEditavel(Long prestadorId, UUID keycloakId) {
        Prestador prestador = prestadorRepository.buscarPorIdComBloqueio(prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
        if (!prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para modificar este prestador");
        }
        if (prestador.isSolicitacaoEnviada()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro pendente de análise não pode ser editado");
        }
        return prestador;
    }

    private Prestador buscarPrestadorVisivel(Long prestadorId, UUID keycloakId, boolean administrador) {
        Prestador prestador = prestadorRepository.findById(prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
        boolean publicado = prestador.isAtivo() && prestador.getStatus() == StatusPrestador.ATIVO;
        boolean dono = prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId);
        if (!publicado && !dono && !administrador) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado");
        }
        return prestador;
    }
}
