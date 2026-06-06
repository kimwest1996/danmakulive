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

-- Live danmaku (P0)
CREATE TABLE IF NOT EXISTS live_danmaku (
    id            VARCHAR(36)   PRIMARY KEY,
    room_id       VARCHAR(36)   NOT NULL,
    user_id       VARCHAR(36)   NOT NULL,
    user_name     VARCHAR(64)   NOT NULL,
    content       VARCHAR(255)  NOT NULL,
    send_time     BIGINT        NOT NULL COMMENT 'epoch_ms',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_room_time (room_id, send_time DESC),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Live room (P1)
CREATE TABLE IF NOT EXISTS live_room (
    id              VARCHAR(36)   PRIMARY KEY,
    title           VARCHAR(128)  NOT NULL,
    owner_id        VARCHAR(36)   NOT NULL,
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1=live, 2=ended',
    replay_video_id VARCHAR(36)   DEFAULT NULL COMMENT '结束后关联的回放视频 ID',
    replay_status   TINYINT       NOT NULL DEFAULT 0 COMMENT '0=pending, 1=converting, 2=done',
    started_at      DATETIME      NOT NULL,
    ended_at        DATETIME      DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_owner (owner_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
