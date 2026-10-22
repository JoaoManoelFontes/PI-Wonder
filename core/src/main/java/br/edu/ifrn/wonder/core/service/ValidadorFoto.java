package br.edu.ifrn.wonder.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

final class ValidadorFoto {

    private static final long TAMANHO_MAXIMO_FOTO = 5 * 1024 * 1024;

    private ValidadorFoto() {
    }

    static void validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o arquivo da foto");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_FOTO) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "A foto deve ter no máximo 5 MB");
        }
        try (InputStream conteudo = arquivo.getInputStream()) {
            byte[] assinatura = conteudo.readNBytes(12);
            boolean valido = switch (arquivo.getContentType() == null ? "" : arquivo.getContentType()) {
                case "image/jpeg" -> comecaCom(assinatura, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
                case "image/png" -> comecaCom(assinatura, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10});
                case "image/webp" -> assinatura.length >= 12
                        && comecaCom(assinatura, new byte[]{'R', 'I', 'F', 'F'})
                        && Arrays.equals(Arrays.copyOfRange(assinatura, 8, 12), new byte[]{'W', 'E', 'B', 'P'});
                default -> false;
            };
            if (!valido) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A foto deve ter conteúdo e formato JPEG, PNG ou WebP compatíveis");
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler a foto", exception);
        }
    }

    private static boolean comecaCom(byte[] conteudo, byte[] assinatura) {
        return conteudo.length >= assinatura.length
                && Arrays.equals(Arrays.copyOf(conteudo, assinatura.length), assinatura);
    }
}
