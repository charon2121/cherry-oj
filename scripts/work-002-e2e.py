#!/usr/bin/env python3
"""WORK-002: isolated MySQL/Redis + five real Java services + production Compose Judge.
Run after Maven package and local Judge/sandbox builds. Never uses the user's databases/volumes.
--keep leaves the test stack alive until <output>/stop exists, for browser verification.
"""
import argparse, secrets, stat, hashlib, http.cookiejar, io, json, os, pathlib, socket, subprocess, tempfile, time, urllib.error, urllib.request, uuid, zipfile
parser=argparse.ArgumentParser(description='Run WORK-002 on owned temporary databases/ports/volumes. Maven package and local Judge images required. Cleanup only removes this run; --keep waits for its output/stop file.')
parser.add_argument('--keep',action='store_true')
parser.add_argument('--preflight',action='store_true',help='Check tools, jars and images without starting services')
args=parser.parse_args()
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=pathlib.Path(tempfile.mkdtemp(prefix='cherry-work002-'))
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
 JUDGE_TESTDATA_VOLUME=NAME+'-testdata',JUDGE_STRICT_WHITESPACE='false',JUDGE_PORT=str(ports['judge']),JUDGE_ADVERTISE_URL=f"http://127.0.0.1:{ports['judge']}",
 JUDGE_CONTROL_PLANE_URL=f"http://host.docker.internal:{ports['judging']}",JUDGE_HEARTBEAT_INTERVAL='1s',JUDGE_ENVIRONMENT_FINGERPRINT=NAME)
env.update(WORK002_DB_PASSWORD=secrets.token_urlsafe(24),WORK002_MYSQL_PORT=str(ports['mysql']),
 WORK002_REDIS_PORT=str(ports['redis']),WORK002_KAFKA_PORT=str(ports['kafka']))
service_tokens={name:secrets.token_urlsafe(48) for name in ['submission-problem','submission-judging','judging-submission']}
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
 CHERRY_TEST_DATA_ROOT=str(OUT/'problem-assets'),CHERRY_JUDGE_TESTDATA_ROOT=str(OUT/'legacy-assets'),
 CHERRY_JUDGE_NODE_LEASE_DURATION='4s',CHERRY_WEB_ORIGIN=f"http://{NAME}.localhost:{ports['web']}",
 CHERRY_AUTH_PRIVATE_KEY_LOCATION='file:'+str(OUT/'keys/active-private.pem'),CHERRY_AUTH_PUBLIC_KEY_LOCATION='file:'+str(OUT/'keys/active-public.pem'))
 for database in ['user','problem','judging','submission']:
  config.update({f'CHERRY_{database.upper()}_DB_URL':f"jdbc:mysql://127.0.0.1:{ports['mysql']}/cherry_{database}?serverTimezone=UTC",f'CHERRY_{database.upper()}_DB_USERNAME':'root',f'CHERRY_{database.upper()}_DB_PASSWORD':env['WORK002_DB_PASSWORD']})
 config.update(CHERRY_SUBMISSION_SERVICE_URL=f"http://127.0.0.1:{ports['submission']}",
 CHERRY_JUDGING_SERVICE_URL=f"http://127.0.0.1:{ports['judging']}",
 CHERRY_KAFKA_BOOTSTRAP_SERVERS=f"127.0.0.1:{ports['kafka']}",
 CHERRY_SUBMISSION_MESSAGING_ENABLED='true',CHERRY_SUBMISSION_ACCEPTING='true',CHERRY_FORMAL_JUDGING_ENABLED='true',
 CHERRY_SUBMISSION_PROBLEM_TOKEN=service_tokens['submission-problem'],
 CHERRY_SERVICE_CALLS_SUBMISSION_PROBLEM_TOKENS=service_tokens['submission-problem'],
 CHERRY_SUBMISSION_JUDGING_TOKEN=service_tokens['submission-judging'],CHERRY_SUBMISSION_JUDGING_TOKENS=service_tokens['submission-judging'],
 CHERRY_JUDGING_SUBMISSION_TOKEN=service_tokens['judging-submission'],CHERRY_JUDGING_SUBMISSION_TOKENS=service_tokens['judging-submission'])
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
 for image in ['mysql:8.4','redis:7-alpine','apache/kafka-native:3.8.0','cherry-oj-judge:local','cherry-oj-sandbox:local']:docker('image','inspect',image)
 run(compose+['config','--quiet'],env=env)
 if args.preflight:
  print('Preflight PASS; no services started.',flush=True)
  raise SystemExit(0)
 run(compose+['up','-d','mysql','redis','kafka'],env=env)
 wait(lambda:sql('SELECT 1')=='1','MySQL')
 sql('CREATE DATABASE cherry_user; CREATE DATABASE cherry_problem; CREATE DATABASE cherry_judging; CREATE DATABASE cherry_submission;')
 run([str(ROOT/'scripts/identity-keys'),'init',str(OUT/'keys')])
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
 problem=request('/api/admin/problems','POST',{'slug':'work002-plus','title':'WORK-002 加法校验','difficulty':'EASY','codeMode':'ACM','languageId':'cpp'},expect=201)
 pid=problem['id'];vid=problem['versions'][0]['id'];version_path=f'/api/admin/problems/{pid}/versions/{vid}'
 version=request(version_path)
 version=request(version_path,'PATCH',{'title':'WORK-002 加法校验','statementMarkdown':'给定两个整数，输出它们的和。','inputDescriptionMarkdown':'两个整数。','outputDescriptionMarkdown':'一个整数。','constraintsMarkdown':None,'hintMarkdown':None,'difficulty':'EASY','tags':[],'samples':[{'ordinal':1,'input':'1 2','output':'3','explanationMarkdown':None}],'starterCode':'','changeSummary':None,'rowVersion':version['rowVersion']})
 data=io.BytesIO()
 with zipfile.ZipFile(data,'w',zipfile.ZIP_DEFLATED) as z:
  for name,content in [('测试数据/',b''),('测试数据/1.in',b'1 2\n'),('测试数据/1.out',b'3\n'),('__MACOSX/._data',b'\xff')]:
   info=zipfile.ZipInfo(name);info.compress_type=zipfile.ZIP_DEFLATED
   info.external_attr=((stat.S_IFDIR|0o700) if name.endswith('/') else (stat.S_IFREG|0o600))<<16
   z.writestr(info,content)
 zipbytes=data.getvalue();boundary='work002-'+uuid.uuid4().hex
 upload=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="finder.zip"\r\nContent-Type: application/zip\r\n\r\n'.encode()+zipbytes+f'\r\n--{boundary}--\r\n'.encode())
 asset=request(f'/api/admin/problems/{pid}/test-data','POST',upload,f'multipart/form-data; boundary={boundary}',201)
 assert asset['status']=='READY' and asset['contentSha256']==hashlib.sha256(zipbytes).hexdigest(),asset
 version=request(version_path+'/test-data','PUT',{'testDataVersionId':asset['id'],'rowVersion':version['rowVersion']})
 deploy_payload={'testDataVersionId':asset['id'],'expectedSha256':asset['contentSha256'],'rowVersion':version['rowVersion']}
 before=request(version_path+'/publish-check');assert any(c['code']=='ONLINE_JUDGE_NODE' and not c['passed'] for c in before['checks']),before
 failure=request(version_path+'/deployment','POST',deploy_payload,expect=503);assert failure['code']=='NO_ONLINE_JUDGE_NODE',failure
 run(compose+['up','-d','--wait','judge','sandbox'],env=env)
 wait(lambda:sql('SELECT COUNT(*) FROM cherry_judging.judge_node WHERE lease_expires_at>UTC_TIMESTAMP(6)')=='1','node registration')
 print('Real Compose node registered; first environment ACTIVE.',flush=True)
 deployed=request(version_path+'/deployment','POST',deploy_payload);assert deployed['status']=='READY',deployed
 again=request(version_path+'/deployment','POST',deploy_payload);assert again['status']=='READY',again
 source='#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}'
 calibrated=request(version_path+'/calibration','POST',{'languageId':'cpp','cpuNs':1000000000,'memoryBytes':268435456,'clockNs':None,'referenceSource':source,'rowVersion':version['rowVersion']})
 assert calibrated['status']=='VALID',calibrated
 deploy_payload['rowVersion']=request(version_path)['rowVersion']
 ready=request(version_path+'/publish-check');assert ready['ready'],ready
 assert sql('SELECT COUNT(*) FROM cherry_judging.test_data_deployment')=='0'
 assert sql('SELECT COUNT(*) FROM cherry_judging.test_data_node_deployment WHERE available=1')=='1'
 assert not (OUT/'legacy-assets').exists(),'Java unexpectedly created local deployment directory'
 print('Upload → bind → remote install → C++ calibration: PASS.',flush=True)
 published=request(version_path+'/publish','POST',{'rowVersion':request(version_path)['rowVersion']})
 deploy_payload['rowVersion']=published['rowVersion']
 current_problem=request(f'/api/admin/problems/{pid}')
 request(f'/api/admin/problems/{pid}','PATCH',{'slug':'work002-plus','visibility':'PUBLIC','rowVersion':current_problem['rowVersion']})
 assert request('/api/problems/work002-plus')['problemVersionId']==vid
 actual_fingerprint=sql("SELECT fingerprint FROM cherry_judging.judge_environment WHERE status='ACTIVE'")
 submissions=[]
 def submit(code,key=None,expect=201,expected_owner=None,expected_version=None):
  key=key or str(uuid.uuid4())
  result=request('/api/submissions','POST',{'problemId':pid,'expectedProblemVersionId':expected_version or vid,'languageId':'cpp','source':code},expect=expect,
   extra_headers={'Idempotency-Key':key,'X-Expected-User-Id':expected_owner or owner})
  return key,result
 def finished(sid,verdict,seconds=90):
  def terminal():
   result=request('/api/submissions/'+sid)
   return result if result['status']=='DONE' else None
  result=wait(terminal,'submission '+sid,seconds)
  assert result['verdict']==verdict,(sid,result['verdict'],verdict)
  forbidden={'caseResults','output','diff','source','completeSource','environmentFingerprint','testDataVersionId'}
  assert not forbidden.intersection(result),list(result)
  if verdict not in ['CE','SE']:assert 'message' not in result
  submissions.append({'id':sid,'verdict':verdict})
  print('Formal result:',sid,verdict,flush=True)
  return result
 first_vid=vid
 first_key,first=submit(source)
 finished(first['id'],'AC')
 assert submit(source,first_key,200,expected_version=first_vid)[1]['id']==first['id']
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
 request(version_path+'/deployment','POST',deploy_payload)
 assert request(version_path+'/publish-check')['ready']
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
 # Existing facts must remain unchanged when a new problem version is calibrated/published.
 frozen_input=sql("SELECT payload FROM cherry_submission.judge_input WHERE submission_id='"+first['id']+"'")
 current_problem=request(f'/api/admin/problems/{pid}')
 revision=request(f'/api/admin/problems/{pid}/versions','POST',{'rowVersion':current_problem['rowVersion'],'reuseTestData':True},expect=201)
 revised_path=f"/api/admin/problems/{pid}/versions/{revision['id']}"
 current_calibration=request(revised_path+'/calibration','POST',{'languageId':'cpp','cpuNs':1000000000,'memoryBytes':268435456,'clockNs':None,'referenceSource':source,'rowVersion':revision['rowVersion']})
 revision=request(revised_path)
 request(revised_path+'/publish','POST',{'rowVersion':revision['rowVersion']})
 assert frozen_input==sql("SELECT payload FROM cherry_submission.judge_input WHERE submission_id='"+first['id']+"'")
 assert submit(source,expect=409)[1]['code']=='PROBLEM_VERSION_CHANGED'
 old_vid=vid;vid=revision['id']
 print('New published version/calibration preserve the frozen JudgeInput; stale version rejected: PASS.',flush=True)
 # A ready replacement environment must not silently judge an input frozen to the old one.
 run(compose+['stop','kafka'],env=env)
 _,environment_value=submit(source)
 environment_input=sql("SELECT payload FROM cherry_submission.judge_input WHERE submission_id='"+environment_value['id']+"'")
 old_environment=sql("SELECT BIN_TO_UUID(id) FROM cherry_judging.judge_environment WHERE status='ACTIVE'")
 upgraded_env=dict(env,JUDGE_NODE_ID=NAME+'-upgrade',JUDGE_STRICT_WHITESPACE='true',JUDGE_TESTDATA_VOLUME=NAME+'-upgrade-data')
 extra_volumes.append(NAME+'-upgrade-data')
 run(compose+['up','-d','--wait','judge'],env=upgraded_env)
 wait(lambda:sql("SELECT COUNT(*) FROM cherry_judging.judge_environment WHERE status='REGISTERED'")=='1','replacement environment')
 new_environment=sql("SELECT BIN_TO_UUID(id) FROM cherry_judging.judge_environment WHERE status='REGISTERED'")
 def switch_environment(previous,target):
  generated=run(['python3',str(ROOT/'apps/server/judging-service/scripts/switch-environment.py'),previous,target]).stdout.decode()
  sql('USE cherry_judging;\n'+generated)
 switch_environment(old_environment,new_environment)
 request(version_path+'/deployment','POST',deploy_payload)
 run(compose+['start','kafka'],env=env)
 finished(environment_value['id'],'SE')
 assert environment_input==sql("SELECT payload FROM cherry_submission.judge_input WHERE submission_id='"+environment_value['id']+"'")
 run(compose+['up','-d','--wait','judge'],env=env)
 wait(lambda:sql("SELECT COUNT(*) FROM cherry_judging.judge_node WHERE node_id='"+NAME+"' AND lease_expires_at>UTC_TIMESTAMP(6)")=='1','original environment node')
 switch_environment(new_environment,old_environment)
 request(version_path+'/deployment','POST',deploy_payload)
 print('Different ready environment cannot replace a frozen input target: PASS.',flush=True)


 # Stop new admission while retaining reads and the original key's replay fact.
 submission_process=processes[3];submission_process.terminate();submission_process.wait(timeout=15)
 command,config,log=start('submission',{'CHERRY_SUBMISSION_ACCEPTING':'false'})
 paused=subprocess.Popen(command,env=config,cwd=OUT,stdout=log,stderr=log);processes.append(paused)
 wait(lambda:raw(f"http://127.0.0.1:{ports['submission']}/actuator/health")['status']=='UP','paused submission')
 assert request('/api/submissions/'+first['id'])['verdict']=='AC'
 assert submit(source,first_key,200,expected_version=first_vid)[1]['id']==first['id']
 assert submit(source,expect=503)[1]['code']=='SUBMISSIONS_PAUSED'
 paused.terminate();paused.wait(timeout=15)
 command,config,log=start('submission');resumed=subprocess.Popen(command,env=config,cwd=OUT,stdout=log,stderr=log);processes.append(resumed)
 wait(lambda:raw(f"http://127.0.0.1:{ports['submission']}/actuator/health")['status']=='UP','resumed submission')
 assert submit(source,first_key,200,expected_version=first_vid)[1]['id']==first['id']
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
  for marker in ['completeSource','caseResults','testDataContentSha256','iostream']:
   assert marker not in payloads,(database,marker)
 evidence={'project':NAME,'ports':ports,'problemId':pid,'versionId':vid,'slug':'work002-plus',
  'environmentFingerprint':actual_fingerprint,'testDataVersionId':asset['id'],'environmentId':deployed['environmentId'],
  'calibrationId':current_calibration['id'],'baselineVersionId':first_vid,'baselineCalibrationId':calibrated['id'],'sha256':asset['contentSha256'],'requestIds':request_ids,
  'submissions':submissions,'workerCrash':'pass','frozenVersion':'pass','frozenEnvironment':'pass','result':'pass','rollback':'pass','kafkaRecovery':'pass','nodeRecovery':'pass'}
 (OUT/'evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2))
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
