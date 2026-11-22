package br.edu.ifrn.wonder.notification.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifrn.wonder.notification.model.Notificacao;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    boolean existsByChaveEventoAndUsuarioKeycloakIdAndTipo(
            String chaveEvento,
            UUID usuarioKeycloakId,
            String tipo);
}
