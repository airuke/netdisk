USE netdisk;

CREATE TABLE sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(32) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(32) NULL,
  status INT NOT NULL,
  UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_object (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  bucket VARCHAR(63) NOT NULL,
  object_key VARCHAR(255) NOT NULL,
  size BIGINT NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  hash VARCHAR(64) NULL,
  reference_count INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_storage_object (bucket, object_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_node (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  parent_id BIGINT NOT NULL,
  object_id BIGINT NULL,
  name VARCHAR(200) NOT NULL,
  type VARCHAR(8) NOT NULL,
  category VARCHAR(16) NOT NULL,
  size BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL,
  deleted_at DATETIME(6) NULL,
  trash_root_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  active_parent BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = FALSE THEN parent_id ELSE NULL END) STORED,
  UNIQUE KEY uk_active_name (user_id, active_parent, name),
  KEY ix_node_parent (user_id, parent_id, deleted),
  KEY ix_node_object (object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
