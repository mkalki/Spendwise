ALTER TABLE payment_transactions
    DROP CONSTRAINT payment_transactions_status_check;

ALTER TABLE payment_transactions
    ADD CONSTRAINT payment_transactions_status_check
        CHECK (status IN (
            'CREATED',
            'APPROVED',
            'CAPTURING',
            'COMPLETED',
            'PARTIALLY_REFUNDED',
            'REFUNDED',
            'REVERSED',
            'CANCELLED',
            'EXPIRED',
            'FAILED',
            'REQUIRES_RECONCILIATION'
        ));

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.payment_transactions'::regclass
          AND contype = 'c'
          AND (pg_get_constraintdef(oid) ILIKE '%paypal_capture_id%'
               OR pg_get_constraintdef(oid) ILIKE '%completed_at%')
    LOOP
        EXECUTE format('ALTER TABLE public.payment_transactions DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE payment_transactions
    ADD CONSTRAINT payment_transactions_capture_lifecycle_check
        CHECK (
            (status IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'REVERSED')
                AND paypal_capture_id IS NOT NULL AND completed_at IS NOT NULL)
            OR
            (status NOT IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'REVERSED')
                AND paypal_capture_id IS NULL AND completed_at IS NULL)
        );
