-- Run this script after creating and connecting to ecommerce_db.
CREATE TABLE IF NOT EXISTS customer_orders (
    order_id VARCHAR(50) PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    product_name VARCHAR(100),
    amount NUMERIC(12,2),
    status VARCHAR(50),
    order_date TIMESTAMP
);

INSERT INTO customer_orders
(order_id, customer_id, product_name, amount, status, order_date)
VALUES
('ORD-1001', 'CUST-1001', 'Laptop', 75000.00, 'Delivered', CURRENT_TIMESTAMP),
('ORD-1002', 'CUST-1001', 'Monitor', 15000.00, 'Shipped', CURRENT_TIMESTAMP),
('ORD-1003', 'CUST-1002', 'Keyboard', 3000.00, 'Processing', CURRENT_TIMESTAMP)
ON CONFLICT (order_id) DO NOTHING;


-- Salesforce Contact mirror table used by the Platform Event consumer.
CREATE TABLE IF NOT EXISTS contacts (
    id BIGSERIAL PRIMARY KEY,
    salesforce_contact_id VARCHAR(18) UNIQUE NOT NULL,
    first_name VARCHAR(80),
    last_name VARCHAR(80),
    email VARCHAR(255),
    phone VARCHAR(40),
    operation VARCHAR(20),
    source_system VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
