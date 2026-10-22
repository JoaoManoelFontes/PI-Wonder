package br.edu.ifrn.wonder.core.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Agendamento;
import br.edu.ifrn.wonder.core.domain.Avaliacao;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.dto.CadastroAvaliacaoRequest;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.AvaliacaoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AvaliacaoService {

    private final AgendamentoRepository agendamentoRepository;
    private final AvaliacaoRepository avaliacaoRepository;

    @Transactional
    public Avaliacao cadastrar(Long prestadorId, UUID keycloakId, CadastroAvaliacaoRequest dados) {
        Agendamento agendamento = agendamentoRepository
                .buscarPorIdEPrestadorComBloqueio(dados.agendamentoId(), prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado para este prestador"));

        if (!agendamento.getCliente().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o cliente do agendamento pode avaliar");
        }
        if (agendamento.getStatus() != StatusAgendamento.CONCLUIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Somente agendamentos concluídos podem ser avaliados");
        }
        if (avaliacaoRepository.existsByAgendamentoId(agendamento.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agendamento já avaliado");
        }
        return avaliacaoRepository.save(Avaliacao.builder().agendamento(agendamento).nota(dados.nota()).build());
    }
}
