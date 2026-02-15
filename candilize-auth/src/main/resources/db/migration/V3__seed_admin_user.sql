-- Seed default admin user for development/testing (password: admin123)
INSERT INTO users (username, email, password, role, enabled)
SELECT 'admin', 'admin@candilize.local', '$2a$10$8K1p/a0dL1LXMIgoEDFrwOfMQMIk8PQeLkM5R5r8xWxLzQJKdNkJe', 'ROLE_ADMIN', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
