package br.edu.ifrn.wonder.core.repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PrestadorListagemRepositoryImpl implements PrestadorListagemRepository {
    private final EntityManager gerenciadorEntidades;

    @Override
    @Transactional(readOnly = true)
    public Page<Prestador> buscarAdministrativos(String busca, List<UUID> responsaveis, StatusPrestador status,
            Instant de, Instant ate, boolean maisAntigos, int inicio, int limite) {
        var filtros = montarFiltros(busca, responsaveis, status, de, ate);
        var contagem = gerenciadorEntidades.createQuery("SELECT COUNT(prestador) FROM Prestador prestador"
                + " JOIN prestador.perfilUsuario perfil" + filtros.clausula(), Long.class);
        filtros.parametros().forEach(contagem::setParameter);
        long total = contagem.getSingleResult();
        var prestadores = montarConsulta(filtros, maisAntigos)
                .setFirstResult(inicio).setMaxResults(limite).getResultList();
        return new PageImpl<>(prestadores, Pageable.unpaged(), total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Prestador> buscarAdministrativosParaExportacao(String busca, List<UUID> responsaveis,
            StatusPrestador status, Instant de, Instant ate, boolean maisAntigos) {
        return montarConsulta(montarFiltros(busca, responsaveis, status, de, ate), maisAntigos).getResultList();
    }

    private FiltrosAdministrativos montarFiltros(String busca, List<UUID> responsaveis, StatusPrestador status,
            Instant de, Instant ate) {
        StringBuilder filtros = new StringBuilder(" WHERE 1=1");
        Map<String, Object> parametros = new LinkedHashMap<>();
        if (!busca.isEmpty()) {
            filtros.append(" AND (LOWER(prestador.nomeEstab) LIKE :busca ESCAPE '!' ");
            parametros.put("busca", "%" + busca.toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
            if (!responsaveis.isEmpty()) {
                filtros.append(" OR perfil.keycloakId IN :responsaveis");
                parametros.put("responsaveis", responsaveis);
            }
            filtros.append(")");
        }
        if (status != null) {
            filtros.append(" AND prestador.status = :status");
            parametros.put("status", status);
        }
        if (de != null) {
            filtros.append(" AND prestador.criadoEm >= :de AND prestador.criadoEm < :ate");
            parametros.put("de", de);
            parametros.put("ate", ate);
        }
        return new FiltrosAdministrativos(filtros.toString(), parametros);
    }

    private jakarta.persistence.TypedQuery<Prestador> montarConsulta(
            FiltrosAdministrativos filtros, boolean maisAntigos) {
        String direcao = maisAntigos ? " ASC" : " DESC";
        var consulta = gerenciadorEntidades.createQuery("SELECT prestador FROM Prestador prestador"
                + " JOIN FETCH prestador.perfilUsuario perfil" + filtros.clausula()
                + " ORDER BY prestador.criadoEm" + direcao + ", prestador.id" + direcao, Prestador.class);
        filtros.parametros().forEach(consulta::setParameter);
        return consulta;
    }

    private record FiltrosAdministrativos(String clausula, Map<String, Object> parametros) {
    }
}
