package br.edu.ifrn.wonder.core.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Notificacao;
import br.edu.ifrn.wonder.core.domain.StatusNotificacao;
import br.edu.ifrn.wonder.core.dto.NotificacoesPaginaResponse;
import br.edu.ifrn.wonder.core.dto.NotificacaoResponse;
import br.edu.ifrn.wonder.core.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;

    @Transactional(readOnly = true)
    public List<Notificacao> listar(UUID usuarioKeycloakId, String status) {
        if (status == null || status.isBlank()) {
            return notificacaoRepository.findByUsuarioKeycloakIdOrderByCriadoEmDesc(usuarioKeycloakId);
        }
        StatusNotificacao statusNotificacao = interpretarStatus(status);
        return notificacaoRepository.findByUsuarioKeycloakIdAndStatusOrderByCriadoEmDesc(
                usuarioKeycloakId, statusNotificacao);
    }

    @Transactional
    public Notificacao marcarComoLida(Long id, UUID usuarioKeycloakId) {
        Notificacao notificacao = notificacaoRepository.findByIdAndUsuarioKeycloakId(id, usuarioKeycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notificação não encontrada"));
        notificacao.setStatus(StatusNotificacao.LIDA);
        return notificacao;
    }

    @Transactional(readOnly = true)
    public NotificacoesPaginaResponse listarPagina(UUID usuarioKeycloakId, String status, int pagina, int limite) {
        if (pagina < 0 || limite < 1 || limite > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação inválida");
        }
        var pageable = PageRequest.of(pagina, limite, Sort.by(Sort.Direction.DESC, "criadoEm"));
        var resultado = status == null || status.isBlank()
                ? notificacaoRepository.findByUsuarioKeycloakIdOrderByCriadoEmDesc(usuarioKeycloakId, pageable)
                : notificacaoRepository.findByUsuarioKeycloakIdAndStatusOrderByCriadoEmDesc(
                        usuarioKeycloakId, interpretarStatus(status), pageable);
        return new NotificacoesPaginaResponse(resultado.getContent().stream()
                .map(NotificacaoResponse::from).toList(), pagina, limite,
                resultado.getTotalElements(), resultado.hasNext());
    }

    @Transactional
    public void marcarTodasComoLidas(UUID usuarioKeycloakId) {
        notificacaoRepository.marcarTodasComoLidas(usuarioKeycloakId);
    }

    private StatusNotificacao interpretarStatus(String status) {
        try {
            return StatusNotificacao.valueOf(status.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de notificação inválido");
        }
    }
}
