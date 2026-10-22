CREATE TABLE avaliacoes (
    id BIGSERIAL PRIMARY KEY,
    agendamento_id BIGINT NOT NULL,
    nota INTEGER NOT NULL,

    CONSTRAINT uk_avaliacoes_agendamento UNIQUE (agendamento_id),
    CONSTRAINT fk_avaliacoes_agendamento FOREIGN KEY (agendamento_id) REFERENCES agendamentos(id),
    CONSTRAINT chk_avaliacoes_nota CHECK (nota BETWEEN 1 AND 5)
);
