package br.edu.ifrn.wonder.core.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifrn.wonder.core.domain.FotoEstabelecimento;

public interface FotoEstabelecimentoRepository extends JpaRepository<FotoEstabelecimento, Long> {
    List<FotoEstabelecimento> findByPrestadorIdOrderByIdAsc(Long prestadorId);
    long countByPrestadorId(Long prestadorId);
    Optional<FotoEstabelecimento> findByIdAndPrestadorId(Long id, Long prestadorId);
}
