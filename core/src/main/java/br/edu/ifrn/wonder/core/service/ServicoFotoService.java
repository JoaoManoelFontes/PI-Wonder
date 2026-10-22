package br.edu.ifrn.wonder.core.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;

@Service
public class ServicoFotoService {

    private final PrestadorRepository prestadorRepository;
    private final ServicoRepository servicoRepository;
    private final ArmazenamentoFotoService armazenamentoFotoService;
    private final String apiPublicUrl;

    public ServicoFotoService(PrestadorRepository prestadorRepository, ServicoRepository servicoRepository,
            ArmazenamentoFotoService armazenamentoFotoService,
            @Value("${wonder.api.public-url}") String apiPublicUrl) {
        this.prestadorRepository = prestadorRepository;
        this.servicoRepository = servicoRepository;
        this.armazenamentoFotoService = armazenamentoFotoService;
        this.apiPublicUrl = apiPublicUrl.replaceAll("/+$", "");
    }

    @Transactional
    public Servico atualizarFoto(Long prestadorId, Long servicoId, UUID keycloakId, MultipartFile arquivo) {
        Prestador prestador = prestadorRepository.buscarPorIdComBloqueio(prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
        if (!prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para modificar este prestador");
        }
        if (prestador.getStatus() == StatusPrestador.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro pendente de análise não pode ser editado");
        }
        Servico servico = servicoRepository.findByIdAndPrestadorId(servicoId, prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
        ValidadorFoto.validar(arquivo);
        armazenamentoFotoService.salvar(nomeObjeto(prestadorId, servicoId), arquivo);
        servico.setFotoUrl(apiPublicUrl + caminhoFoto(prestadorId, servicoId));
        return servico;
    }

    @Transactional(readOnly = true)
    public FotoPerfilConteudo buscarFoto(Long prestadorId, Long servicoId, UUID keycloakId, boolean administrador) {
        Prestador prestador = prestadorRepository.findById(prestadorId).orElseThrow(this::fotoNaoEncontrada);
        boolean dono = prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId);
        boolean prestadorPublicado = prestador.isAtivo() && prestador.getStatus() == StatusPrestador.ATIVO;
        if (!prestadorPublicado && !dono && !administrador) {
            throw fotoNaoEncontrada();
        }
        Servico servico = servicoRepository.findByIdAndPrestadorId(servicoId, prestadorId)
                .orElseThrow(this::fotoNaoEncontrada);
        if (servico.getStatus() != StatusServico.ATIVO && !dono && !administrador) {
            throw fotoNaoEncontrada();
        }
        if (servico.getFotoUrl() == null || !servico.getFotoUrl().endsWith(caminhoFoto(prestadorId, servicoId))) {
            throw fotoNaoEncontrada();
        }
        return armazenamentoFotoService.buscar(nomeObjeto(prestadorId, servicoId));
    }

    private String nomeObjeto(Long prestadorId, Long servicoId) {
        return "prestadores/%s/servicos/%s/foto".formatted(prestadorId, servicoId);
    }

    private String caminhoFoto(Long prestadorId, Long servicoId) {
        return "/catalogo/prestadores/%s/servicos/%s/foto".formatted(prestadorId, servicoId);
    }

    private ResponseStatusException fotoNaoEncontrada() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto do serviço não encontrada");
    }
}
