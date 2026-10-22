package br.edu.ifrn.wonder.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;

@Service
public class PrestadorFotoService {

    private static final long TAMANHO_MAXIMO_FOTO = 5 * 1024 * 1024;

    private final PrestadorRepository prestadorRepository;
    private final ArmazenamentoFotoService armazenamentoFotoService;
    private final String apiPublicUrl;

    public PrestadorFotoService(PrestadorRepository prestadorRepository,
            ArmazenamentoFotoService armazenamentoFotoService,
            @Value("${wonder.api.public-url}") String apiPublicUrl) {
        this.prestadorRepository = prestadorRepository;
        this.armazenamentoFotoService = armazenamentoFotoService;
        this.apiPublicUrl = apiPublicUrl.replaceAll("/+$", "");
    }

    @Transactional
    public Prestador atualizarFoto(Long prestadorId, UUID keycloakId, MultipartFile arquivo) {
        Prestador prestador = prestadorRepository.buscarPorIdComBloqueio(prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
        if (!prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para modificar este prestador");
        }
        if (prestador.getStatus() == StatusPrestador.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro pendente de análise não pode ser editado");
        }

        validarFoto(arquivo);
        armazenamentoFotoService.salvar(nomeObjeto(prestadorId), arquivo);
        prestador.setFotoUrl(apiPublicUrl + caminhoFoto(prestadorId));
        return prestador;
    }

    @Transactional(readOnly = true)
    public FotoPerfilConteudo buscarFoto(Long prestadorId, UUID keycloakId, boolean administrador) {
        Prestador prestador = prestadorRepository.findById(prestadorId)
                .orElseThrow(this::fotoNaoEncontrada);
        boolean publicado = prestador.isAtivo() && prestador.getStatus() == StatusPrestador.ATIVO;
        boolean dono = prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId);
        if (!publicado && !dono && !administrador) {
            throw fotoNaoEncontrada();
        }
        if (prestador.getFotoUrl() == null || !prestador.getFotoUrl().endsWith(caminhoFoto(prestadorId))) {
            throw fotoNaoEncontrada();
        }
        return armazenamentoFotoService.buscar(nomeObjeto(prestadorId));
    }

    private void validarFoto(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o arquivo da foto");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_FOTO) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "A foto deve ter no máximo 5 MB");
        }
        try (InputStream conteudo = arquivo.getInputStream()) {
            byte[] assinatura = conteudo.readNBytes(12);
            boolean valido = switch (arquivo.getContentType() == null ? "" : arquivo.getContentType()) {
                case "image/jpeg" -> comecaCom(assinatura, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
                case "image/png" -> comecaCom(assinatura, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10});
                case "image/webp" -> assinatura.length >= 12
                        && comecaCom(assinatura, new byte[]{'R', 'I', 'F', 'F'})
                        && Arrays.equals(Arrays.copyOfRange(assinatura, 8, 12), new byte[]{'W', 'E', 'B', 'P'});
                default -> false;
            };
            if (!valido) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A foto deve ter conteúdo e formato JPEG, PNG ou WebP compatíveis");
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler a foto", exception);
        }
    }

    private boolean comecaCom(byte[] conteudo, byte[] assinatura) {
        return conteudo.length >= assinatura.length
                && Arrays.equals(Arrays.copyOf(conteudo, assinatura.length), assinatura);
    }

    private String nomeObjeto(Long prestadorId) {
        return "prestadores/%s/foto".formatted(prestadorId);
    }

    private String caminhoFoto(Long prestadorId) {
        return "/catalogo/prestadores/%s/foto".formatted(prestadorId);
    }

    private ResponseStatusException fotoNaoEncontrada() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto do prestador não encontrada");
    }
}
