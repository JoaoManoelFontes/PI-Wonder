package br.edu.ifrn.wonder.core.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.domain.Categoria;
import br.edu.ifrn.wonder.core.domain.Prestador;
import br.edu.ifrn.wonder.core.domain.Servico;
import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.domain.StatusCategoria;
import br.edu.ifrn.wonder.core.domain.StatusPrestador;
import br.edu.ifrn.wonder.core.domain.StatusServico;
import br.edu.ifrn.wonder.core.dto.AtualizacaoServicoRequest;
import br.edu.ifrn.wonder.core.dto.CadastroServicoRequest;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import br.edu.ifrn.wonder.core.repository.CategoriaRepository;
import br.edu.ifrn.wonder.core.repository.PrestadorRepository;
import br.edu.ifrn.wonder.core.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ServicoService {

    private final ServicoRepository servicoRepository;
    private final PrestadorRepository prestadorRepository;
    private final CategoriaRepository categoriaRepository;
    private final AgendamentoRepository agendamentoRepository;

    @Transactional
    public Servico cadastrar(Long prestadorId, UUID keycloakId, CadastroServicoRequest dados) {
        Prestador prestador = buscarPrestadorEditavel(prestadorId, keycloakId);
        Categoria categoria = dados.categoriaId() == null ? null : buscarCategoriaAtiva(dados.categoriaId());
        return servicoRepository.save(Servico.builder()
                .prestador(prestador).categoria(categoria).nome(dados.nome())
                .preco(dados.preco()).duracaoMin(dados.duracaoMin()).status(StatusServico.ATIVO).build());
    }

    @Transactional
    public Servico atualizar(Long prestadorId, Long servicoId, UUID keycloakId, AtualizacaoServicoRequest dados) {
        buscarPrestadorEditavel(prestadorId, keycloakId);
        Servico servico = buscarServico(prestadorId, servicoId);

        if (dados.categoriaId() != null) {
            servico.setCategoria(buscarCategoriaAtiva(dados.categoriaId()));
        }
        if (dados.nome() != null) {
            servico.setNome(dados.nome());
        }
        if (dados.preco() != null) {
            servico.setPreco(dados.preco());
        }
        if (dados.duracaoMin() != null) {
            servico.setDuracaoMin(dados.duracaoMin());
        }
        return servico;
    }

    @Transactional
    public void inativar(Long prestadorId, Long servicoId, UUID keycloakId) {
        buscarPrestadorEditavel(prestadorId, keycloakId);
        Servico servico = buscarServico(prestadorId, servicoId);
        if (agendamentoRepository.existsByServicoIdAndStatusIn(servicoId,
                List.of(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Serviço possui agendamentos em aberto");
        }
        servico.setStatus(StatusServico.INATIVO);
    }

    private Prestador buscarPrestadorEditavel(Long prestadorId, UUID keycloakId) {
        Prestador prestador = prestadorRepository.findById(prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador não encontrado"));
        if (!prestador.getPerfilUsuario().getKeycloakId().equals(keycloakId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para modificar este prestador");
        }
        if (prestador.getStatus() == StatusPrestador.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro pendente de análise não pode ser editado");
        }
        return prestador;
    }

    private Servico buscarServico(Long prestadorId, Long servicoId) {
        return servicoRepository.findByIdAndPrestadorId(servicoId, prestadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
    }

    private Categoria buscarCategoriaAtiva(Long categoriaId) {
        return categoriaRepository.findByIdAndStatus(categoriaId, StatusCategoria.ATIVA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inexistente ou inativa"));
    }
}
