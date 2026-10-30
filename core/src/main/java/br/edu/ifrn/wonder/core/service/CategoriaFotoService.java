package br.edu.ifrn.wonder.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.dto.FotoPerfilConteudo;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;

@Service
public class CategoriaFotoService {

    private final CategoriaRepository categoriaRepository;
    private final ArmazenamentoFotoService armazenamentoFotoService;
    private final String apiPublicUrl;

    public CategoriaFotoService(CategoriaRepository categoriaRepository,
            ArmazenamentoFotoService armazenamentoFotoService,
            @Value("${wonder.api.public-url}") String apiPublicUrl) {
        this.categoriaRepository = categoriaRepository;
        this.armazenamentoFotoService = armazenamentoFotoService;
        this.apiPublicUrl = apiPublicUrl.replaceAll("/+$", "");
    }

    @Transactional
    public Categoria atualizarFoto(Long categoriaId, MultipartFile arquivo) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));

        ValidadorFoto.validar(arquivo);
        armazenamentoFotoService.salvar(nomeObjeto(categoriaId), arquivo);
        categoria.setFotoUrl(apiPublicUrl + caminhoFoto(categoriaId));
        return categoriaRepository.saveAndFlush(categoria);
    }

    @Transactional(readOnly = true)
    public FotoPerfilConteudo buscarFoto(Long categoriaId) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(this::fotoNaoEncontrada);
        if (categoria.getFotoUrl() == null || !categoria.getFotoUrl().endsWith(caminhoFoto(categoriaId))) {
            throw fotoNaoEncontrada();
        }
        return armazenamentoFotoService.buscar(nomeObjeto(categoriaId));
    }

    private String nomeObjeto(Long categoriaId) {
        return "categorias/%s/foto".formatted(categoriaId);
    }

    private String caminhoFoto(Long categoriaId) {
        return "/catalogo/categorias/%s/foto".formatted(categoriaId);
    }

    private ResponseStatusException fotoNaoEncontrada() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto da categoria não encontrada");
    }
}
