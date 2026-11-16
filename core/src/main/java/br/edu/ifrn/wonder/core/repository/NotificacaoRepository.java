package br.edu.ifrn.wonder.core.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifrn.wonder.core.domain.Notificacao;
import br.edu.ifrn.wonder.core.domain.StatusNotificacao;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByUsuarioKeycloakIdOrderByCriadoEmDesc(UUID usuarioKeycloakId);

    List<Notificacao> findByUsuarioKeycloakIdAndStatusOrderByCriadoEmDesc(
            UUID usuarioKeycloakId,
            StatusNotificacao status);

    Optional<Notificacao> findByIdAndUsuarioKeycloakId(Long id, UUID usuarioKeycloakId);

    boolean existsByChaveEventoAndUsuarioKeycloakIdAndTipo(
            String chaveEvento,
            UUID usuarioKeycloakId,
            String tipo);
}
