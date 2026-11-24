package br.edu.ifrn.wonder.core.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.dto.CadastroHorarioAtendimentoRequest;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HorarioAtendimentoService {

    private final HorarioAtendimentoRepository horarioAtendimentoRepository;
    private final PrestadorRepository prestadorRepository;

    @Transactional
    public HorarioAtendimento cadastrar(Long prestadorId, UUID keycloakId, CadastroHorarioAtendimentoRequest dados) {
        Prestador prestador = buscarPrestadorEditavel(prestadorId, keycloakId);
        validarIntervalo(prestadorId, dados);
        return horarioAtendimentoRepository.save(HorarioAtendimento.builder().prestador(prestador)
                .diaSemana(dados.diaSemana()).horaInicio(dados.horaInicio()).horaFim(dados.horaFim()).build());
    }

    @Transactional
    public List<HorarioAtendimento> cadastrarLote(Long prestadorId, UUID keycloakId,
            List<CadastroHorarioAtendimentoRequest> dados) {
        Prestador prestador = buscarPrestadorEditavel(prestadorId, keycloakId);
        if (dados == null || dados.isEmpty() || dados.size() > 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe de 1 a 8 horários");
        }
        for (int indice = 0; indice < dados.size(); indice++) {
            CadastroHorarioAtendimentoRequest atual = dados.get(indice);
            if (atual == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horário inválido");
            }
            validarIntervalo(prestadorId, atual);
            for (int anterior = 0; anterior < indice; anterior++) {
                CadastroHorarioAtendimentoRequest outro = dados.get(anterior);
                if (atual.diaSemana().equals(outro.diaSemana())
                        && atual.horaInicio().isBefore(outro.horaFim())
                        && atual.horaFim().isAfter(outro.horaInicio())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Horários do lote se sobrepõem");
                }
            }
        }
        return horarioAtendimentoRepository.saveAll(dados.stream().map(item -> HorarioAtendimento.builder()
                .prestador(prestador).diaSemana(item.diaSemana())
                .horaInicio(item.horaInicio()).horaFim(item.horaFim()).build()).toList());
    }

    private void validarIntervalo(Long prestadorId, CadastroHorarioAtendimentoRequest dados) {
        if (dados.diaSemana() == null || dados.diaSemana() < 1 || dados.diaSemana() > 7
                || dados.horaInicio() == null || dados.horaFim() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe dia e horários válidos");
        }
        if (!dados.horaInicio().isBefore(dados.horaFim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O início deve ser anterior ao fim do atendimento");
        }
        if (dados.horaInicio().getNano() != 0 || dados.horaFim().getNano() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe horários sem frações de segundo");
        }
        if (horarioAtendimentoRepository.existsByPrestadorIdAndDiaSemanaAndHoraInicioLessThanAndHoraFimGreaterThan(
                prestadorId, dados.diaSemana(), dados.horaFim(), dados.horaInicio())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Intervalo sobreposto a um horário de atendimento existente");
        }
    }

    @Transactional
    public void remover(Long prestadorId, Long horarioId, UUID keycloakId) {
        buscarPrestadorEditavel(prestadorId, keycloakId);
        HorarioAtendimento horario = horarioAtendimentoRepository.findByIdAndPrestadorId(horarioId, prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Horário de atendimento não encontrado"));
        horarioAtendimentoRepository.delete(horario);
    }

    private Prestador buscarPrestadorEditavel(Long prestadorId, UUID keycloakId) {
        Prestador prestador = prestadorRepository.buscarPorIdComBloqueio(prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
        if (!prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para modificar este prestador");
        }
        if (prestador.isSolicitacaoEnviada()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro pendente de análise não pode ser editado");
        }
        return prestador;
    }
}
