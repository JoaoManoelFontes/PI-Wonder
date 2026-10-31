package br.edu.ifrn.wonder.core.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.AgendamentoResponse;
import br.edu.ifrn.wonder.core.dto.CadastroAgendamentoRequest;
import br.edu.ifrn.wonder.core.dto.DisponibilidadeResponse;
import br.edu.ifrn.wonder.core.dto.DiasDisponiveisResponse;
import br.edu.ifrn.wonder.core.security.UsuarioAutenticadoProvider;
import br.edu.ifrn.wonder.core.service.AgendamentoService;
import br.edu.ifrn.wonder.core.service.DisponibilidadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agendamentos")
public class AgendamentoController {

    private final DisponibilidadeService disponibilidadeService;
    private final AgendamentoService agendamentoService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @Value("${wonder.api.public-url}")
    private String apiPublicUrl;

    @GetMapping
    List<AgendamentoResponse> listar(
            @RequestParam(value = "data", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            Authentication autenticacao) {
        boolean administrador = possuiPapel(autenticacao, "ROLE_ADMIN");
        boolean prestador = possuiPapel(autenticacao, "ROLE_PROVIDER");

        return agendamentoService.listar(
                        usuarioAutenticadoProvider.getUuidUsuario(),
                        administrador,
                        prestador,
                        data
                ).stream()
                .map(agendamento -> AgendamentoResponse.from(agendamento, apiPublicUrl))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AgendamentoResponse criar(@Valid @RequestBody CadastroAgendamentoRequest request) {
        return AgendamentoResponse.from(
                agendamentoService.criar(usuarioAutenticadoProvider.getUuidUsuario(), request),
                apiPublicUrl
        );
    }

    @GetMapping("/dias-disponiveis")
    DiasDisponiveisResponse listarDiasDisponiveis(
            @RequestParam("prestador_id") Long prestadorId,
            @RequestParam("servico_id") Long servicoId,
            @RequestParam("mes") @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return disponibilidadeService.calcularDiasDisponiveis(prestadorId, servicoId, mes);
    }

    @GetMapping("/disponibilidade")
    DisponibilidadeResponse listarDisponibilidade(
            @RequestParam("prestador_id") Long prestadorId,
            @RequestParam("servico_id") Long servicoId,
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return disponibilidadeService.calcular(prestadorId, servicoId, data);
    }

    private boolean possuiPapel(Authentication autenticacao, String papel) {
        return autenticacao.getAuthorities().stream()
                .anyMatch(autoridade -> autoridade.getAuthority().equals(papel));
    }
}
