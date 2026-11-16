package br.edu.ifrn.wonder.core.repository;

import java.time.LocalDate;

public interface AgendamentoContagemDiaria {

    LocalDate getData();

    String getStatus();

    long getQuantidade();
}
