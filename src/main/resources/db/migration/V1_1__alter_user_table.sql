ALTER TABLE "user"
    ADD COLUMN age SMALLINT NOT NULL;
    DO $$
    BEGIN
        PERFORM pg_sleep(30); -- Задержка в 10 секунд
    END $$;
