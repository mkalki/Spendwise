ALTER TABLE public.payment_transactions
    ADD COLUMN IF NOT EXISTS candidate_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_record
        JOIN pg_attribute column_record
          ON column_record.attrelid = constraint_record.conrelid
         AND column_record.attnum = ANY (constraint_record.conkey)
        WHERE constraint_record.contype = 'f'
          AND constraint_record.conrelid = 'public.payment_transactions'::regclass
          AND constraint_record.confrelid = 'public.product_candidates'::regclass
          AND column_record.attname = 'candidate_id'
    ) THEN
        ALTER TABLE public.payment_transactions
            ADD CONSTRAINT payment_transactions_candidate_id_fkey
            FOREIGN KEY (candidate_id)
            REFERENCES public.product_candidates (id);
    END IF;
END $$;
