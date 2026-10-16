package br.edu.ifrn.wonder.core.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogoPublicoService {

    private final CategoriaRepository categoriaRepository;
    private final PrestadorRepository prestadorRepository;

    public List<Categoria> listarCategoriasAtivas() {
        return categoriaRepository.findByStatusOrderByNomeAsc(StatusCategoria.ATIVA);
    }

    public List<Prestador> listarPrestadoresPublicos() {
        return prestadorRepository.findByAtivoTrueAndStatusOrderByNomeEstabAsc(StatusPrestador.ATIVO);
    }

    public Categoria buscarCategoriaAtiva(Long categoriaId) {
        return categoriaRepository.findByIdAndStatus(categoriaId, StatusCategoria.ATIVA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
    }

    public Prestador buscarPrestadorPublico(Long prestadorId) {
        return prestadorRepository.findByIdAndAtivoTrueAndStatus(prestadorId, StatusPrestador.ATIVO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
    }


}
