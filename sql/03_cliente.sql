CREATE TABLE CLIENTE (
    CPF                   VARCHAR(11) NOT NULL,
    Data_Primeira_Visita  DATE        NOT NULL,
    Fidelidade_Ativa      BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_cliente PRIMARY KEY (CPF),
    CONSTRAINT fk_cliente_pessoa FOREIGN KEY (CPF) REFERENCES PESSOA (CPF)
);