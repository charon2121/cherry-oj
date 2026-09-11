"""Fixed, read-only SQL evidence binds the real Kafka chain to this run's identities."""
import hashlib
import json
import re
import time

from business_api import FIXTURES
from native_control import validate_registration


def uuid(value):
    if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', value):
        raise ValueError('invalid evidence UUID')
    return value


class Evidence:
    def __init__(self, dependencies):
        self.dependencies = dependencies

    def rows(self, database, query):
        if database not in {'judging', 'submission'} or not query.startswith('SELECT '):
            raise ValueError('read-only evidence query required')
        output = self.dependencies.docker('exec', 'cherry-ci-' + self.dependencies.identity + '-mysql',
            'mysql', '--defaults-extra-file=/run/cherry-client.cnf', '--batch', '--raw', '--skip-column-names',
            'cherry_ci_' + database, '-e', 'SET SESSION MAX_EXECUTION_TIME=5000; '
            'SET TRANSACTION READ ONLY; START TRANSACTION; ' + query + '; ROLLBACK', timeout=10)
        if output == 'Command completed with exit status 0.':
            return []
        rows = output.splitlines()
        if len(rows) > 16:
            raise ValueError('evidence query exceeded row bound')
        return [json.loads(row) for row in rows]

    def identity(self, node):
        deadline = time.monotonic() + 40
        while time.monotonic() < deadline:
            rows = self.rows('judging', "SELECT JSON_OBJECT('judgeEnvironmentId',BIN_TO_UUID(e.id),"
                "'environmentFingerprint',e.fingerprint,'status',e.status,'nodeId',n.node_id,"
                "'sessionId',BIN_TO_UUID(n.session_id),'registration',n.metadata_json,'online',n.lease_expires_at>UTC_TIMESTAMP(6)) "
                'FROM judge_environment e JOIN judge_node n ON n.judge_environment_id=e.id LIMIT 2')
            if len(rows) == 1 and rows[0]['online'] == 1:
                if rows[0]['nodeId'] != node or rows[0]['status'] != 'ACTIVE':
                    raise ValueError('fresh node/environment selection mismatch')
                uuid(rows[0]['judgeEnvironmentId'])
                registration = validate_registration(rows[0]['registration'], node)
                if registration['environmentFingerprint'] != rows[0]['environmentFingerprint'] or registration['sessionId'] != rows[0]['sessionId']:
                    raise ValueError('persisted registration does not match active node')
                return rows[0]
            time.sleep(.25)
        raise TimeoutError('fresh node registration did not arrive')

    def deployment(self, context):
        rows = self.rows('judging', "SELECT JSON_OBJECT('nodeId',d.node_id,'sha256',LOWER(HEX(d.expected_sha256)),"
            "'fileCount',d.file_count,'available',d.available,'sessionId',BIN_TO_UUID(d.session_id),"
            "'environmentId',BIN_TO_UUID(n.judge_environment_id),'online',n.lease_expires_at>UTC_TIMESTAMP(6)) "
            'FROM test_data_node_deployment d JOIN judge_node n ON n.node_id=d.node_id AND n.session_id=d.session_id '
            f"WHERE test_data_version_id=UUID_TO_BIN('{uuid(context['testDataVersionId'])}') LIMIT 2")
        if len(rows) != 1:
            raise ValueError('expected exactly one new deployment receipt')
        row = rows[0]
        expected = dict(nodeId=context['nodeId'], sha256=context['dataSha256'], fileCount=12, available=1,
                        sessionId=context['sessionId'], environmentId=context['judgeEnvironmentId'], online=1)
        if row != expected:
            raise ValueError('deployment receipt does not belong to the fresh node/data')
        return row

    def calibration(self, context):
        rows = self.rows('judging', "SELECT JSON_OBJECT('id',BIN_TO_UUID(id),'environmentId',BIN_TO_UUID(judge_environment_id),"
            "'cpuNs',cpu_ns,'memoryBytes',memory_bytes,'clockNs',clock_ns,'sourceType',source_type,"
            "'benchmark',benchmark_summary_json) FROM language_calibration WHERE status='VALID' AND language_id='cpp' "
            f"AND problem_version_id=UUID_TO_BIN('{uuid(context['problemVersionId'])}') LIMIT 2")
        if len(rows) != 1:
            raise ValueError('expected one fresh valid calibration')
        row = rows[0]
        if row['environmentId'] != context['judgeEnvironmentId'] or row['cpuNs'] != 1000000000 or row['memoryBytes'] != 268435456:
            raise ValueError('calibration environment/limits mismatch')
        if row['sourceType'] != 'BENCHMARK' or row['benchmark']['verdict'] != 'AC' or row['benchmark']['sourceSha256'] != hashlib.sha256((FIXTURES / 'calibration.cpp').read_bytes()).hexdigest():
            raise ValueError('calibration did not execute this reference source')
        return row

    def formal(self, context, submission_id, source):
        sid = uuid(submission_id)
        rows = self.rows('submission', "SELECT JSON_OBJECT('input',JSON_REMOVE(i.payload,'$.completeSource'),"
            "'status',s.status,'attempt',s.attempt_no,'userId',s.user_id,'sourceSha256',LOWER(SHA2(s.source,256))) "
            f"FROM submission s JOIN judge_input i ON i.submission_id=s.id WHERE s.id='{sid}' LIMIT 2")
        if len(rows) != 1 or rows[0]['status'] != 'DONE' or rows[0]['attempt'] != 1:
            raise ValueError('submission missing, unfinished or retried')
        row = rows[0]
        for field in ('problemId', 'problemVersionId', 'testDataVersionId', 'judgeEnvironmentId',
                      'environmentFingerprint', 'languageCalibrationId'):
            if row['input'][field] != context[field]:
                raise ValueError('JudgeInput identity mismatch: ' + field)
        expected_sha = hashlib.sha256(source.encode()).hexdigest()
        if row['sourceSha256'] != expected_sha or row['input']['sourceSha256'] != expected_sha or row['input']['totalCount'] != 6 or row['input']['testDataContentSha256'] != context['dataSha256']:
            raise ValueError('frozen source/test data differs from submitted fixture')
        # Cross-database SELECT is read-only and uses only the run's four fresh databases.
        links = self.rows('judging', "SELECT JSON_OBJECT('taskId',t.id,'status',t.status,'attempt',t.attempt_no,"
            "'requests',(SELECT COUNT(*) FROM cherry_ci_submission.outbox_event o JOIN inbox_event i ON i.event_id=o.event_id "
            "WHERE o.message_key=t.submission_id AND o.published=1),"
            "'lifecycle',(SELECT COUNT(*) FROM outbox_event o JOIN cherry_ci_submission.inbox_event i ON i.event_id=o.event_id "
            "WHERE o.message_key=t.submission_id AND o.published=1)) FROM judge_task t "
            f"WHERE t.submission_id='{sid}' LIMIT 2")
        if len(links) != 1 or links[0]['status'] != 'DONE' or links[0]['attempt'] != 1 or links[0]['requests'] != 1 or links[0]['lifecycle'] != 2:
            raise ValueError('real Kafka request/lifecycle receipt chain is incomplete')
        return dict(submissionId=sid, submission=row, kafka=links[0])
