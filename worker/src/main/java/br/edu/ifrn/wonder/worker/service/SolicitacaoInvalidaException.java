package br.edu.ifrn.wonder.worker.service;

public class SolicitacaoInvalidaException extends RuntimeException {
    public SolicitacaoInvalidaException() {
        super("Envelope ou snapshot de exportação inválido");
    }
}
