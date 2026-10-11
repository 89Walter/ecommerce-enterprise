-- id = ID dell'utente in Keycloak (claim "sub" del token)
CREATE TABLE user_profiles (
    id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(30),
    default_recipient_name VARCHAR(120),
    default_address_line VARCHAR(255),
    default_city VARCHAR(120),
    default_postal_code VARCHAR(20),
    default_country_code VARCHAR(2),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_user_profiles_email (email)
);
