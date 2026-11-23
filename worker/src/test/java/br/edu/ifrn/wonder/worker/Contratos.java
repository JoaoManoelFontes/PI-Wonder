package br.edu.ifrn.wonder.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

public class Contratos {
    public static byte[] corpo(String tipo) throws IOException {
        try (var entrada = Contratos.class.getResourceAsStream("/contratos/" + tipo + ".json")) {
            return entrada.readAllBytes();
        }
    }

    public static Message mensagem(String tipo, UUID id) throws IOException {
        var propriedades = new MessageProperties();
        propriedades.setContentType("application/json");
        propriedades.setHeader("__TypeId__", "br.edu.ifrn.wonder.core.dto.ExportacaoSolicitada");
        return new Message(new String(corpo(tipo), StandardCharsets.UTF_8)
                .replace("f7cce9df-37bf-4ed0-8189-3d00eb540a13", id.toString()).getBytes(StandardCharsets.UTF_8), propriedades);
    }
}
