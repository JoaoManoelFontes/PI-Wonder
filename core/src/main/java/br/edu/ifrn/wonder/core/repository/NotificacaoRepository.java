package br.edu.ifrn.wonder.core.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.ifrn.wonder.core.domain.Notificacao;
import br.edu.ifrn.wonder.core.domain.StatusNotificacao;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByUsuarioKeycloakIdOrderByCriadoEmDesc(UUID usuarioKeycloakId);

    List<Notificacao> findByUsuarioKeycloakIdAndStatusOrderByCriadoEmDesc(
            UUID usuarioKeycloakId,
            StatusNotificacao status);

    Page<Notificacao> findByUsuarioKeycloakIdOrderByCriadoEmDesc(UUID usuarioKeycloakId, Pageable pageable);

    Page<Notificacao> findByUsuarioKeycloakIdAndStatusOrderByCriadoEmDesc(
            UUID usuarioKeycloakId, StatusNotificacao status, Pageable pageable);

    Optional<Notificacao> findByIdAndUsuarioKeycloakId(Long id, UUID usuarioKeycloakId);

    boolean existsByChaveEventoAndUsuarioKeycloakIdAndTipo(
            String chaveEvento,
            UUID usuarioKeycloakId,
            String tipo);

    @Modifying
    @Query("update Notificacao n set n.status = br.edu.ifrn.wonder.core.domain.StatusNotificacao.LIDA "
            + "where n.usuarioKeycloakId = :usuarioKeycloakId "
            + "and n.status = br.edu.ifrn.wonder.core.domain.StatusNotificacao.PENDENTE")
    int marcarTodasComoLidas(@Param("usuarioKeycloakId") UUID usuarioKeycloakId);
}
