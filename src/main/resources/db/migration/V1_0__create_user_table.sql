CREATE TABLE IF NOT EXISTS "user" (
    id SERIAL NOT NULL PRIMARY KEY,
    name varchar(50) NOT NULL,
    email varchar(50) NOT NULL
);