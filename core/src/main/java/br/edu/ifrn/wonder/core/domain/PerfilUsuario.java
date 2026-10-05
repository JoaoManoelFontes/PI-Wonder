package br.edu.ifrn.wonder.core.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "perfis_usuarios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerfilUsuario extends BaseEntity {
    @Column(name = "keycloak_id", nullable = false, unique = true)
    private UUID keycloakId;

    @Column(name = "foto_url")
    @Setter
    private String fotoUrl;

    @Column(name = "numero_telefone")
    @Setter
    private String numeroTelefone;

    public PerfilUsuario(UUID keycloakId) {
        this.keycloakId = keycloakId;
    }
}
