package br.edu.ifrn.wonder.core.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends BaseEntity {
    @Column(name = "keycloak_user_id", nullable = false, unique = true)
    private UUID keycloakUserId;

    @Column(name = "photo_url")
    @Setter
    private String photoUrl;

    @Column(name = "phone")
    @Setter
    private String phone;

    public Profile(UUID keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }
}
