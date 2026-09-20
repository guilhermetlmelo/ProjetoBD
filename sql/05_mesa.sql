

CREATE TABLE MESA (
    Numero_Mesa  INTEGER     NOT NULL,
    Capacidade   INTEGER     NOT NULL,
    Localizacao  VARCHAR(40) NOT NULL,
    CONSTRAINT pk_mesa PRIMARY KEY (Numero_Mesa),
    CONSTRAINT ck_mesa_capacidade CHECK (Capacidade > 0)
);

INSERT INTO MESA (Numero_Mesa, Capacidade, Localizacao) VALUES
(1,  4, 'Salao interno'),
(2,  4, 'Salao interno'),
(3,  4, 'Salao interno'),
(4,  4, 'Salao interno'),
(5,  6, 'Area externa'),
(6,  6, 'Area externa'),
(7,  4, 'Mezanino'),
(8,  4, 'Mezanino'),
(9,  2, 'Balcao'),
(10, 2, 'Balcao');
