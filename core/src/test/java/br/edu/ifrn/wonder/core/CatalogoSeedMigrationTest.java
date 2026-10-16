package br.edu.ifrn.wonder.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import br.edu.ifrn.wonder.core.domain.StatusAgendamento;
import br.edu.ifrn.wonder.core.repository.AgendamentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CatalogoSeedMigrationTest {

    private static final UUID PERFIL_SEED_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Test
    void deveDisponibilizarMassaMinimaParaCatalogoEDisponibilidade() {
        Long prestadorId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM prestadores
                WHERE perfil_usuario_id = ?
                  AND status = 'ATIVO'
                  AND ativo = TRUE
                """,
                Long.class,
                PERFIL_SEED_ID
        );

        Integer categoriasAtivas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM categorias WHERE status = 'ATIVA'",
                Integer.class
        );
        Integer servicosAtivos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM servicos WHERE prestador_id = ? AND status = 'ATIVO'",
                Integer.class,
                prestadorId
        );
        Integer horarios = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM horarios_atendimento WHERE prestador_id = ?",
                Integer.class,
                prestadorId
        );
        Integer agendamentos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamentos WHERE prestador_id = ?",
                Integer.class,
                prestadorId
        );

        var conflitos = agendamentoRepository
                .findByPrestadorIdAndInicioLessThanAndFimGreaterThanAndStatusNotOrderByInicioAsc(
                        prestadorId,
                        LocalDateTime.of(2030, 1, 7, 10, 30),
                        LocalDateTime.of(2030, 1, 7, 10, 15),
                        StatusAgendamento.CANCELADO
                );

        assertThat(categoriasAtivas).isGreaterThanOrEqualTo(5);
        assertThat(servicosAtivos).isEqualTo(3);
        assertThat(horarios).isEqualTo(6);
        assertThat(agendamentos).isEqualTo(2);
        assertThat(conflitos)
                .singleElement()
                .extracting(agendamento -> agendamento.getStatus())
                .isEqualTo(StatusAgendamento.CONFIRMADO);
    }
}
