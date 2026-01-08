SET @col_exists = (
    SELECT COUNT(*) 
    FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
    AND table_name = 'users' 
    AND column_name = 'email'
);

SET @sql = IF(@col_exists > 0, 'ALTER TABLE users DROP COLUMN email', 'SELECT 1 AS no_email_column');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
