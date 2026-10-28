package br.edu.ifrn.wonder.core.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.core.domain.Categoria;
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
}
