package br.edu.ifrn.wonder.core.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.PerfilUsuario;
import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.HorarioAtendimento;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.dto.CadastroPrestadorRequest;
import br.edu.ifrn.wonder.core.dto.AtualizacaoPrestadorRequest;
import br.edu.ifrn.wonder.core.dto.AtualizacaoStatusPrestadorRequest;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import br.edu.ifrn.wonder.core.repository.HorarioAtendimentoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrestadorService {

    private final PerfilUsuarioService perfilUsuarioService;
    private final PrestadorRepository prestadorRepository;
    private final CategoriaRepository categoriaRepository;
    private final ServicoRepository servicoRepository;
    private final HorarioAtendimentoRepository horarioAtendimentoRepository;
    private final AgendamentoRepository agendamentoRepository;

    @Transactional
    public Prestador cadastrarPrestador(UUID keycloakId, CadastroPrestadorRequest request) {
        PerfilUsuario perfilUsuario = perfilUsuarioService.buscarOuCriarPorKeycloakId(keycloakId);

        prestadorRepository.findByPerfilUsuarioId(perfilUsuario.getId())
                .ifPresent(prestador -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Usuário já possui cadastro de prestador");
                });

        Prestador prestador = Prestador.builder()
                .perfilUsuario(perfilUsuario)
                .nomeEstab(request.nomeEstab())
                .documento(request.documento())
                .endereco(request.endereco())
                .numero(request.numero())
                .bairro(request.bairro())
                .cidade(request.cidade())
                .estado(request.estado())
                .complemento(request.complemento())
                .fotoUrl(request.fotoUrl())
                .status(StatusPrestador.PENDENTE)
                .ativo(false)
                .enviadoEm(LocalDateTime.now())
                .build();

        return prestadorRepository.save(prestador);
    }

    @Transactional(readOnly = true)
    public Prestador buscarPrestadorDoUsuario(UUID keycloakId) {
        return prestadorRepository.findByPerfilUsuarioKeycloakId(keycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
    }

    @Transactional(readOnly = true)
    public List<Categoria> listarCategoriasDoUsuario(UUID keycloakId) {
        Prestador prestador = buscarPrestadorDoUsuario(keycloakId);
        return categoriaRepository.buscarPorPrestador(prestador.getId(), StatusCategoria.ATIVA, StatusServico.ATIVO);
    }

    @Transactional
    public Prestador atualizarPrestador(Long prestadorId, UUID keycloakId, AtualizacaoPrestadorRequest request) {
        Prestador prestador = buscarPrestadorParaAlteracao(prestadorId);

        if (!prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para modificar este prestador");
        }
        if (prestador.getStatus() == StatusPrestador.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro pendente de análise não pode ser editado");
        }

        if (request.nomeEstab() != null) {
            prestador.setNomeEstab(request.nomeEstab());
        }
        if (request.documento() != null) {
            prestador.setDocumento(request.documento());
        }
        if (request.endereco() != null) {
            prestador.setEndereco(request.endereco());
        }
        if (request.numero() != null) {
            prestador.setNumero(request.numero());
        }
        if (request.bairro() != null) {
            prestador.setBairro(request.bairro());
        }
        if (request.cidade() != null) {
            prestador.setCidade(request.cidade());
        }
        if (request.estado() != null) {
            prestador.setEstado(request.estado());
        }
        if (request.complemento() != null) {
            prestador.setComplemento(request.complemento());
        }

        return prestador;
    }

    @Transactional
    public Prestador enviarParaAprovacao(Long prestadorId, UUID keycloakId) {
        Prestador prestador = buscarPrestadorParaAlteracao(prestadorId);

        if (!prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para enviar este cadastro");
        }
        if (prestador.getStatus() != StatusPrestador.RASCUNHO && prestador.getStatus() != StatusPrestador.REJEITADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro não pode ser enviado neste status");
        }

        validarRequisitosDeEnvio(prestador);
        prestador.setStatus(StatusPrestador.PENDENTE);
        prestador.setAtivo(false);
        prestador.setEnviadoEm(LocalDateTime.now());
        prestador.setMotivoRejeicao(null);
        return prestador;
    }

    @Transactional
    public void inativar(Long prestadorId, UUID keycloakId) {
        Prestador prestador = buscarPrestadorParaAlteracao(prestadorId);

        if (!prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para modificar este prestador");
        }
        if (prestador.getStatus() == StatusPrestador.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro pendente de análise não pode ser editado");
        }
        if (agendamentoRepository.existsByPrestadorIdAndStatusIn(prestadorId,
                List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prestador possui agendamentos em aberto");
        }
        prestador.setAtivo(false);
    }

    private Prestador buscarPrestadorParaAlteracao(Long prestadorId) {
        return prestadorRepository.buscarPorIdComBloqueio(prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
    }

    private void validarRequisitosDeEnvio(Prestador prestador) {
        List<String> erros = new ArrayList<>();
        if (prestador.getNomeEstab() == null || prestador.getNomeEstab().isBlank()) {
            erros.add("Informe o nome do estabelecimento");
        }
        if (prestador.getDocumento() == null || prestador.getDocumento().isBlank()) {
            erros.add("Informe o documento do estabelecimento");
        }
        if (servicoRepository.findByPrestadorIdAndStatusOrderByNomeAsc(prestador.getId(), StatusServico.ATIVO).isEmpty()) {
            erros.add("Cadastre ao menos um serviço ativo");
        }
        if (categoriaRepository.buscarPorPrestador(prestador.getId(), StatusCategoria.ATIVA, StatusServico.ATIVO).isEmpty()) {
            erros.add("Vincule ao menos uma categoria ativa a um serviço ativo do estabelecimento");
        }
        if (horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestador.getId()).isEmpty()) {
            erros.add("Cadastre ao menos um horário de atendimento");
        }
        if (!erros.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", erros));
        }
    }

    @Transactional(readOnly = true)
    public List<Prestador> listarSolicitacoesPendentes() {
        return prestadorRepository.findByStatusOrderByEnviadoEmAsc(StatusPrestador.PENDENTE);
    }

    @Transactional(readOnly = true)
    public Prestador detalharPrestadorAdmin(Long id) {
        return prestadorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
    }

    @Transactional(readOnly = true)
    public List<Servico> listarServicosAdmin(Long prestadorId) {
        return servicoRepository.findByPrestadorIdOrderByNomeAsc(prestadorId);
    }

    @Transactional(readOnly = true)
    public List<HorarioAtendimento> listarHorariosAdmin(Long prestadorId) {
        return horarioAtendimentoRepository.findByPrestadorIdOrderByDiaSemanaAscHoraInicioAsc(prestadorId);
    }

    @Transactional
    public Prestador atualizarStatusAdmin(
            Long id,
            AtualizacaoStatusPrestadorRequest request,
            UUID adminId) {
        Prestador prestador = buscarPrestadorParaAlteracao(id);
        StatusPrestador novoStatus = converterStatusAdmin(request.status());
        validarTransicaoAdmin(prestador.getStatus(), novoStatus);

        if (novoStatus == StatusPrestador.ATIVO
                && prestador.getPerfilUsuario().getKeycloakId().equals(adminId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrador não pode aprovar o próprio cadastro");
        }

        switch (novoStatus) {
            case ATIVO -> ativarPorAdmin(prestador, adminId);
            case REJEITADO, RASCUNHO -> devolverPorAdmin(prestador, novoStatus, request.motivoRejeicao(), adminId);
            case SUSPENSO -> suspenderPorAdmin(prestador);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de prestador inválido");
        }
        return prestador;
    }

    private StatusPrestador converterStatusAdmin(String status) {
        return switch (status.trim()) {
            case "rascunho" -> StatusPrestador.RASCUNHO;
            case "ativo" -> StatusPrestador.ATIVO;
            case "rejeitado" -> StatusPrestador.REJEITADO;
            case "suspenso" -> StatusPrestador.SUSPENSO;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de prestador inválido");
        };
    }

    private void validarTransicaoAdmin(StatusPrestador atual, StatusPrestador novo) {
        boolean permitida = switch (atual) {
            case PENDENTE -> novo == StatusPrestador.ATIVO
                    || novo == StatusPrestador.REJEITADO
                    || novo == StatusPrestador.RASCUNHO;
            case ATIVO -> novo == StatusPrestador.SUSPENSO;
            case SUSPENSO -> novo == StatusPrestador.ATIVO;
            default -> false;
        };
        if (!permitida) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alteração de status não permitida");
        }
    }

    private void ativarPorAdmin(Prestador prestador, UUID adminId) {
        if (prestador.getStatus() == StatusPrestador.PENDENTE) {
            prestador.setAprovadoEm(LocalDateTime.now());
            prestador.setAprovadoPor(adminId.toString());
        }
        prestador.setStatus(StatusPrestador.ATIVO);
        prestador.setAtivo(true);
        prestador.setMotivoRejeicao(null);
    }

    private void devolverPorAdmin(
            Prestador prestador,
            StatusPrestador novoStatus,
            String motivo,
            UUID adminId) {
        if (motivo == null || motivo.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Motivo é obrigatório para rejeitar ou solicitar correção"
            );
        }
        prestador.setStatus(novoStatus);
        prestador.setAtivo(false);
        prestador.setAprovadoEm(null);
        prestador.setAprovadoPor(adminId.toString());
        prestador.setMotivoRejeicao(motivo.trim());
    }

    private void suspenderPorAdmin(Prestador prestador) {
        prestador.setStatus(StatusPrestador.SUSPENSO);
        prestador.setAtivo(false);
        prestador.setMotivoRejeicao(null);
    }
}
