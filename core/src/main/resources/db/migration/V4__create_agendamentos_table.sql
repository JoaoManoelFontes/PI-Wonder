CREATE TABLE agendamentos (
    id BIGSERIAL PRIMARY KEY,
    cliente_id UUID NOT NULL,
    prestador_id BIGINT NOT NULL,
    servico_id BIGINT NOT NULL,
    inicio TIMESTAMP NOT NULL,
    fim TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',

    CONSTRAINT fk_agendamentos_cliente FOREIGN KEY (cliente_id) REFERENCES perfis_usuarios(id),
    CONSTRAINT fk_agendamentos_prestador FOREIGN KEY (prestador_id) REFERENCES prestadores(id),
    CONSTRAINT fk_agendamentos_servico FOREIGN KEY (servico_id) REFERENCES servicos(id),
    CONSTRAINT chk_agendamentos_periodo CHECK (inicio < fim)
);
