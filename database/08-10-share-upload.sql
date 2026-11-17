-- 新安装先执行 01-core.sql，再执行本文件一次。
USE netdisk;
ALTER TABLE file_object ADD COLUMN cleanup_after DATETIME(6) NULL;
CREATE INDEX ix_object_hash ON file_object (hash, size, status);

CREATE TABLE share_link (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  node_id BIGINT NOT NULL,
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  extraction_hash VARCHAR(100) NULL,
  expires_at DATETIME(6) NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_share_code(code),
  KEY ix_share_owner(user_id, created_at),
  KEY ix_share_node(user_id, node_id, revoked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_upload_session (
  id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  client_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  parent_id BIGINT NOT NULL,
  name VARCHAR(200) NOT NULL,
  size BIGINT NOT NULL,
  sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  chunk_size INT NOT NULL,
  chunk_count INT NOT NULL,
  bucket VARCHAR(63) NOT NULL,
  object_prefix VARCHAR(255) NOT NULL,
  status VARCHAR(16) NOT NULL,
  result_node_id BIGINT NULL,
  output_object_id BIGINT NULL,
  merge_token VARCHAR(36) NULL,
  merge_lease_until DATETIME(6) NULL,
  instant BOOLEAN NOT NULL DEFAULT FALSE,
  parts_cleaned BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  closed_at DATETIME(6) NULL,
  UNIQUE KEY uk_upload_client(user_id, client_id),
  KEY ix_upload_owner(user_id, updated_at),
  KEY ix_upload_expiry(status, expires_at),
  KEY ix_upload_cleanup(status, parts_cleaned, closed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_upload_part (
  session_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  part_index INT NOT NULL,
  size BIGINT NOT NULL,
  sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  object_key VARCHAR(255) NOT NULL,
  attempt VARCHAR(36) NOT NULL,
  status VARCHAR(16) NOT NULL,
  lease_until DATETIME(6) NULL,
  PRIMARY KEY(session_id, part_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 取消请求的幂等记录：阻止丢失响应/延迟到达的初始化请求重新创建已取消任务。
CREATE TABLE file_upload_cancel (
  user_id BIGINT NOT NULL,
  client_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY(user_id, client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
