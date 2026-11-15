-- 运行时版本由工具链锁定；显示名与可编辑前缀仍归 problem-service，已有语言配置保持原值。
INSERT INTO language (language_id, display_name, solution_prefix, updated_at)
VALUES ('pypy', 'Python 3 (PyPy)', CONCAT('import sys', CHAR(10)), UTC_TIMESTAMP(6));
