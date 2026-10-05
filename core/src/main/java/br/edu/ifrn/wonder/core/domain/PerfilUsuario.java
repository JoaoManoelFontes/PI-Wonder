package br.edu.ifrn.wonder.core.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "perfis_usuarios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerfilUsuario{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true)
    private UUID keycloakId;

    @Column(name = "foto_url")
    @Setter
    private String fotoUrl;

    @Column(name = "numero_telefone")
    @Setter
    private String numeroTelefone;


    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    protected void onCreate() {
        Instant agora = Instant.now();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = Instant.now();
    }

    public PerfilUsuario(UUID keycloakId) {
        this.keycloakId = keycloakId;
    }
}
