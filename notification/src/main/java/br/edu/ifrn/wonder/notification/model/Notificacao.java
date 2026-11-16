package br.edu.ifrn.wonder.notification.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notificacoes", uniqueConstraints = @UniqueConstraint(
        name = "uk_notificacoes_evento_usuario_tipo",
        columnNames = {"chave_evento", "usuario_keycloak_id", "tipo"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_keycloak_id", nullable = false)
    private UUID usuarioKeycloakId;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusNotificacao status = StatusNotificacao.PENDENTE;

    @Column(name = "chave_evento", nullable = false, length = 120)
    private String chaveEvento;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }
}
