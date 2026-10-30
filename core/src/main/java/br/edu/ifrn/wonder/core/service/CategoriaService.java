package br.edu.ifrn.wonder.core.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.dto.AtualizacaoCategoriaRequest;
import br.edu.ifrn.wonder.core.dto.AtualizacaoStatusCategoriaRequest;
import br.edu.ifrn.wonder.core.dto.CadastroCategoriaRequest;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<Categoria> listarCategorias() {
        return categoriaRepository.findAllByOrderByNomeAsc();
    }

    @Transactional
    public Categoria cadastrarCategoria(CadastroCategoriaRequest request) {
        String nome = request.nome().trim();
        if (categoriaRepository.existsByNomeIgnoreCase(nome)) {
            throw categoriaJaCadastrada();
        }

        Categoria categoria = Categoria.builder()
                .nome(nome)
                .descricao(normalizarDescricao(request.descricao()))
                .build();
        try {
            return categoriaRepository.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException exception) {
            throw categoriaJaCadastrada(exception);
        }
    }

    @Transactional
    public Categoria atualizarCategoria(Long categoriaId, AtualizacaoCategoriaRequest request) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
        String nome = request.nome().trim();
        if (categoriaRepository.existsByNomeIgnoreCaseAndIdNot(nome, categoriaId)) {
            throw categoriaJaCadastrada();
        }

        categoria.setNome(nome);
        categoria.setDescricao(normalizarDescricao(request.descricao()));
        try {
            return categoriaRepository.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException exception) {
            throw categoriaJaCadastrada(exception);
        }
    }

    @Transactional
    public Categoria atualizarStatus(Long categoriaId, AtualizacaoStatusCategoriaRequest request) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
        categoria.setStatus(converterStatus(request.status()));
        return categoriaRepository.saveAndFlush(categoria);
    }

    private StatusCategoria converterStatus(String status) {
        return switch (status.trim()) {
            case "ativa" -> StatusCategoria.ATIVA;
            case "inativa" -> StatusCategoria.INATIVA;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de categoria inválido");
        };
    }

    private String normalizarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return null;
        }
        return descricao.trim();
    }

    private ResponseStatusException categoriaJaCadastrada() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Categoria já cadastrada");
    }

    private ResponseStatusException categoriaJaCadastrada(DataIntegrityViolationException exception) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Categoria já cadastrada", exception);
    }
}
