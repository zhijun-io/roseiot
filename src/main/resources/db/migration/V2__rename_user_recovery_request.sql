DO $$
BEGIN
    IF to_regclass('public.user_recovery_request') IS NOT NULL THEN
        ALTER TABLE user_recovery_request RENAME TO password_reset_request;
        ALTER INDEX user_recovery_request_token_hash_uq RENAME TO password_reset_request_token_hash_uq;
    END IF;
END $$;
