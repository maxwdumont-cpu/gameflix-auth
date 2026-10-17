-- GameFlix User Service: users table.
-- Output of `SHOW CREATE TABLE users;` against MySQL 8.4 after Hibernate (ddl-auto=update)
-- created it from User.java on 2026-10-17. Kept here so the column types and constraints can
-- be checked without running the project. Running this file by hand produces the same table.

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `display_name` varchar(50) NOT NULL,
  `email` varchar(254) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  `tos_accepted_at` datetime(6) DEFAULT NULL,
  `tos_version` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
