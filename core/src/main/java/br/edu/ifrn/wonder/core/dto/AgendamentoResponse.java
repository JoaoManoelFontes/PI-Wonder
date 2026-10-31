package br.edu.ifrn.wonder.core.dto;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.domain.Agendamento;

public record AgendamentoResponse(
        Long id,
        @JsonProperty("cliente_id") UUID clienteId,
        @JsonProperty("prestador_id") Long prestadorId,
        @JsonProperty("servico_id") Long servicoId,
        LocalDateTime inicio,
        String status,
        @JsonProperty("cliente_nome") String clienteNome,
        @JsonProperty("cliente_foto_url") String clienteFotoUrl
) {

    public static AgendamentoResponse from(Agendamento agendamento, String apiPublicUrl) {
        return new AgendamentoResponse(
                agendamento.getId(),
                agendamento.getCliente().getId(),
                agendamento.getPrestador().getId(),
                agendamento.getServico().getId(),
                agendamento.getInicio(),
                agendamento.getStatus().name().toLowerCase(Locale.ROOT),
                null,
                montarFotoClienteUrl(agendamento, apiPublicUrl)
        );
    }

    private static String montarFotoClienteUrl(Agendamento agendamento, String apiPublicUrl) {
        if (agendamento.getCliente().getFotoUrl() == null) {
            return null;
        }

        String baseUrl = apiPublicUrl.endsWith("/")
                ? apiPublicUrl.substring(0, apiPublicUrl.length() - 1)
                : apiPublicUrl;

        return "%s/perfis/%s/foto".formatted(baseUrl, agendamento.getCliente().getId());
    }
}
