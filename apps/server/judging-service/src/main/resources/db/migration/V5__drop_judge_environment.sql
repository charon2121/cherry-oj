-- 取消「判题环境」：节点只带身份（nodeId + 会话）与能判的语言，标定按题目版本 × 语言。
-- 历史环境、指纹与切换记录不保留。

-- 节点：去掉环境归属与整份注册元数据，只留下语言清单。
ALTER TABLE judge_node ADD COLUMN languages_json JSON NULL;
UPDATE judge_node SET languages_json = JSON_EXTRACT(metadata_json, '$.languages[*].languageId');
UPDATE judge_node SET languages_json = JSON_ARRAY() WHERE languages_json IS NULL;
ALTER TABLE judge_node
    DROP FOREIGN KEY fk_node_environment,
    DROP INDEX idx_node_online,
    DROP CHECK ck_node_metadata,
    DROP COLUMN judge_environment_id,
    DROP COLUMN metadata_json,
    MODIFY languages_json JSON NOT NULL,
    ADD KEY idx_node_online (lease_expires_at, node_id),
    ADD CONSTRAINT ck_node_languages CHECK (JSON_TYPE(languages_json) = 'ARRAY');

-- 标定：同一题目版本 × 语言只保留最近批准的一条 VALID，其余转为 SUPERSEDED。
UPDATE language_calibration c
JOIN language_calibration newer
  ON newer.problem_version_id = c.problem_version_id AND newer.language_id = c.language_id
 AND newer.status = 'VALID'
 AND (newer.approved_at > c.approved_at OR (newer.approved_at = c.approved_at AND newer.id > c.id))
SET c.status = 'SUPERSEDED', c.row_version = c.row_version + 1
WHERE c.status = 'VALID';
ALTER TABLE language_calibration
    DROP FOREIGN KEY fk_calibration_environment,
    DROP INDEX uq_calibration_one_valid,
    DROP INDEX idx_calibration_resolve,
    DROP INDEX idx_calibration_environment_status,
    DROP COLUMN judge_environment_id,
    ADD CONSTRAINT uq_calibration_one_valid UNIQUE (problem_version_id, language_id, valid_slot),
    ADD KEY idx_calibration_resolve (problem_version_id, language_id, status);

DELETE FROM judging_audit_event WHERE aggregate_type = 'ENVIRONMENT';
ALTER TABLE judging_audit_event
    DROP CHECK ck_judging_audit_aggregate,
    ADD CONSTRAINT ck_judging_audit_aggregate CHECK (aggregate_type IN ('DEPLOYMENT', 'CALIBRATION', 'TASK'));

DROP TABLE judge_environment_language;
DROP TABLE judge_environment;
