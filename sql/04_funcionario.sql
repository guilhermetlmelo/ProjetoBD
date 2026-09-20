CREATE TABLE FUNCIONARIO (
    CPF             VARCHAR(11)   NOT NULL,
    Matricula       VARCHAR(10)   NOT NULL,
    Cargo           VARCHAR(40)   NOT NULL,
    Salario         NUMERIC(10,2) NOT NULL,
    CPF_Supervisor  VARCHAR(11),
    CONSTRAINT pk_funcionario PRIMARY KEY (CPF),
    CONSTRAINT uq_funcionario_matricula UNIQUE (Matricula),
    CONSTRAINT fk_funcionario_pessoa FOREIGN KEY (CPF) REFERENCES PESSOA (CPF),
    CONSTRAINT fk_funcionario_supervisor FOREIGN KEY (CPF_Supervisor) REFERENCES FUNCIONARIO (CPF),
    CONSTRAINT ck_funcionario_salario CHECK (Salario > 0)
);