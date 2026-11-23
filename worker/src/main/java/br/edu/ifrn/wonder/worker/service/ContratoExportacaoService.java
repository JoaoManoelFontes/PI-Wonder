package br.edu.ifrn.wonder.worker.service;

import org.springframework.stereotype.Service;
import br.edu.ifrn.wonder.worker.dto.*;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ContratoExportacaoService {
    private final JsonMapper conversor = JsonMapper.builder().build();

    public ExportacaoSolicitada ler(byte[] corpo) {
        try {
            var pedido = conversor.readValue(corpo, ExportacaoSolicitada.class);
            exigir(pedido != null && pedido.exportacaoId() != null && pedido.tipo() != null
                    && pedido.email() != null && !pedido.email().isBlank()
                    && pedido.nome() != null && !pedido.nome().isBlank()
                    && pedido.dados() != null && pedido.dados().isObject());
            snapshot(pedido);
            return pedido;
        } catch (RuntimeException erro) {
            throw new SolicitacaoInvalidaException();
        }
    }

    public Object snapshot(ExportacaoSolicitada pedido) {
        return switch (pedido.tipo()) {
            case USUARIOS -> {
                var dados = conversor.treeToValue(pedido.dados(), UsuariosSnapshot.class);
                exigir(dados.usuarios() != null);
                dados.usuarios().forEach(usuario -> exigir(usuario != null && usuario.id() != null
                        && usuario.ativo() != null));
                yield dados;
            }
            case PRESTADORES -> {
                var dados = conversor.treeToValue(pedido.dados(), PrestadoresSnapshot.class);
                exigir(dados.prestadores() != null);
                dados.prestadores().forEach(prestador -> exigir(prestador != null && prestador.id() != null
                        && prestador.nomeEstab() != null && prestador.status() != null && prestador.criadoEm() != null));
                yield dados;
            }
            case AGENDAMENTOS -> {
                var dados = conversor.treeToValue(pedido.dados(), AgendamentosSnapshot.class);
                exigir(dados.dataInicio() != null && dados.dataFim() != null && dados.dias() != null);
                validarTotais(dados.totalAgendamentos(), dados.totaisPorStatus());
                dados.dias().forEach(dia -> {
                    exigir(dia != null && dia.data() != null);
                    validarTotais(dia.totalAgendamentos(), dia.totaisPorStatus());
                });
                yield dados;
            }
        };
    }

    private void validarTotais(Long total, AgendamentosSnapshot.Totais totais) {
        exigir(total != null && total >= 0 && totais != null);
        exigir(totais.pendente() != null && totais.pendente() >= 0
                && totais.confirmado() != null && totais.confirmado() >= 0
                && totais.cancelado() != null && totais.cancelado() >= 0
                && totais.concluido() != null && totais.concluido() >= 0);
    }

    private void exigir(boolean valido) {
        if (!valido) throw new SolicitacaoInvalidaException();
    }
}
