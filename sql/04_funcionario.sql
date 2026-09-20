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

INSERT INTO FUNCIONARIO (CPF, Matricula, Cargo, Salario, CPF_Supervisor) VALUES
('20000000001', 'M0001', 'Gerente', 6500.00, NULL);

INSERT INTO FUNCIONARIO (CPF, Matricula, Cargo, Salario, CPF_Supervisor) VALUES
('20000000002', 'M0002', 'Bartender', 2800.00, '20000000001'),
('20000000003', 'M0003', 'Garconete', 2200.00, '20000000001'),
('20000000004', 'M0004', 'Garcom',    2200.00, '20000000001');
