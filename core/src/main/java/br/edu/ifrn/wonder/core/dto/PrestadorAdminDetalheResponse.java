package br.edu.ifrn.wonder.core.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;

public record PrestadorAdminDetalheResponse(
        Long id,
        @JsonProperty("usuario_id") UUID usuarioId,
        @JsonProperty("nome_estab") String nomeEstab,
        String documento,
        String endereco,
        String numero,
        String bairro,
        String cidade,
        String estado,
        String complemento,
        String status,
        boolean ativo,
        @JsonProperty("foto_url") String fotoUrl,
        @JsonProperty("solicitante_foto_url") String solicitanteFotoUrl,
        @JsonProperty("enviado_em") LocalDateTime enviadoEm,
        @JsonProperty("aprovado_em") LocalDateTime aprovadoEm,
        @JsonProperty("aprovado_por") String aprovadoPor,
        @JsonProperty("motivo_rejeicao") String motivoRejeicao,
        List<ServicoAdminResponse> servicos,
        List<HorarioAdminResponse> horarios
) {

    public static PrestadorAdminDetalheResponse from(
            Prestador prestador,
            List<Servico> servicos,
            List<HorarioAtendimento> horarios) {
        return new PrestadorAdminDetalheResponse(
                prestador.getId(),
                prestador.getPerfilUsuario().getKeycloakId(),
                prestador.getNomeEstab(),
                prestador.getDocumento(),
                prestador.getEndereco(),
                prestador.getNumero(),
                prestador.getBairro(),
                prestador.getCidade(),
                prestador.getEstado(),
                prestador.getComplemento(),
                prestador.getStatus().name().toLowerCase(),
                prestador.isAtivo(),
                prestador.getFotoUrl(),
                prestador.getPerfilUsuario().getFotoUrl(),
                prestador.getEnviadoEm(),
                prestador.getAprovadoEm(),
                prestador.getAprovadoPor(),
                prestador.getMotivoRejeicao(),
                servicos.stream().map(servico -> ServicoAdminResponse.from(servico, prestador.getId())).toList(),
                horarios.stream().map(horario -> HorarioAdminResponse.from(horario, prestador.getId())).toList()
        );
    }

    public record ServicoAdminResponse(
            Long id,
            @JsonProperty("prestador_id") Long prestadorId,
            String nome,
            BigDecimal preco,
            @JsonProperty("duracao_min") Integer duracaoMin,
            @JsonProperty("categoria_id") Long categoriaId,
            @JsonProperty("foto_url") String fotoUrl,
            String status
    ) {
        static ServicoAdminResponse from(Servico servico, Long prestadorId) {
            return new ServicoAdminResponse(
                    servico.getId(),
                    prestadorId,
                    servico.getNome(),
                    servico.getPreco(),
                    servico.getDuracaoMin(),
                    servico.getCategoria() == null ? null : servico.getCategoria().getId(),
                    servico.getFotoUrl(),
                    servico.getStatus().name().toLowerCase()
            );
        }
    }

    public record HorarioAdminResponse(
            Long id,
            @JsonProperty("prestador_id") Long prestadorId,
            @JsonProperty("dia_semana") Integer diaSemana,
            @JsonProperty("hora_inicio") LocalTime horaInicio,
            @JsonProperty("hora_fim") LocalTime horaFim
    ) {
        static HorarioAdminResponse from(HorarioAtendimento horario, Long prestadorId) {
            return new HorarioAdminResponse(
                    horario.getId(),
                    prestadorId,
                    horario.getDiaSemana(),
                    horario.getHoraInicio(),
                    horario.getHoraFim()
            );
        }
    }
}
