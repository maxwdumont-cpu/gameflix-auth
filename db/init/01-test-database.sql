-- Runs once, the first time the MySQL container starts with an empty volume.
-- The app database (gameflix) and app user (gameflix_app) are created by the image from
-- MYSQL_DATABASE / MYSQL_USER. This adds a separate database for the automated tests so a
-- test run never touches the data the running service uses.
CREATE DATABASE IF NOT EXISTS gameflix_test;
GRANT ALL PRIVILEGES ON gameflix_test.* TO 'gameflix_app'@'%';
FLUSH PRIVILEGES;
