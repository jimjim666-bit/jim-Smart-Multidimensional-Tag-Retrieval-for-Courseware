CREATE DATABASE IF NOT EXISTS taglib DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE taglib;

CREATE TABLE user (
  user_id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  username       VARCHAR(32) NOT NULL UNIQUE,
  password_hash  VARCHAR(100) NOT NULL,
  nickname       VARCHAR(30) NOT NULL,
  role           VARCHAR(16) NOT NULL DEFAULT 'USER',
  status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until   DATETIME NULL,
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE resource (
  resource_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id        BIGINT NOT NULL,
  title           VARCHAR(120) NOT NULL,
  description     VARCHAR(2000) DEFAULT '',
  material_type   VARCHAR(20) NOT NULL,
  visibility      VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  status          VARCHAR(20) NOT NULL DEFAULT 'UPLOADING',
  download_count  INT NOT NULL DEFAULT 0,
  like_count      INT NOT NULL DEFAULT 0,
  dislike_count   INT NOT NULL DEFAULT 0,
  -- 资源与文件的关系只以 file_object.resource_id 为权威方向，这里不冗余保存 file_id
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at      DATETIME NULL,
  INDEX idx_owner (owner_id),
  INDEX idx_visibility_status (visibility, status, created_at)
) ENGINE=InnoDB;

CREATE TABLE file_object (
  file_id       VARCHAR(64) PRIMARY KEY,                -- UUID
  resource_id   BIGINT NOT NULL,                        -- 权威关联：文件属于哪个资源
  original_name VARCHAR(255) NOT NULL,
  storage_key   VARCHAR(255) NOT NULL,
  extension     VARCHAR(16) NOT NULL,
  mime_type     VARCHAR(100),
  size_bytes    BIGINT NOT NULL,
  checksum      CHAR(64),
  scan_status   VARCHAR(20) DEFAULT 'PASSED',
  storage_status VARCHAR(20) DEFAULT 'AVAILABLE',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_resource (resource_id)
) ENGINE=InnoDB;

CREATE TABLE tag (
  tag_id      BIGINT PRIMARY KEY AUTO_INCREMENT,
  tag_name    VARCHAR(50) NOT NULL,
  dimension   VARCHAR(20) NOT NULL DEFAULT 'OTHER',
  scope       VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  creator_id  BIGINT NOT NULL DEFAULT 0,                -- PUBLIC 固定 0；PRIVATE 必填创建者 user_id
  status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',    -- ACTIVE/DISABLED/MERGED
  merged_to_tag_id BIGINT NULL,                         -- status=MERGED 时的目标标签编号
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- 唯一键包含 creator_id：不同用户可有同名私有标签；PUBLIC 以 0 参与判重
  UNIQUE KEY uk_scope_dim_name_creator (scope, dimension, tag_name, creator_id)
) ENGINE=InnoDB;

CREATE TABLE resource_tag (
  resource_id BIGINT NOT NULL,
  tag_id      BIGINT NOT NULL,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (resource_id, tag_id),
  INDEX idx_tag (tag_id)
) ENGINE=InnoDB;

CREATE TABLE favorite (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_resource (user_id, resource_id)
) ENGINE=InnoDB;

CREATE TABLE rating (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  rating      VARCHAR(10) NOT NULL,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_resource (user_id, resource_id)
) ENGINE=InnoDB;

CREATE TABLE download_record (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_id BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  result      VARCHAR(20) NOT NULL,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_resource (resource_id),
  INDEX idx_date (created_at)
) ENGINE=InnoDB;

CREATE TABLE daily_statistics (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date       DATE NOT NULL,
  upload_count    INT DEFAULT 0,
  download_count  INT DEFAULT 0,
  active_resources INT DEFAULT 0,
  UNIQUE KEY uk_date (stat_date)
) ENGINE=InnoDB;

CREATE TABLE audit_log (
  audit_id    VARCHAR(64) PRIMARY KEY,
  actor_id    BIGINT NOT NULL,
  action      VARCHAR(50) NOT NULL,
  target_type VARCHAR(30),
  target_id   BIGINT,
  detail      VARCHAR(500),
  result      VARCHAR(20),
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
