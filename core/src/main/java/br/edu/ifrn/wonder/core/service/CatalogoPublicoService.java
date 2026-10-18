package br.edu.ifrn.wonder.core.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogoPublicoService {

    private final CategoriaRepository categoriaRepository;
    private final PrestadorRepository prestadorRepository;
    private final ServicoRepository servicoRepository;
    private final HorarioAtendimentoRepository horarioAtendimentoRepository;

    public List<Categoria> listarCategoriasAtivas() {
        return categoriaRepository.findByStatusOrderByNomeAsc(StatusCategoria.ATIVA);
    }

    public List<Prestador> listarPrestadoresPublicos() {
        return listarPrestadoresPublicos(null, null);
    }

    public List<Prestador> listarPrestadoresPublicos(String nome, Long categoriaId) {
        String nomeBusca = nome == null || nome.isBlank() ? "" : nome.trim();
        return prestadorRepository.buscarPublicosComFiltros(
                nomeBusca, categoriaId, StatusPrestador.ATIVO, StatusCategoria.ATIVA, StatusServico.ATIVO);
    }

    public Categoria buscarCategoriaAtiva(Long categoriaId) {
        return categoriaRepository.findByIdAndStatus(categoriaId, StatusCategoria.ATIVA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
    }

    public Prestador buscarPrestadorPublico(Long prestadorId) {
        return prestadorRepository.findByIdAndAtivoTrueAndStatus(prestadorId, StatusPrestador.ATIVO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
    }

    public Servico buscarServicoAtivo(Long prestadorId, Long servicoId) {
        buscarPrestadorPublico(prestadorId);
        return servicoRepository.findByIdAndPrestadorIdAndStatus(servicoId, prestadorId, StatusServico.ATIVO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
    }

    public HorarioAtendimento buscarHorarioAtendimento(Long prestadorId, Long horarioId) {
        buscarPrestadorPublico(prestadorId);
        return horarioAtendimentoRepository.findByIdAndPrestadorId(horarioId, prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Horário de atendimento não encontrado"));
    }

    public List<HorarioAtendimento> listarHorariosDoPrestador(Long prestadorId) {
        buscarPrestadorPublico(prestadorId);
        return horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestadorId);
    }

    public List<Categoria> listarCategoriasDoPrestador(Long prestadorId) {
        buscarPrestadorPublico(prestadorId);
        return categoriaRepository.buscarPorPrestador(prestadorId, StatusCategoria.ATIVA, StatusServico.ATIVO);
    }
}
