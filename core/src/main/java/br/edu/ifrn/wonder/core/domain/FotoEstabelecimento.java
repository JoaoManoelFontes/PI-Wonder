package br.edu.ifrn.wonder.core.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "fotos_estabelecimento")
public class FotoEstabelecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestador_id", nullable = false)
    private Prestador prestador;

    @Column(name = "nome_objeto", nullable = false, length = 500)
    private String nomeObjeto;

    @Column(name = "foto_url", nullable = false, length = 500)
    private String fotoUrl;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
