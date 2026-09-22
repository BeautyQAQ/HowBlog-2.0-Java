CREATE TABLE IF NOT EXISTS `tb_user_role` (
  `user_id` VARCHAR(64) NOT NULL,
  `role` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (`user_id`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tb_auth_session` (
  `id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `user_id` VARCHAR(64) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `expires_at` DATETIME(6) NOT NULL,
  `revoked_at` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_auth_session_user` (`user_id`),
  KEY `idx_auth_session_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tb_auth_refresh_token` (
  `token_hash` BINARY(32) NOT NULL,
  `session_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `consumed_at` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`token_hash`),
  KEY `idx_auth_refresh_session` (`session_id`),
  CONSTRAINT `fk_auth_refresh_session` FOREIGN KEY (`session_id`)
    REFERENCES `tb_auth_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;