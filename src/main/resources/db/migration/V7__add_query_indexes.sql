CREATE INDEX ix_orders_status_expires_at ON orders (status, expires_at);
CREATE INDEX ix_orders_status_updated_at ON orders (status, updated_at);
CREATE INDEX ix_orders_paid_at ON orders (paid_at);

CREATE INDEX ix_inquiry_member_id_id ON inquiry (member_id, id);
CREATE INDEX ix_inquiry_status ON inquiry (status);

CREATE INDEX ix_quote_inquiry_member_id_id ON quote_inquiry (member_id, id);
CREATE INDEX ix_quote_inquiry_status ON quote_inquiry (status);

CREATE INDEX ix_product_status_recommend_score ON product (status, recommend_score);
CREATE INDEX ix_product_status_sales_count ON product (status, sales_count);
CREATE INDEX ix_product_status_base_price ON product (status, base_price);
