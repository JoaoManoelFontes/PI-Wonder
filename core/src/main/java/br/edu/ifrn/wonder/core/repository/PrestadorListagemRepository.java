package br.edu.ifrn.wonder.core.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;

public interface PrestadorListagemRepository {
    Page<Prestador> buscarAdministrativos(String busca, List<UUID> responsaveis, StatusPrestador status,
            Instant de, Instant ate, boolean maisAntigos, int inicio, int limite);
}
