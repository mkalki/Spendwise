CREATE INDEX payment_transactions_owner_pro_payments_idx
    ON payment_transactions (owner_id, product_id, created_at DESC)
    WHERE product_id LIKE 'SPENDWISE_PRO_%';
