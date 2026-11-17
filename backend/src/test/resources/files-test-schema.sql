CREATE TABLE IF NOT EXISTS sys_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(32) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL, nickname VARCHAR(32), status INT NOT NULL
);
CREATE TABLE IF NOT EXISTS file_object (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, bucket VARCHAR(63) NOT NULL, object_key VARCHAR(255) NOT NULL,
 size BIGINT NOT NULL, mime_type VARCHAR(100) NOT NULL, hash VARCHAR(64), reference_count INT NOT NULL,
 status VARCHAR(16) NOT NULL, created_at TIMESTAMP NOT NULL, UNIQUE(bucket,object_key)
);
CREATE TABLE IF NOT EXISTS file_node (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, parent_id BIGINT NOT NULL,
 object_id BIGINT, name VARCHAR(200) NOT NULL, type VARCHAR(8) NOT NULL, category VARCHAR(16) NOT NULL,
 size BIGINT NOT NULL, deleted BOOLEAN NOT NULL, deleted_at TIMESTAMP, trash_root_id BIGINT,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 active_parent BIGINT GENERATED ALWAYS AS (CASE WHEN deleted=FALSE THEN parent_id ELSE NULL END),
 UNIQUE(user_id,active_parent,name)
);

-- 模块 8-10 的隔离测试结构；生产 MySQL 使用 database 中的迁移 SQL。
ALTER TABLE file_object ADD COLUMN IF NOT EXISTS cleanup_after TIMESTAMP;
CREATE TABLE IF NOT EXISTS share_link (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  node_id BIGINT NOT NULL,
  code VARCHAR(32) NOT NULL,
  extraction_hash VARCHAR(100) NULL,
  expires_at TIMESTAMP NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL,
  UNIQUE(code)
);
CREATE TABLE IF NOT EXISTS file_upload_session (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  client_id VARCHAR(64) NOT NULL,
  parent_id BIGINT NOT NULL,
  name VARCHAR(200) NOT NULL,
  size BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  chunk_size INT NOT NULL,
  chunk_count INT NOT NULL,
  bucket VARCHAR(63) NOT NULL,
  object_prefix VARCHAR(255) NOT NULL,
  status VARCHAR(16) NOT NULL,
  result_node_id BIGINT NULL,
  output_object_id BIGINT NULL,
  merge_token VARCHAR(36) NULL,
  merge_lease_until TIMESTAMP NULL,
  instant BOOLEAN NOT NULL DEFAULT FALSE,
  parts_cleaned BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  closed_at TIMESTAMP NULL,
  UNIQUE(user_id, client_id)
);
CREATE TABLE IF NOT EXISTS file_upload_part (
  session_id VARCHAR(36) NOT NULL,
  part_index INT NOT NULL,
  size BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  object_key VARCHAR(255) NOT NULL,
  attempt VARCHAR(36) NOT NULL,
  status VARCHAR(16) NOT NULL,
  lease_until TIMESTAMP NULL,
  PRIMARY KEY(session_id, part_index)
);
CREATE TABLE IF NOT EXISTS file_upload_cancel (
  user_id BIGINT NOT NULL,
  client_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY(user_id, client_id)
);
