package br.edu.ifrn.wonder.worker.service;

import java.io.IOException;
import java.io.Writer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import br.edu.ifrn.wonder.worker.dto.*;

@Service
public class GeradorCsvService {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter CADASTRO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    public String nomeArquivo(ExportacaoSolicitada pedido, Object snapshot) {
        return switch (pedido.tipo()) {
            case USUARIOS -> "usuarios-" + pedido.exportacaoId() + ".csv";
            case PRESTADORES -> "prestadores-" + pedido.exportacaoId() + ".csv";
            case AGENDAMENTOS -> {
                var dados = (AgendamentosSnapshot) snapshot;
                yield "agendamentos-" + dados.dataInicio() + "-a-" + dados.dataFim() + "-" + pedido.exportacaoId() + ".csv";
            }
        };
    }

    public void escrever(Writer destino, ExportacaoSolicitada.Tipo tipo, Object snapshot) throws IOException {
        destino.write('\uFEFF');
        switch (tipo) {
            case USUARIOS -> {
                linha(destino, "ID do usuário", "Usuário", "Nome", "Sobrenome", "E-mail", "Situação");
                for (var usuario : ((UsuariosSnapshot) snapshot).usuarios()) {
                    linha(destino, usuario.id(), usuario.username(), usuario.nome(), usuario.sobrenome(),
                            usuario.email(), usuario.ativo() ? "Ativo" : "Inativo");
                }
            }
            case PRESTADORES -> {
                linha(destino, "ID do prestador", "Estabelecimento", "Responsável", "Situação", "Data de cadastro");
                for (var prestador : ((PrestadoresSnapshot) snapshot).prestadores()) {
                    linha(destino, prestador.id(), prestador.nomeEstab(), prestador.responsavelNome(),
                            prestador.status().descricao(), CADASTRO.format(prestador.criadoEm()));
                }
            }
            case AGENDAMENTOS -> {
                linha(destino, "Data", "Total", "Pendentes", "Confirmados", "Cancelados", "Concluídos");
                var dados = (AgendamentosSnapshot) snapshot;
                for (var dia : dados.dias()) {
                    linhaTotais(destino, DATA.format(dia.data()), dia.totalAgendamentos(), dia.totaisPorStatus());
                }
                linhaTotais(destino, "TOTAL", dados.totalAgendamentos(), dados.totaisPorStatus());
            }
        }
    }

    private void linhaTotais(Writer destino, String data, Long total, AgendamentosSnapshot.Totais totais) throws IOException {
        linha(destino, data, total, totais.pendente(), totais.confirmado(), totais.cancelado(), totais.concluido());
    }

    private void linha(Writer destino, Object... valores) throws IOException {
        for (int indice = 0; indice < valores.length; indice++) {
            if (indice > 0) destino.write(';');
            Object valor = valores[indice];
            String texto = valor == null ? "" : valor.toString();
            if (!(valor instanceof Number) && formula(texto)) texto = "'" + texto;
            destino.write('"');
            destino.write(texto.replace("\"", "\"\""));
            destino.write('"');
        }
        destino.write("\r\n");
    }

    private boolean formula(String texto) {
        int indice = 0;
        while (indice < texto.length() && texto.charAt(indice) == ' ') indice++;
        return indice < texto.length() && "=+-@\t\r\n".indexOf(texto.charAt(indice)) >= 0;
    }
}
