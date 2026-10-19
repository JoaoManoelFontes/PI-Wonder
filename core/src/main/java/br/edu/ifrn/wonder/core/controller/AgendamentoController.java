package br.edu.ifrn.wonder.core.controller;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifrn.wonder.core.dto.DiasDisponiveisResponse;
import br.edu.ifrn.wonder.core.service.DisponibilidadeService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agendamentos")
public class AgendamentoController {

    private final DisponibilidadeService disponibilidadeService;

    @GetMapping("/dias-disponiveis")
    DiasDisponiveisResponse listarDiasDisponiveis(
            @RequestParam("prestador_id") Long prestadorId,
            @RequestParam("servico_id") Long servicoId,
            @RequestParam("mes") @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return disponibilidadeService.calcularDiasDisponiveis(prestadorId, servicoId, mes);
    }
}
