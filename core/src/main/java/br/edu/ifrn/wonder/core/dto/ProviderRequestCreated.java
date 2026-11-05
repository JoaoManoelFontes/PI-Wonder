package br.edu.ifrn.wonder.core.dto;

import java.util.List;

/** Contrato JSON do evento provider_request_created consumido pelo notification. */
public record ProviderRequestCreated(List<String> destinatarios, DadosPrestador prestador) {

    public record DadosPrestador(Long id, String nomeEstab, String documento, String cidade, String estado) {
    }
}
