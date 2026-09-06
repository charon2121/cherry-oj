#!/usr/bin/env python3
"""Generate reviewable MySQL SQL; never connects to or modifies a database.

After reviewing the IDs and intended impact, run the output with mysql in batch
mode (without --force). Any guard failure aborts the connection and rolls back.
"""
import argparse
import uuid


def switch_sql(previous, target, legacy=False):
    previous, target = str(uuid.UUID(previous)), str(uuid.UUID(target))
    if previous == target:
        raise ValueError("source and destination must differ")
    online = "1=1" if legacy else "EXISTS (SELECT 1 FROM judge_node WHERE judge_environment_id=UUID_TO_BIN(@target) AND lease_expires_at>UTC_TIMESTAMP(6))"
    return f"""-- No calibration, deployment, problem version or JudgeInput is rewritten.
SET @previous='{previous}';
SET @target='{target}';
CREATE TEMPORARY TABLE switch_guard (ok INT NOT NULL CHECK (ok=1));
START TRANSACTION;
SELECT id FROM judge_node_registry_lock WHERE id=1 FOR UPDATE;
SELECT BIN_TO_UUID(id),fingerprint,status FROM judge_environment WHERE id IN (UUID_TO_BIN(@previous),UUID_TO_BIN(@target)) FOR UPDATE;
INSERT INTO switch_guard SELECT COUNT(*) FROM judge_environment WHERE id=UUID_TO_BIN(@previous) AND status='ACTIVE';
INSERT INTO switch_guard SELECT COUNT(*) FROM judge_environment WHERE id=UUID_TO_BIN(@target) AND status IN ('REGISTERED','RETIRED') AND {online};
UPDATE judge_environment SET status='RETIRED',retired_at=UTC_TIMESTAMP(6),row_version=row_version+1 WHERE id=UUID_TO_BIN(@previous) AND status='ACTIVE';
INSERT INTO switch_guard VALUES(ROW_COUNT());
UPDATE judge_environment SET status='ACTIVE',activated_at=UTC_TIMESTAMP(6),retired_at=NULL,row_version=row_version+1 WHERE id=UUID_TO_BIN(@target) AND status IN ('REGISTERED','RETIRED');
INSERT INTO switch_guard VALUES(ROW_COUNT());
INSERT INTO judging_audit_event(id,aggregate_type,aggregate_id,action,detail_json,created_at) VALUES(UUID_TO_BIN(UUID()),'ENVIRONMENT',UUID_TO_BIN(@target),'OPERATOR_ENVIRONMENT_SWITCH',JSON_OBJECT('previousEnvironmentId',@previous,'legacyTarget',{str(legacy).lower()}),UTC_TIMESTAMP(6));
COMMIT;
DROP TEMPORARY TABLE switch_guard;
"""


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("previous", help="current ACTIVE environment UUID")
    parser.add_argument("target", help="explicitly chosen destination environment UUID")
    parser.add_argument("--legacy-target", action="store_true", help="rollback to legacy-local: target need not have a node lease")
    args = parser.parse_args()
    print(switch_sql(args.previous, args.target, args.legacy_target))
