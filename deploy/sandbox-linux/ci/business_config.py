"""Fresh full-stack settings. No local profiles, credentials or business data are imported."""
import json
import os
from pathlib import Path
import secrets

SERVICES = ('user', 'problem', 'submission', 'judging', 'gateway')
DATABASES = SERVICES[:-1]
PORTS = {'gateway': 8080, 'user': 8081, 'problem': 8082, 'submission': 8083, 'judging': 8084}
IMAGES = {'mysql': 'mysql:8.4', 'redis': 'redis:7.4-alpine', 'kafka': 'apache/kafka-native:3.8.0'}


def private(path, text):
    path = Path(path)
    with open(path, 'x', opener=lambda p, flags: os.open(p, flags, 0o600)) as output:
        output.write(text)
    return path


def environment(directory, credentials):
    directory = Path(directory)
    env = dict(SPRING_PROFILES_ACTIVE='test', SERVER_ADDRESS='127.0.0.1',
               CHERRY_LOG_PATH=str(directory / 'logs'), CHERRY_WEB_ORIGIN='http://127.0.0.1:4173',
               CHERRY_SESSION_SECURE='false', CHERRY_REDIS_HOST='127.0.0.1', CHERRY_REDIS_PORT='16379',
               CHERRY_REDIS_PASSWORD=credentials['redis'], CHERRY_KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:19092',
               CHERRY_IDENTITY_JWKS_URI='http://127.0.0.1:8081/.well-known/jwks.json',
               CHERRY_IDENTITY_METADATA_URI='http://127.0.0.1:8081/internal/identity/metadata',
               CHERRY_AUTH_PRIVATE_KEY_LOCATION='file:' + str(directory / 'keys/active-private.pem'),
               CHERRY_AUTH_PUBLIC_KEY_LOCATION='file:' + str(directory / 'keys/active-public.pem'),
               CHERRY_TEST_DATA_ROOT=str(directory / 'problem-assets'),
               CHERRY_JUDGE_TESTDATA_ROOT=str(directory / 'forbidden-local-testdata'),
               CHERRY_JUDGE_CONTROL_TOKEN=credentials['control'],
               CHERRY_SUBMISSION_ACCEPTING='true', CHERRY_SUBMISSION_MESSAGING_ENABLED='true',
               CHERRY_FORMAL_JUDGING_ENABLED='true', CHERRY_CUSTOM_RUN_ENABLED='true',
               CHERRY_SUBMISSION_PROBLEM_TOKEN=credentials['submission_problem'],
               CHERRY_SERVICE_CALLS_SUBMISSION_PROBLEM_TOKENS=credentials['submission_problem'],
               CHERRY_SUBMISSION_JUDGING_TOKEN=credentials['submission_judging'],
               CHERRY_SUBMISSION_JUDGING_TOKENS=credentials['submission_judging'],
               CHERRY_JUDGING_SUBMISSION_TOKEN=credentials['judging_submission'],
               CHERRY_JUDGING_SUBMISSION_TOKENS=credentials['judging_submission'],
               LOGGING_LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE='1MB',
               LOGGING_LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP='8MB',
               LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY='1')
    for name, port in PORTS.items():
        env['CHERRY_' + name.upper() + '_SERVICE_URL'] = f'http://127.0.0.1:{port}'
    env['CHERRY_JUDGING_BASE_URL'] = env['CHERRY_JUDGING_SERVICE_URL']
    for name in DATABASES:
        prefix = 'CHERRY_' + name.upper() + '_DB_'
        env[prefix + 'URL'] = f'jdbc:mysql://127.0.0.1:13306/cherry_ci_{name}?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false'
        env[prefix + 'USERNAME'] = 'ci_' + name
        env[prefix + 'PASSWORD'] = credentials[name]
    return env


def create(directory):
    directory = Path(directory)
    directory.mkdir(mode=0o700)
    (directory / 'logs').mkdir(mode=0o700)
    credentials = {key: secrets.token_hex(32) for key in (*DATABASES, 'root', 'redis', 'control',
                   'submission_problem', 'submission_judging', 'judging_submission')}
    credentials.update(username='ci_admin', initialPassword='Ci-A!' + secrets.token_hex(16),
                       password='Ci-B!' + secrets.token_hex(16))
    private(directory / 'credentials.json', json.dumps(credentials))
    private(directory / 'bootstrap-password', credentials['initialPassword'] + '\n')
    private(directory / 'environment.json', json.dumps(environment(directory, credentials)))
    private(directory / 'mysql.env', 'MYSQL_ROOT_PASSWORD=' + credentials['root'] + '\n')
    private(directory / 'mysql.cnf', '[client]\nuser=root\npassword=' + credentials['root'] + '\n')
    sql = []
    for name in DATABASES:
        sql += [f'CREATE DATABASE cherry_ci_{name} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;',
                f"CREATE USER 'ci_{name}'@'%' IDENTIFIED BY '{credentials[name]}';",
                f"GRANT ALL ON cherry_ci_{name}.* TO 'ci_{name}'@'%';"]
    private(directory / 'init.sql', '\n'.join(sql) + '\n')
    private(directory / 'redis.conf', f"bind 0.0.0.0\nrequirepass {credentials['redis']}\nmaxmemory 64mb\nmaxmemory-policy noeviction\n")
    return credentials
