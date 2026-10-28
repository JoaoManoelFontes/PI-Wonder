package br.edu.ifrn.wonder.core.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Categoria;
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
