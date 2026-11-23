package br.edu.ifrn.wonder.core.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifrn.wonder.core.dto.DashboardAgendamentosResponse;
import br.edu.ifrn.wonder.core.dto.DashboardIndicadoresResponse;
import br.edu.ifrn.wonder.core.dto.ExportacaoSolicitadaResponse;
import br.edu.ifrn.wonder.core.service.AdminDashboardService;
import br.edu.ifrn.wonder.core.service.ExportacaoService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final ExportacaoService exportacaoService;

    @GetMapping("/agendamentos")
    DashboardAgendamentosResponse consultarAgendamentos(
            @RequestParam(value = "data_inicio", required = false) String dataInicio,
            @RequestParam(value = "data_fim", required = false) String dataFim) {
        return adminDashboardService.consultarAgendamentos(converterData(dataInicio), converterData(dataFim));
    }

    @PostMapping("/agendamentos/exportacoes")
    ExportacaoSolicitadaResponse prepararExportacaoAgendamentos(
            @RequestParam(value = "data_inicio", required = false) String dataInicio,
            @RequestParam(value = "data_fim", required = false) String dataFim) {
        return exportacaoService.solicitarAgendamentos(converterData(dataInicio), converterData(dataFim));
    }

    private LocalDate converterData(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            if (!valor.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || valor.startsWith("0000-")) {
                throw new DateTimeParseException("Formato inválido", valor, 0);
            }
            return LocalDate.parse(valor);
        } catch (DateTimeParseException excecao) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe datas válidas no formato YYYY-MM-DD", excecao);
        }
    }

    @GetMapping("/indicadores")
    DashboardIndicadoresResponse consultarIndicadores() {
        return adminDashboardService.consultarIndicadores();
    }
}
