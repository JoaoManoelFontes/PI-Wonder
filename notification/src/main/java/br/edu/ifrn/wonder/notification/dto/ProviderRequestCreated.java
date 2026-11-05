package br.edu.ifrn.wonder.notification.dto;

import java.util.List;

public record ProviderRequestCreated(List<String> destinatarios, DadosPrestador prestador) {

    public record DadosPrestador(Long id, String nomeEstab, String documento, String cidade, String estado) {
    }
}
