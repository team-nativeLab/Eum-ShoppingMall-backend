CREATE TABLE member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(30),
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6),
    CONSTRAINT pk_member PRIMARY KEY (id),
    CONSTRAINT uk_member_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(60),
    description VARCHAR(200),
    icon_key VARCHAR(50),
    sort_order INT NOT NULL,
    active BIT(1) NOT NULL,
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uk_category_slug UNIQUE (slug),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE banner (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    link_url VARCHAR(500),
    sort_order INT NOT NULL,
    visible BIT(1) NOT NULL,
    CONSTRAINT pk_banner PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE faq (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(50) NOT NULL,
    question VARCHAR(300) NOT NULL,
    answer TEXT NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT pk_faq PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    pinned BIT(1) NOT NULL,
    visible BIT(1) NOT NULL,
    CONSTRAINT pk_notice PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE site_content (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    code VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    CONSTRAINT pk_site_content PRIMARY KEY (id),
    CONSTRAINT uk_site_content_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    category_id BIGINT,
    name VARCHAR(200) NOT NULL,
    brand VARCHAR(100),
    description TEXT,
    image_url VARCHAR(500),
    base_price BIGINT NOT NULL,
    list_price BIGINT,
    price_visible BIT(1) NOT NULL,
    recommend_score INT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product_certification (
    product_id BIGINT NOT NULL,
    certification VARCHAR(30) NOT NULL,
    CONSTRAINT pk_product_certification PRIMARY KEY (product_id, certification),
    CONSTRAINT fk_product_certification_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product_sku (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    option_name VARCHAR(100) NOT NULL,
    price BIGINT NOT NULL,
    stock INT NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_product_sku PRIMARY KEY (id),
    CONSTRAINT fk_product_sku_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE checkout_delivery (
    member_id BIGINT NOT NULL,
    recipient VARCHAR(50),
    recipient_phone VARCHAR(30),
    zip_code VARCHAR(10),
    address VARCHAR(200),
    address_detail VARCHAR(200),
    delivery_request VARCHAR(200),
    CONSTRAINT pk_checkout_delivery PRIMARY KEY (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cart_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    selected BIT(1) NOT NULL,
    CONSTRAINT pk_cart_item PRIMARY KEY (id),
    CONSTRAINT uk_cart_item_member_sku UNIQUE (member_id, sku_id),
    CONSTRAINT fk_cart_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6),
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    INDEX ix_refresh_token_member_id (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount BIGINT NOT NULL,
    item_amount BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL,
    shipping_fee BIGINT NOT NULL,
    order_name VARCHAR(200) NOT NULL,
    recipient VARCHAR(50),
    recipient_phone VARCHAR(30),
    zip_code VARCHAR(10),
    address VARCHAR(200),
    address_detail VARCHAR(200),
    delivery_request VARCHAR(200),
    expires_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6),
    courier VARCHAR(50),
    tracking_number VARCHAR(50),
    idempotency_key VARCHAR(100),
    version BIGINT NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_no UNIQUE (order_no),
    CONSTRAINT uk_orders_member_idempotency UNIQUE (member_id, idempotency_key),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    option_name VARCHAR(100) NOT NULL,
    order_price BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_oi_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    order_id BIGINT NOT NULL,
    payment_key VARCHAR(200) NOT NULL,
    method VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    approved_amount BIGINT NOT NULL,
    balance_amount BIGINT NOT NULL,
    pg_raw TEXT,
    approved_at DATETIME(6),
    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT uk_payment_payment_key UNIQUE (payment_key),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inquiry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    member_id BIGINT,
    contact_phone VARCHAR(30),
    contact_email VARCHAR(255),
    inquiry_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    answer TEXT,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_inquiry PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE quote_inquiry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    member_id BIGINT,
    product_id BIGINT,
    company_name VARCHAR(100) NOT NULL,
    manager_name VARCHAR(50) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(255),
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_quote_inquiry PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
