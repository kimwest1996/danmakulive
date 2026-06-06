-- DanmakuLive Database Schema

CREATE DATABASE IF NOT EXISTS danmakulive;
USE danmakulive;

-- User account
CREATE TABLE IF NOT EXISTS user (
    id           VARCHAR(36)   PRIMARY KEY,
    email        VARCHAR(255)  NOT NULL,
    password     VARCHAR(255)  NOT NULL COMMENT 'bcrypt hash',
    nickname     VARCHAR(64)   NOT NULL,
    avatar_url   VARCHAR(512)  DEFAULT '',
    status       TINYINT       NOT NULL DEFAULT 0 COMMENT '0=normal, 1=disabled',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
