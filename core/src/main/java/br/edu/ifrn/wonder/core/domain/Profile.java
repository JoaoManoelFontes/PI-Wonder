package br.edu.ifrn.wonder.core.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "profiles")
public class Profile extends BaseEntity{
    @Column(name = "keycloak_user_id", nullable = false, unique = true)
    private UUID keycloakUserId;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "phone")
    private String phone;
}
