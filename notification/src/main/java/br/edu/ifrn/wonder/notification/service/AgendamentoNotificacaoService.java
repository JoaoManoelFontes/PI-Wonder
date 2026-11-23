package br.edu.ifrn.wonder.notification.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifrn.wonder.notification.dto.AgendamentoNotificacaoEvent;
import br.edu.ifrn.wonder.notification.model.Notificacao;
import br.edu.ifrn.wonder.notification.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgendamentoNotificacaoService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"));

    private final NotificacaoRepository notificacaoRepository;

    @Transactional
    public void processar(AgendamentoNotificacaoEvent evento) {
        validar(evento);
        montar(evento).forEach(this::salvarSeAindaNaoExistir);
    }

    private List<DadosNotificacao> montar(AgendamentoNotificacaoEvent evento) {
        String detalhe = " de %s em %s".formatted(evento.nomeServico(), evento.nomeEstabelecimento());
        String horario = " para " + DATA_HORA.format(evento.inicio());
        String status = evento.statusNovo();
        String chaveBase = "agendamento:" + evento.agendamentoId() + ":";
        List<DadosNotificacao> notificacoes = new ArrayList<>();

        if (status == null || status.isBlank()) {
            notificacoes.add(new DadosNotificacao(evento.prestadorKeycloakId(), "novo_agendamento",
                    "Um cliente agendou um horário" + detalhe + horario + ".", chaveBase + "criado"));
            notificacoes.add(new DadosNotificacao(evento.clienteKeycloakId(), "novo_agendamento",
                    "Seu agendamento" + detalhe + " foi criado com sucesso" + horario + ".", chaveBase + "criado"));
            return notificacoes;
        }

        String motivo = evento.motivo() == null || evento.motivo().isBlank()
                ? "" : " Motivo: " + evento.motivo().strip();
        switch (status.toLowerCase(Locale.ROOT)) {
            case "confirmado" -> notificacoes.add(new DadosNotificacao(
                    evento.clienteKeycloakId(), "agendamento_confirmado",
                    "Seu agendamento" + detalhe + " foi confirmado pelo prestador.", chaveBase + "confirmado"));
            case "cancelado" -> {
                if ("cliente".equalsIgnoreCase(evento.alteradoPorTipo())) {
                    notificacoes.add(new DadosNotificacao(evento.prestadorKeycloakId(),
                            "agendamento_cancelado_cliente",
                            "Um cliente cancelou o agendamento" + detalhe + horario + "." + motivo,
                            chaveBase + "cancelado"));
                    notificacoes.add(new DadosNotificacao(evento.clienteKeycloakId(),
                            "agendamento_cancelado_cliente",
                            "Seu agendamento" + detalhe + " foi cancelado." + motivo,
                            chaveBase + "cancelado"));
                } else {
                    String responsavel = "administrador".equalsIgnoreCase(evento.alteradoPorTipo())
                            ? "pela administração" : "pelo prestador";
                    notificacoes.add(new DadosNotificacao(evento.clienteKeycloakId(),
                            "agendamento_cancelado_prestador",
                            "Seu agendamento" + detalhe + " foi cancelado " + responsavel + "." + motivo,
                            chaveBase + "cancelado"));
                }
            }
            case "concluido" -> notificacoes.add(new DadosNotificacao(
                    evento.clienteKeycloakId(), "agendamento_concluido",
                    "Seu atendimento" + detalhe + " foi concluído.", chaveBase + "concluido"));
            case "lembrete" -> {
                notificacoes.add(new DadosNotificacao(evento.clienteKeycloakId(), "lembrete_agendamento",
                        "Lembrete: seu agendamento" + detalhe + " acontecerá" + horario + ".",
                        chaveBase + "lembrete"));
                notificacoes.add(new DadosNotificacao(evento.prestadorKeycloakId(), "lembrete_agendamento",
                        "Lembrete: você possui um agendamento" + detalhe + horario + ".",
                        chaveBase + "lembrete"));
            }
            default -> throw new IllegalArgumentException("Status de agendamento não suportado para notificação");
        }
        return notificacoes;
    }

    private void salvarSeAindaNaoExistir(DadosNotificacao dados) {
        String chaveEvento = dados.chaveEvento;
        if (notificacaoRepository.existsByChaveEventoAndUsuarioKeycloakIdAndTipo(
                chaveEvento, dados.usuarioKeycloakId, dados.tipo)) {
            return;
        }
        notificacaoRepository.save(Notificacao.builder()
                .usuarioKeycloakId(dados.usuarioKeycloakId)
                .tipo(dados.tipo)
                .mensagem(dados.mensagem)
                .chaveEvento(chaveEvento)
                .build());
    }

    private void validar(AgendamentoNotificacaoEvent evento) {
        if (evento == null || evento.agendamentoId() == null || evento.agendamentoId() <= 0
                || evento.clienteKeycloakId() == null || evento.prestadorKeycloakId() == null
                || evento.nomeEstabelecimento() == null || evento.nomeEstabelecimento().isBlank()
                || evento.nomeServico() == null || evento.nomeServico().isBlank()
                || evento.inicio() == null) {
            throw new IllegalArgumentException("Evento de agendamento inválido");
        }
    }

    private record DadosNotificacao(
            UUID usuarioKeycloakId,
            String tipo,
            String mensagem,
            String chaveEvento) {
    }
}
