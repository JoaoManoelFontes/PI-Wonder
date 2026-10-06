package br.edu.ifrn.wonder.core.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prestadores")
public class Prestador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_usuario_id", nullable = false, unique = true)
    private PerfilUsuario perfilUsuario;

    @Column(name = "nome_estab", nullable = false)
    private String nomeEstab;

    @Column(length = 20)
    private String documento;

    private String endereco;

    @Column(length = 30)
    private String numero;

    @Column(length = 120)
    private String bairro;

    @Column(length = 120)
    private String cidade;

    @Column(length = 2)
    private String estado;

    private String complemento;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPrestador status = StatusPrestador.RASCUNHO;

    @Builder.Default
    @Column(nullable = false)
    private boolean ativo = false;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;

    @Column(name = "aprovado_em")
    private LocalDateTime aprovadoEm;

    @Column(name = "aprovado_por", length = 50)
    private String aprovadoPor;

    @Column(name = "motivo_rejeicao", columnDefinition = "TEXT")
    private String motivoRejeicao;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

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
}
