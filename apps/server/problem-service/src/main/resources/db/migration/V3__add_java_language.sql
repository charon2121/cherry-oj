-- 运行时版本由工具链锁定；已有语言和题目配置不回写。
INSERT INTO language (language_id, display_name, solution_prefix, updated_at)
VALUES ('java', 'Java 21', CONCAT('import java.io.*;', CHAR(10), 'import java.util.*;', CHAR(10), 'import java.math.*;', CHAR(10)), UTC_TIMESTAMP(6));
