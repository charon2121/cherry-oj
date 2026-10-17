#!/usr/bin/env python3
"""WORK-002: isolated MySQL/Redis + five real Java services + production Compose Judge.
Test data follows docs/protocols/testdata.md: problem-service writes a directory under <output>/problem-testdata, the judge
container reads the same absolute path (read-only bind mount), and only the address crosses services.
Run after Maven package and the local Judge image build (the sandbox executor is built into the judge image). Never uses the user's databases/volumes.
--keep leaves the test stack alive until <output>/stop exists, for browser verification.
"""
import argparse, secrets, stat, hashlib, http.cookiejar, io, json, os, pathlib, socket, subprocess, tempfile, time, urllib.error, urllib.request, uuid, zipfile
parser=argparse.ArgumentParser(description='Run WORK-002 on owned temporary databases/ports/volumes. Maven package and local Judge images required. Cleanup only removes this run; --keep waits for its output/stop file.')
parser.add_argument('--keep',action='store_true')
parser.add_argument('--preflight',action='store_true',help='Check tools, jars and images without starting services')
args=parser.parse_args()
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=pathlib.Path(tempfile.mkdtemp(prefix='cherry-work002-')).resolve()
# 测试数据根目录：problem-service 写，judge 容器按同一个绝对路径只读挂载（见 compose.yaml）。
TESTDATA_ROOT=OUT/'problem-testdata';TESTDATA_ROOT.mkdir(mode=0o755)
TESTDATA_ROOT.chmod(0o755)
NAME='cherry-work002-'+uuid.uuid4().hex[:8]
processes=[];containers=[];logs=[];extra_volumes=[]
def run(cmd,**kw):
 kw.setdefault('env',env)
 return subprocess.run(cmd,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,**kw)
def port():
 with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
ports={name:port() for name in ['mysql','redis','user','gateway','problem','judging','submission','judge','kafka','web']}
print('E2E output:',OUT,flush=True)
# Do not inherit Spring/Java injection or user service settings: they can override
# application.yaml and redirect migrations outside this owned stack.
env={key:os.environ[key] for key in ['PATH','HOME','TMPDIR','JAVA_HOME','LANG','LC_ALL',
 'DOCKER_HOST','DOCKER_CONTEXT','DOCKER_CONFIG','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH'] if key in os.environ}
env.update(JUDGE_BIND_ADDRESS='127.0.0.1',CHERRY_JUDGE_CONTROL_TOKEN='work002-isolated-control',JUDGE_NODE_ID=NAME,
 JUDGE_TESTDATA_VOLUME=NAME+'-testdata',CHERRY_TEST_DATA_ROOT=str(TESTDATA_ROOT),JUDGE_STRICT_WHITESPACE='false',JUDGE_PORT=str(ports['judge']),JUDGE_ADVERTISE_URL=f"http://127.0.0.1:{ports['judge']}",
 JUDGE_CONTROL_PLANE_URL=f"http://host.docker.internal:{ports['judging']}",JUDGE_HEARTBEAT_INTERVAL='1s')
env.update(WORK002_DB_PASSWORD=secrets.token_urlsafe(24),WORK002_MYSQL_PORT=str(ports['mysql']),
 WORK002_REDIS_PORT=str(ports['redis']),WORK002_KAFKA_PORT=str(ports['kafka']))
compose=['docker','compose','-f',str(ROOT/'compose.work-002-test.yaml'),'-p',NAME]
def docker(*cmd):return run(['docker',*cmd])
def sql(statement):
 try:return run(compose+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B'],env=env,input=statement.encode()).stdout.decode().strip()
 except subprocess.CalledProcessError as error:
  with open(OUT/'sql-errors.log','ab') as log:log.write(error.stderr)
  raise
def wait(check,description,seconds=60):
 deadline=time.monotonic()+seconds
 while time.monotonic()<deadline:
  try:
   result=check()
   if result:return result
  except (OSError,urllib.error.URLError,subprocess.CalledProcessError,AssertionError):pass
  time.sleep(.5)
 raise RuntimeError('timed out: '+description)
def raw(url):
 with urllib.request.urlopen(url,timeout=3) as r:return json.load(r)
def start(service,extra=None):
 config=dict(env,SERVER_PORT=str(ports[service]),CHERRY_LOG_PATH=str(OUT/'logs'),
 CHERRY_USER_SERVICE_URL=f"http://127.0.0.1:{ports['user']}",CHERRY_PROBLEM_SERVICE_URL=f"http://127.0.0.1:{ports['problem']}",
 CHERRY_JUDGING_BASE_URL=f"http://127.0.0.1:{ports['judging']}",CHERRY_IDENTITY_JWKS_URI=f"http://127.0.0.1:{ports['user']}/.well-known/jwks.json",
 CHERRY_IDENTITY_METADATA_URI=f"http://127.0.0.1:{ports['user']}/internal/identity/metadata",CHERRY_REDIS_PORT=str(ports['redis']),
 CHERRY_TEST_DATA_ROOT=str(TESTDATA_ROOT),
 CHERRY_JUDGE_NODE_LEASE_DURATION='4s',CHERRY_WEB_ORIGIN=f"http://{NAME}.localhost:{ports['web']}",
 CHERRY_AUTH_PRIVATE_KEY_LOCATION='file:'+str(OUT/'keys/active-private.pem'),CHERRY_AUTH_PUBLIC_KEY_LOCATION='file:'+str(OUT/'keys/active-public.pem'))
 for database in ['user','problem','judging','submission']:
  config.update({f'CHERRY_{database.upper()}_DB_URL':f"jdbc:mysql://127.0.0.1:{ports['mysql']}/cherry_{database}?serverTimezone=UTC",f'CHERRY_{database.upper()}_DB_USERNAME':'root',f'CHERRY_{database.upper()}_DB_PASSWORD':env['WORK002_DB_PASSWORD']})
 config.update(CHERRY_SUBMISSION_SERVICE_URL=f"http://127.0.0.1:{ports['submission']}",
 CHERRY_JUDGING_SERVICE_URL=f"http://127.0.0.1:{ports['judging']}",
 CHERRY_KAFKA_BOOTSTRAP_SERVERS=f"127.0.0.1:{ports['kafka']}",
 CHERRY_SUBMISSION_MESSAGING_ENABLED='true',CHERRY_SUBMISSION_ACCEPTING='true',CHERRY_FORMAL_JUDGING_ENABLED='true',
 # 服务间调用身份：各服务按名字读 <name>-private.pem，user-service 读公钥；端口是随机的，换令牌地址要显式给。
 CHERRY_SERVICE_IDENTITY_DIR=str(OUT/'service-identity'),
 CHERRY_SERVICE_TOKEN_URI=f"http://127.0.0.1:{ports['user']}/internal/service-tokens")
 if extra:config.update(extra)
 jar=ROOT/f'apps/server/{service}-service/target/{service}-service-0.0.1-SNAPSHOT.jar'
 log=open(OUT/f'{service}.log','ab');logs.append(log)
 command=['java','-Xmx256m','-jar',str(jar)]
 return command,config,log
jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
base=f"http://127.0.0.1:{ports['gateway']}";csrf=None;request_ids=[];owner=None
def request(path,method='GET',body=None,content_type='application/json',expect=200,extra_headers=None):
 global csrf
 headers={'Origin':f"http://{NAME}.localhost:{ports['web']}"}
 if method!='GET':
  if csrf is None:csrf=request('/api/auth/csrf')['token']
  headers.update({'X-CSRF-Token':csrf,'Content-Type':content_type})
 if extra_headers:headers.update(extra_headers)
 payload=body if isinstance(body,bytes) else None if body is None else json.dumps(body).encode()
 try:
  with opener.open(urllib.request.Request(base+path,payload,headers,method=method),timeout=120) as response:
   status=response.status;content=response.read();rid=response.headers.get('X-Request-Id')
 except urllib.error.HTTPError as error:status=error.code;content=error.read();rid=error.headers.get('X-Request-Id')
 result=json.loads(content) if content else None
 if status!=expect:raise AssertionError(f'{method} {path}: expected {expect}, got {status}: {result}')
 if rid:request_ids.append(rid)
 return result.get('data',result) if result else None
try:
 for service in ['user','judging','problem','submission','gateway']:
  assert (ROOT/f'apps/server/{service}-service/target/{service}-service-0.0.1-SNAPSHOT.jar').is_file(),f'Package {service} first'
 run(['java','-version'])
 for image in ['mysql:8.4','redis:7-alpine','apache/kafka-native:3.8.0','cherry-oj-judge:local']:docker('image','inspect',image)
 run(compose+['config','--quiet'],env=env)
 if args.preflight:
  print('Preflight PASS; no services started.',flush=True)
  raise SystemExit(0)
 run(compose+['up','-d','mysql','redis','kafka'],env=env)
 wait(lambda:sql('SELECT 1')=='1','MySQL')
 sql('CREATE DATABASE cherry_user; CREATE DATABASE cherry_problem; CREATE DATABASE cherry_judging; CREATE DATABASE cherry_submission;')
 run([str(ROOT/'scripts/identity-keys'),'init',str(OUT/'keys')])
 run([str(ROOT/'scripts/service-identity-keys.sh'),str(OUT/'service-identity')])
 command,config,log=start('user')
 subprocess.run(command+['--cherry.auth.mode=bootstrap','--cherry.auth.bootstrap-username=work002admin','--management.endpoint.health.validate-group-membership=false'],env=config,cwd=OUT,input=b'Work002-Initial-Password\n',stdout=log,stderr=log,check=True,timeout=60)
 for service in ['user','judging','problem','submission','gateway']:
  command,config,log=start(service);p=subprocess.Popen(command,env=config,cwd=OUT,stdout=log,stderr=log);processes.append(p)
  wait(lambda:raw(f"http://127.0.0.1:{ports[service]}/actuator/health")['status']=='UP',service,90)
 print('Five services healthy; empty judging database, no provision.',flush=True)
 for authorization in ['', 'Bearer wrong-control']:
  try:
   urllib.request.urlopen(urllib.request.Request(f"http://127.0.0.1:{ports['judging']}/internal/judge-nodes/v1/register",b'{}',{'Content-Type':'application/json','Authorization':authorization}),timeout=3)
   raise AssertionError('node control accepted unauthenticated request')
  except urllib.error.HTTPError as error:
   assert error.code==401 and json.load(error)['code']=='NODE_UNAUTHORIZED'

 request('/api/auth/login','POST',{'username':'work002admin','password':'Work002-Initial-Password'})
 csrf=None
 request('/api/auth/password/change','POST',{'currentPassword':'Work002-Initial-Password','newPassword':'Work002-Changed-Password'},expect=204)
 csrf=None
 owner=request('/api/auth/login','POST',{'username':'work002admin','password':'Work002-Changed-Password'})['user']['id']
 csrf=None
 other=request('/api/admin/users','POST',{'username':'work002user'},expect=201)
 def content(row,title='WORK-002 加法校验'):
  return {'slug':'work002-plus','title':title,'statementMarkdown':'给定两个整数，输出它们的和。','inputDescriptionMarkdown':'两个整数。','outputDescriptionMarkdown':'一个整数。','constraintsMarkdown':None,'hintMarkdown':None,'difficulty':'EASY','tags':[],'samples':[{'ordinal':1,'input':'1 2','output':'3','explanationMarkdown':None}],'starterCode':'','rowVersion':row['rowVersion']}
 def zip_of(cases):
  data=io.BytesIO()
  with zipfile.ZipFile(data,'w',zipfile.ZIP_DEFLATED) as z:
   items=[('测试数据/',b'')]+[(f'测试数据/{n}.{ext}',c) for n,i,o in cases for ext,c in (('in',i),('out',o))]+[('__MACOSX/._data',b'\xff')]
   for name,content_bytes in items:
    info=zipfile.ZipInfo(name);info.compress_type=zipfile.ZIP_DEFLATED
    info.external_attr=((stat.S_IFDIR|0o700) if name.endswith('/') else (stat.S_IFREG|0o600))<<16
    z.writestr(info,content_bytes)
  return data.getvalue()
 def put_test_data(pid,cases):
  boundary='work002-'+uuid.uuid4().hex
  upload=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="finder.zip"\r\nContent-Type: application/zip\r\n\r\n'.encode()+zip_of(cases)+f'\r\n--{boundary}--\r\n'.encode())
  return request(f'/api/admin/problems/{pid}/test-data','PUT',upload,f'multipart/form-data; boundary={boundary}',200)
 problem=request('/api/admin/problems','POST',{'slug':'work002-plus','title':'WORK-002 加法校验','difficulty':'EASY','codeMode':'ACM','languageId':'cpp'},expect=201)
 pid=problem['id'];problem_path=f'/api/admin/problems/{pid}'
 assert 'versions' not in problem and problem['testData'] is None and problem['visibility']=='PRIVATE',problem
 problem=request(problem_path,'PATCH',content(problem))
 asset=put_test_data(pid,[('1',b'1 2\n',b'3\n')])
 assert asset['testcaseCount']==1 and len(asset['digest'])==64 and 'location' not in asset,asset
 assert request(problem_path)['testData']['digest']==asset['digest']
 # 协议目录由 problem-service 写出：地址只存在服务器上，浏览器永远看不到；数据文件与 testdata.json 齐全。
 directory=TESTDATA_ROOT/pid
 assert (directory/'testdata.json').is_file() and (directory/'1.in').is_file(),list(TESTDATA_ROOT.rglob('*'))
 before=request(problem_path+'/publish-check');assert any(c['code']=='ONLINE_JUDGE_NODE' and not c['passed'] for c in before['checks']),before
 source='#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}'
 calibration_payload={'languageId':'cpp','cpuNs':1000000000,'memoryBytes':268435456,'clockNs':None,'referenceSource':source}
 failure=request(problem_path+'/calibration','POST',calibration_payload,expect=503);assert failure['code']=='NO_ONLINE_JUDGE_NODE',failure
 run(compose+['up','-d','--wait','judge'],env=env)
 wait(lambda:sql('SELECT COUNT(*) FROM cherry_judging.judge_node WHERE lease_expires_at>UTC_TIMESTAMP(6)')=='1','node registration')
 print('Real Compose node registered.',flush=True)
 calibrated=request(problem_path+'/calibration','POST',calibration_payload)
 assert calibrated['status']=='VALID' and calibrated['testDataDigest']==asset['digest'],calibrated
 ready=request(problem_path+'/publish-check');assert ready['ready'],ready
 print('Upload → protocol directory → address-based C++ calibration: PASS.',flush=True)
 published=request(problem_path+'/publish','POST',{'rowVersion':request(problem_path)['rowVersion']})
 assert published['visibility']=='PUBLIC' and published['publishedAt'],published
 public=request('/api/problems/work002-plus');assert public['problemId']==pid and 'problemVersionId' not in public,public
 submissions=[]
 def submit(code,key=None,expect=201,expected_owner=None):
  key=key or str(uuid.uuid4())
  result=request('/api/submissions','POST',{'problemId':pid,'languageId':'cpp','source':code},expect=expect,
   extra_headers={'Idempotency-Key':key,'X-Expected-User-Id':expected_owner or owner})
  return key,result
 def finished(sid,verdict,seconds=90):
  def terminal():
   result=request('/api/submissions/'+sid)
   return result if result['status']=='DONE' else None
  result=wait(terminal,'submission '+sid,seconds)
  assert result['verdict']==verdict,(sid,result['verdict'],verdict)
  forbidden={'testcaseResults','output','diff','source','completeSource','testDataDigest','testDataLocation'}
  assert not forbidden.intersection(result),list(result)
  if verdict not in ['CE','SE']:assert 'message' not in result
  submissions.append({'id':sid,'verdict':verdict})
  print('Formal result:',sid,verdict,flush=True)
  return result
 first_key,first=submit(source)
 finished(first['id'],'AC')
 assert submit(source,first_key,200)[1]['id']==first['id']
 assert request('/api/submission-requests/'+first_key)['id']==first['id']
 submit(source+'\n// changed',first_key,409)
 for verdict,code in [('WA',source.replace('a+b','a-b')),('CE','int main( {'),
                      ('TLE','int main(){volatile unsigned long long x=0;while(true){x=x+1;}}'),
                      ('WA','#include <iostream>\n#include <string>\nint main(){std::string s;std::getline(std::cin,s);std::cout<<s;}')]:
  key,value=submit(code);finished(value['id'],verdict)
 print('AC/WA/CE/TLE and hidden-input echo projection: PASS.',flush=True)
 # Stop Kafka before admission: local facts must survive and deliver after recovery.
 run(compose+['stop','kafka'],env=env)
 kafka_key,kafka_value=submit(source)
 assert request('/api/submissions/'+kafka_value['id'])['status']=='PENDING'
 run(compose+['start','kafka'],env=env)
 finished(kafka_value['id'],'AC')
 # Frozen input is accepted while ready; loss of its only node must terminate with SE.
 run(compose+['stop','kafka'],env=env)
 node_key,node_value=submit(source)
 run(compose+['stop','judge'],env=env)
 wait(lambda:sql('SELECT COUNT(*) FROM cherry_judging.judge_node WHERE lease_expires_at>UTC_TIMESTAMP(6)')=='0','node lease expired')
 run(compose+['start','kafka'],env=env)
 finished(node_value['id'],'SE')
 assert int(sql("SELECT COUNT(*) FROM cherry_judging.judge_attempt a JOIN cherry_judging.judge_task t ON t.id=a.task_id WHERE t.submission_id='"+node_value['id']+"'"))<=3
 run(compose+['start','judge'],env=env)
 wait(lambda:sql('SELECT COUNT(*) FROM cherry_judging.judge_node WHERE lease_expires_at>UTC_TIMESTAMP(6)')=='1','node recovered')
 assert request(problem_path+'/publish-check')['ready']
 print('Kafka outage and node disappearance → bounded SE → recovery: PASS.',flush=True)
 # Crash the worker process during a real attempt. A successor must reclaim its lease.
 crash_key,crash_value=submit('int main(){volatile unsigned long long x=0;while(true){x=x+1;}}')
 wait(lambda:sql("SELECT status FROM cherry_judging.judge_task WHERE submission_id='"+crash_value['id']+"'")=='RUNNING','worker attempt running')
 processes[1].kill();processes[1].wait(timeout=15)
 command,config,log=start('judging');successor=subprocess.Popen(command,env=config,cwd=OUT,stdout=log,stderr=log);processes.append(successor)
 wait(lambda:raw(f"http://127.0.0.1:{ports['judging']}/actuator/health")['status']=='UP','worker successor')
 finished(crash_value['id'],'TLE',120)
 assert int(sql("SELECT attempt_no FROM cherry_judging.judge_task WHERE submission_id='"+crash_value['id']+"'"))>=2
 print('Worker crash and lease reclaim: PASS.',flush=True)
# 判题结果记录它读取的是哪一份数据：第一次提交判的是第一份数据。
 digest_of=lambda sid:sql("SELECT a.test_data_digest FROM cherry_judging.judge_attempt a JOIN cherry_judging.judge_task t ON t.id=a.task_id WHERE t.submission_id='"+sid+"' AND a.status='COMPLETED' ORDER BY a.attempt_no DESC LIMIT 1")
 assert digest_of(first['id'])==asset['digest'],digest_of(first['id'])
 # 替换测试数据：旧校准立即过期，新提交被挡住，直到按新数据重新校准；已冻结的 JudgeInput 不变。
 frozen_input=sql("SELECT payload FROM cherry_submission.judge_input WHERE submission_id='"+first['id']+"'")
 second=put_test_data(pid,[('1',b'1 2\n',b'3\n'),('2',b'100 -7\n',b'93\n')])
 assert second['testcaseCount']==2 and second['digest']!=asset['digest'],second
 stale=request(problem_path+'/publish-check');assert any(c['code']=='CALIBRATION' and not c['passed'] for c in stale['checks']),stale
 assert submit(source,expect=503)[1]['code']=='JUDGING_NOT_READY'
 recalibrated=request(problem_path+'/calibration','POST',calibration_payload)
 assert recalibrated['status']=='VALID' and recalibrated['testDataDigest']==second['digest'],recalibrated
 _,fresh=submit(source);done=finished(fresh['id'],'AC')
 assert done['testcaseCount']==2 and done['passedTestcaseCount']==2,done
 assert digest_of(fresh['id'])==second['digest']
 assert frozen_input==sql("SELECT payload FROM cherry_submission.judge_input WHERE submission_id='"+first['id']+"'")
 assert digest_of(first['id'])==asset['digest']
 print('Test data replaced → stale calibration blocks submissions → recalibrated judging reads the new data; frozen input unchanged: PASS.',flush=True)
 # 题目没有版本：公开题目的修改立即生效；取消公开后不能提交，重新公开后恢复。
 current=request(problem_path)
 request(problem_path,'PATCH',content(current,'WORK-002 加法校验（已修改）'))
 assert request('/api/problems/work002-plus')['title']=='WORK-002 加法校验（已修改）'
 unpublished=request(problem_path+'/unpublish','POST',{'rowVersion':request(problem_path)['rowVersion']});assert unpublished['visibility']=='PRIVATE'
 assert submit(source,expect=404)[1]['code']=='PROBLEM_NOT_AVAILABLE'
 request(problem_path+'/publish','POST',{'rowVersion':unpublished['rowVersion']})
 _,again=submit(source);finished(again['id'],'AC')
 print('Public edit takes effect immediately; unpublish blocks and republish restores: PASS.',flush=True)
 current_calibration=recalibrated

 # 核心代码（CORE）：用户文件 = C++ 前缀 + 用户代码，驱动代码单独成文件；报错里用户文件的行号是编辑器行号。
 canary='driver-canary-'+uuid.uuid4().hex
 driver='#include "solution.cpp"\n// '+canary+'\nint main() {\n    long long a, b;\n    cin >> a >> b;\n    cout << Solution().add(a, b) << "\\n";\n}\n'
 starter='class Solution {\npublic:\n    long long add(long long a, long long b) {\n    }\n};\n'
 def core_content(row,**changes):
  body={'slug':'work002-core','title':'WORK-002 核心代码加法','statementMarkdown':'实现 add，返回两数之和。','inputDescriptionMarkdown':'两个整数。','outputDescriptionMarkdown':'它们的和。','constraintsMarkdown':None,'hintMarkdown':None,'difficulty':'EASY','tags':[],'samples':[{'ordinal':1,'input':'1 2','output':'3','explanationMarkdown':None}],'starterCode':starter,'rowVersion':row['rowVersion']}
  body.update(changes);return body
 core=request('/api/admin/problems','POST',{'slug':'work002-core','title':'WORK-002 核心代码加法','difficulty':'EASY','codeMode':'CORE','languageId':'cpp'},expect=201)
 cid=core['id'];core_path=f'/api/admin/problems/{cid}'
 assert core['codeMode']=='CORE' and core['allowedLanguages'][0]['judgeTemplate'] is None,core
 core=request(core_path,'PATCH',core_content(core))
 put_test_data(cid,[('1',b'1 2\n',b'3\n'),('2',b'-4 -6\n',b'-10\n')])
 language_check=[c for c in request(core_path+'/publish-check')['checks'] if c['code']=='LANGUAGE'][0]
 assert not language_check['passed'] and '驱动代码' in language_check['message'],language_check
 core=request(core_path,'PATCH',core_content(core,judgeTemplate=driver))
 assert core['allowedLanguages'][0]['judgeTemplate']==driver
 reference='class Solution {\npublic:\n    long long add(long long a, long long b) { return a + b; }\n};\n'
 core_calibrated=request(core_path+'/calibration','POST',{**calibration_payload,'referenceSource':reference})
 assert core_calibrated['status']=='VALID',core_calibrated
 assert request(core_path+'/publish-check')['ready']
 request(core_path+'/publish','POST',{'rowVersion':request(core_path)['rowVersion']})
 public_core=request('/api/problems/work002-core')
 assert public_core['codeMode']=='CORE' and canary not in json.dumps(public_core) and 'judgeTemplate' not in json.dumps(public_core),public_core
 def core_submit(code):
  return request('/api/submissions','POST',{'problemId':cid,'languageId':'cpp','source':code},expect=201,
   extra_headers={'Idempotency-Key':str(uuid.uuid4()),'X-Expected-User-Id':owner})
 core_ac=core_submit(reference);finished(core_ac['id'],'AC')
 finished(core_submit(reference.replace('a + b','a - b'))['id'],'WA')
 # 用户第 4 行引用未定义的名字：文件里是第 6 行（前缀 2 行），用户看到的必须是第 4 行。
 undefined_name='class Solution {\npublic:\n    long long add(long long a, long long b) {\n        return a + missing_value;\n    }\n};\n'
 core_ce=finished(core_submit(undefined_name)['id'],'CE')
 assert 'solution.cpp:4:' in core_ce['message'] and 'solution.cpp:6:' not in core_ce['message'],core_ce['message']
 assert '/' not in core_ce['message'].split('\n')[0],core_ce['message']
 # 签名写错：错在驱动文件的调用处，报错指向 Main.cpp 的原始行号，不换算。
 wrong_signature='class Solution {\npublic:\n    long long add(long long a) { return a; }\n};\n'
 driver_ce=finished(core_submit(wrong_signature)['id'],'CE')
 assert 'Main.cpp:6:' in driver_ce['message'],driver_ce['message']
 # mysql 批处理输出会转义换行与反斜杠，按十六进制取出再解码。
 frozen=json.loads(bytes.fromhex(sql("SELECT HEX(CAST(payload AS CHAR)) FROM cherry_submission.judge_input WHERE submission_id='"+core_ac['id']+"'")).decode())
 assert frozen['completeSource']==driver and frozen['solutionPrefixLineCount']==2,frozen.keys()
 assert frozen['solutionSource'].startswith('#include <bits/stdc++.h>\n') and frozen['solutionSource'].endswith(reference),frozen['solutionSource'][:80]
 assert request('/api/submissions/'+core_ac['id']+'/source',extra_headers={'X-Expected-User-Id':owner})['source']==reference
 # 自测的运行错误：assert 用 __FILE__/__LINE__ 报位置，stderr 里的用户文件行号同样换算成编辑器行号。
 failing_assert='class Solution {\npublic:\n    long long add(long long a, long long b) {\n        assert(a == 999999);\n        return a + b;\n    }\n};\n'
 run_result=request('/api/custom-runs','POST',{'problemId':cid,'languageId':'cpp','source':failing_assert,'inputText':'1 2\n'},
  extra_headers={'X-Expected-User-Id':owner})
 assert run_result['status']=='RUNTIME_ERROR' and 'solution.cpp:4' in run_result['stderr']['text'] and 'solution.cpp:6' not in run_result['stderr']['text'],run_result
 # 改了驱动代码：标定不失效（只提示），公开检查提示重新标定，提交照常判。
 core=request(core_path,'PATCH',core_content(request(core_path),judgeTemplate=driver+'// revised\n'))
 calibration_check=[c for c in request(core_path+'/publish-check')['checks'] if c['code']=='CALIBRATION'][0]
 assert calibration_check['passed'] and '建议重新标定' in calibration_check['message'],calibration_check
 finished(core_submit(reference)['id'],'AC')
 for database in ['submission','judging']:
  assert canary not in sql('SELECT payload FROM cherry_'+database+'.outbox_event'),database
 for log_file in OUT.glob('*.log'):
  assert canary not in log_file.read_text(errors='replace'),log_file
 print('CORE: driver + prefixed solution file, editor line numbers in CE and runtime stderr, driver never exposed, recalibration hint: PASS.',flush=True)


 # Stop new admission while retaining reads and the original key's replay fact.
 submission_process=processes[3];submission_process.terminate();submission_process.wait(timeout=15)
 command,config,log=start('submission',{'CHERRY_SUBMISSION_ACCEPTING':'false'})
 paused=subprocess.Popen(command,env=config,cwd=OUT,stdout=log,stderr=log);processes.append(paused)
 wait(lambda:raw(f"http://127.0.0.1:{ports['submission']}/actuator/health")['status']=='UP','paused submission')
 assert request('/api/submissions/'+first['id'])['verdict']=='AC'
 assert submit(source,first_key,200)[1]['id']==first['id']
 assert submit(source,expect=503)[1]['code']=='SUBMISSIONS_PAUSED'
 paused.terminate();paused.wait(timeout=15)
 command,config,log=start('submission');resumed=subprocess.Popen(command,env=config,cwd=OUT,stdout=log,stderr=log);processes.append(resumed)
 wait(lambda:raw(f"http://127.0.0.1:{ports['submission']}/actuator/health")['status']=='UP','resumed submission')
 assert submit(source,first_key,200)[1]['id']==first['id']
 print('Rollback switch preserves reads, replay and stored facts: PASS.',flush=True)
 # Create a second real account through the public admin API, then prove ownership and
 # expected-editor-account checks using its own independent cookie jar.
 admin_opener,admin_csrf,admin_owner=opener,csrf,owner
 jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar));csrf=None
 request('/api/auth/login','POST',{'username':'work002user','password':other['temporaryPassword']});csrf=None
 request('/api/auth/password/change','POST',{'currentPassword':other['temporaryPassword'],'newPassword':'Work002-User-Password'},expect=204);csrf=None
 owner=request('/api/auth/login','POST',{'username':'work002user','password':'Work002-User-Password'})['user']['id'];csrf=None
 assert request('/api/submissions/'+first['id'],expect=404)['code']=='SUBMISSION_NOT_FOUND'
 assert request('/api/submission-requests/'+first_key,expect=404)['code']=='SUBMISSION_NOT_FOUND'
 assert submit(source,expected_owner=admin_owner,expect=409)[1]['code']=='SESSION_CHANGED'
 _,user_value=submit(source);finished(user_value['id'],'AC')
 opener,csrf,owner=admin_opener,admin_csrf,admin_owner
 assert request('/api/submissions/'+user_value['id'],expect=404)['code']=='SUBMISSION_NOT_FOUND'
 print('USER/ADMIN ownership and account-switch precondition: PASS.',flush=True)
 # The broker and persisted events may contain references/safe summaries, never source or hidden fields.
 for database in ['submission','judging']:
  payloads=sql('SELECT payload FROM cherry_'+database+'.outbox_event')
  for marker in ['completeSource','testcaseResults','testDataLocation','iostream']:
   assert marker not in payloads,(database,marker)
 node_id=sql('SELECT node_id FROM cherry_judging.judge_node LIMIT 1')
 evidence={'project':NAME,'ports':ports,'problemId':pid,'slug':'work002-plus',
  'testDataDigest':second['digest'],'baselineTestDataDigest':asset['digest'],'nodeId':node_id,
  'calibrationId':current_calibration['id'],'baselineCalibrationId':calibrated['id'],'requestIds':request_ids,
  'submissions':submissions,'workerCrash':'pass','frozenInput':'pass','result':'pass','rollback':'pass','kafkaRecovery':'pass','nodeRecovery':'pass'}
 evidence['core']={'problemId':cid,'slug':'work002-core','calibrationId':core_calibrated['id'],'acSubmissionId':core_ac['id'],
  'userLineCompileError':'solution.cpp:4','driverCompileError':'Main.cpp:6','runtimeStderr':'solution.cpp:4','result':'pass'}
 evidence['webOrigin']=f"http://{NAME}.localhost:{ports['web']}"
 (OUT/'evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2))
 # apps/web/e2e/judge-node.spec.ts 用它们停止/恢复同一个 Compose 项目里的 judge 节点。
 (OUT/'compose.env.json').write_text(json.dumps({k:v for k,v in env.items() if k.startswith('JUDGE_') or k in ('CHERRY_JUDGE_CONTROL_TOKEN','CHERRY_TEST_DATA_ROOT')}));os.chmod(OUT/'compose.env.json',0o600)
 print('E2E PASS:',OUT/'evidence.json',flush=True)
 if args.keep:
  import http.server, threading, urllib.parse
  dist=ROOT/'apps/web/dist'
  assert (dist/'index.html').is_file(),'Build Web before --keep'
  class BrowserProxy(http.server.SimpleHTTPRequestHandler):
   def __init__(self,*arguments,**options):super().__init__(*arguments,directory=str(dist),**options)
   def log_message(self,*arguments):pass
   def api(self):
    length=int(self.headers.get('Content-Length','0'))
    if length>2*1024*1024:self.send_error(413);return
    body=self.rfile.read(length) if length else None
    headers={k:v for k,v in self.headers.items() if k.lower() not in ['host','connection','content-length']}
    req=urllib.request.Request(base+self.path,body,headers,method=self.command)
    try:response=urllib.request.urlopen(req,timeout=30)
    except urllib.error.HTTPError as error:response=error
    with response:
     content=response.read();self.send_response(response.status)
     for key,value in response.headers.items():
      if key.lower() not in ['connection','transfer-encoding','content-length']:self.send_header(key,value)
     self.send_header('Content-Length',str(len(content)));self.end_headers();self.wfile.write(content)
   def do_GET(self):
    if self.path.startswith('/api/'):return self.api()
    path=urllib.parse.urlsplit(self.path).path
    if not (dist/path.lstrip('/')).is_file():self.path='/index.html'
    return super().do_GET()
   do_POST=api
   do_PATCH=api
   do_PUT=api
   do_DELETE=api
  browser_server=http.server.ThreadingHTTPServer(('127.0.0.1',ports['web']),BrowserProxy)
  threading.Thread(target=browser_server.serve_forever,daemon=True).start()
  print('Browser URL:',f"http://{NAME}.localhost:{ports['web']}/problems/work002-plus",flush=True)
  print('Hand test: Gateway',base,'; test user work002user / Work002-User-Password. Create',OUT/'stop','to clean up.',flush=True)
  while not (OUT/'stop').exists():time.sleep(1)
finally:
 for process in processes:
  if process.poll() is None:process.terminate()
 for process in processes:
  try:process.wait(timeout=10)
  except subprocess.TimeoutExpired:process.kill();process.wait()
 if not args.preflight:
  try:run(compose+['down','-v'],env=env)
  except Exception:print('Cleanup failed; use the recorded project name to inspect owned resources.',flush=True)
 for volume in extra_volumes:
  try:docker('volume','rm',volume)
  except Exception:pass
 for log in logs:log.close()
 print('Isolated E2E resources cleaned; evidence retained at',OUT,flush=True)
