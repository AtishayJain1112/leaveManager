-- Employee Leave Management System - MySQL 8 schema
-- Executed automatically on startup (spring.sql.init.mode=always). Safe to re-run.
-- To create it manually:  mysql -u root -p < src/main/resources/schema.sql

CREATE DATABASE IF NOT EXISTS leave_manager;
USE leave_manager;

CREATE TABLE IF NOT EXISTS employees (
    id            INT          NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password      VARCHAR(100) NOT NULL,            -- BCrypt hash (60 chars)
    role          VARCHAR(20)  NOT NULL,            -- EMPLOYEE | MANAGER
    leave_balance INT          NOT NULL DEFAULT 20, -- remaining leave days
    PRIMARY KEY (id),
    CONSTRAINT uk_employees_email UNIQUE (email),
    CONSTRAINT chk_employees_balance CHECK (leave_balance >= 0)
);

CREATE TABLE IF NOT EXISTS leave_requests (
    id          INT          NOT NULL AUTO_INCREMENT,
    employee_id INT          NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_leave_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT chk_leave_dates CHECK (end_date >= start_date),
    INDEX idx_leave_employee (employee_id),
    INDEX idx_leave_status (status)
);
