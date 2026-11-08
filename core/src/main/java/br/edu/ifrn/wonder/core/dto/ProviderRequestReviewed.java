package br.edu.ifrn.wonder.core.dto;

/** Contrato JSON do resultado da análise; motivoRejeicao é obrigatório apenas na recusa. */
public record ProviderRequestReviewed(Long prestadorId, String destinatario, String nomeEstabelecimento,
        Resultado resultado, String motivoRejeicao) {

    public enum Resultado {
        APROVADO, REJEITADO
    }
}
