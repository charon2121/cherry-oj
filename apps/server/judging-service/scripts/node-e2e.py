#!/usr/bin/env python3
"""WORK-040: isolated MySQL/Redis + five real Java services + production Compose Judge.
Run after Maven package and docker compose build judge. Never uses the user's databases/volumes.
--keep leaves the test stack alive until <output>/stop exists, for browser verification.
"""
import argparse, stat, hashlib, http.cookiejar, io, json, os, pathlib, socket, subprocess, tempfile, time, urllib.error, urllib.request, uuid, zipfile
parser=argparse.ArgumentParser();parser.add_argument('--keep',action='store_true');args=parser.parse_args()
ROOT=pathlib.Path(__file__).resolve().parents[4]
OUT=pathlib.Path(tempfile.mkdtemp(prefix='cherry-work040-'))
NAME='cherry-work040-'+uuid.uuid4().hex[:8]
processes=[];containers=[];logs=[];extra_volumes=[]
def run(cmd,**kw):return subprocess.run(cmd,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,**kw)
def port():
 with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
ports={name:port() for name in ['mysql','redis','user','gateway','problem','judging','submission','judge']}
print('E2E output:',OUT,flush=True)
env=dict(os.environ,CHERRY_JUDGE_CONTROL_TOKEN='work040-isolated-control',JUDGE_NODE_ID=NAME,
 JUDGE_TESTDATA_VOLUME=NAME+'-testdata',JUDGE_STRICT_WHITESPACE='false',JUDGE_PORT=str(ports['judge']),JUDGE_ADVERTISE_URL=f"http://127.0.0.1:{ports['judge']}",
 JUDGE_CONTROL_PLANE_URL=f"http://host.docker.internal:{ports['judging']}",JUDGE_HEARTBEAT_INTERVAL='1s',JUDGE_ENVIRONMENT_FINGERPRINT=NAME)
compose=['docker','compose','-f',str(ROOT/'compose.yaml'),'-p',NAME]
def docker(*cmd):return run(['docker',*cmd])
def sql(statement):
 try:return docker('exec','-i',NAME+'-mysql','mysql','-uroot','-ptest-only-password','-N','-B','-e',statement).stdout.decode().strip()
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
 CHERRY_JUDGE_NODE_LEASE_DURATION='4s',CHERRY_WEB_ORIGIN='http://localhost:5173',
 CHERRY_AUTH_PRIVATE_KEY_LOCATION='file:'+str(OUT/'keys/active-private.pem'),CHERRY_AUTH_PUBLIC_KEY_LOCATION='file:'+str(OUT/'keys/active-public.pem'))
 for database in ['user','problem','judging']:
  config.update({f'CHERRY_{database.upper()}_DB_URL':f"jdbc:mysql://127.0.0.1:{ports['mysql']}/cherry_{database}?serverTimezone=UTC",f'CHERRY_{database.upper()}_DB_USERNAME':'root',f'CHERRY_{database.upper()}_DB_PASSWORD':'test-only-password'})
 if extra:config.update(extra)
 jar=ROOT/f'apps/server/{service}-service/target/{service}-service-0.0.1-SNAPSHOT.jar'
 log=open(OUT/f'{service}.log','ab');logs.append(log)
 command=['java','-Xmx256m','-jar',str(jar)]
 return command,config,log
jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
base=f"http://127.0.0.1:{ports['gateway']}";csrf=None;request_ids=[]
def request(path,method='GET',body=None,content_type='application/json',expect=200):
 global csrf
 headers={'Origin':'http://localhost:5173'}
 if method!='GET':
  if csrf is None:csrf=request('/api/auth/csrf')['token']
  headers.update({'X-CSRF-Token':csrf,'Content-Type':content_type})
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
 for kind,image,internal in [('mysql','mysql:8.4',3306),('redis','redis:7-alpine',6379)]:
  name=NAME+'-'+kind
  command=['run','-d','--name',name,'-p',f"127.0.0.1:{ports[kind]}:{internal}"]
  if kind=='mysql':command+=['-e','MYSQL_ROOT_PASSWORD=test-only-password']
  docker(*command,image);containers.append(name)
 wait(lambda:sql('SELECT 1')=='1','MySQL')
 sql('CREATE DATABASE cherry_user; CREATE DATABASE cherry_problem; CREATE DATABASE cherry_judging;')
 run([str(ROOT/'scripts/identity-keys'),'init',str(OUT/'keys')])
 command,config,log=start('user')
 subprocess.run(command+['--cherry.auth.mode=bootstrap','--cherry.auth.bootstrap-username=work040admin','--management.endpoint.health.validate-group-membership=false'],env=config,cwd=OUT,input=b'Work040-Initial-Password\n',stdout=log,stderr=log,check=True,timeout=60)
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

 request('/api/auth/login','POST',{'username':'work040admin','password':'Work040-Initial-Password'})
 csrf=None
 request('/api/auth/password/change','POST',{'currentPassword':'Work040-Initial-Password','newPassword':'Work040-Changed-Password'},expect=204)
 csrf=None
 request('/api/auth/login','POST',{'username':'work040admin','password':'Work040-Changed-Password'})
 csrf=None
 problem=request('/api/admin/problems','POST',{'slug':'work040-plus','title':'WORK-040 加法校验','difficulty':'EASY','codeMode':'ACM','languageId':'cpp'},expect=201)
 pid=problem['id'];vid=problem['versions'][0]['id'];version_path=f'/api/admin/problems/{pid}/versions/{vid}'
 version=request(version_path)
 version=request(version_path,'PATCH',{'title':'WORK-040 加法校验','statementMarkdown':'给定两个整数，输出它们的和。','inputDescriptionMarkdown':'两个整数。','outputDescriptionMarkdown':'一个整数。','constraintsMarkdown':None,'hintMarkdown':None,'difficulty':'EASY','tags':[],'samples':[{'ordinal':1,'input':'1 2','output':'3','explanationMarkdown':None}],'starterCode':'','changeSummary':None,'rowVersion':version['rowVersion']})
 data=io.BytesIO()
 with zipfile.ZipFile(data,'w',zipfile.ZIP_DEFLATED) as z:
  for name,content in [('测试数据/',b''),('测试数据/1.in',b'1 2\n'),('测试数据/1.out',b'3\n'),('__MACOSX/._data',b'\xff')]:
   info=zipfile.ZipInfo(name);info.compress_type=zipfile.ZIP_DEFLATED
   info.external_attr=((stat.S_IFDIR|0o700) if name.endswith('/') else (stat.S_IFREG|0o600))<<16
   z.writestr(info,content)
 zipbytes=data.getvalue();boundary='work040-'+uuid.uuid4().hex
 upload=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="finder.zip"\r\nContent-Type: application/zip\r\n\r\n'.encode()+zipbytes+f'\r\n--{boundary}--\r\n'.encode())
 asset=request(f'/api/admin/problems/{pid}/test-data','POST',upload,f'multipart/form-data; boundary={boundary}',201)
 assert asset['status']=='READY' and asset['contentSha256']==hashlib.sha256(zipbytes).hexdigest(),asset
 version=request(version_path+'/test-data','PUT',{'testDataVersionId':asset['id'],'rowVersion':version['rowVersion']})
 deploy_payload={'testDataVersionId':asset['id'],'expectedSha256':asset['contentSha256'],'rowVersion':version['rowVersion']}
 before=request(version_path+'/publish-check');assert any(c['code']=='ONLINE_JUDGE_NODE' and not c['passed'] for c in before['checks']),before
 failure=request(version_path+'/deployment','POST',deploy_payload,expect=503);assert failure['code']=='NO_ONLINE_JUDGE_NODE',failure
 run(compose+['up','-d','--wait'],env=env)
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
 print('Finder upload → bind → remote install → duplicate install → C++ calibration: PASS.',flush=True)
 run(compose+['stop','judge'],env=env)
 wait(lambda:any(c['code']=='ONLINE_JUDGE_NODE' and not c['passed'] for c in request(version_path+'/publish-check')['checks']),'offline lease')
 failure=request(version_path+'/deployment','POST',deploy_payload,expect=503);assert failure['code']=='NO_ONLINE_JUDGE_NODE'
 old_session=sql('SELECT BIN_TO_UUID(session_id) FROM cherry_judging.judge_node')
 run(compose+['start','judge'],env=env)
 wait(lambda:sql('SELECT BIN_TO_UUID(session_id) FROM cherry_judging.judge_node')!=old_session,'new node session')
 after=request(version_path+'/publish-check');assert not after['ready'],after
 request(version_path+'/deployment','POST',deploy_payload)
 assert request(version_path+'/publish-check')['ready']
 print('Stop → lease expiry → restart → revalidate local data: PASS.',flush=True)
 # Exercise the actual legacy switch on this owned stack, preserving node facts.
 node_receipt=sql('SELECT node_id,HEX(expected_sha256),BIN_TO_UUID(session_id),file_count,available FROM cherry_judging.test_data_node_deployment')
 original_calibration=sql('SELECT BIN_TO_UUID(id),status,cpu_ns,memory_bytes FROM cherry_judging.language_calibration')
 actual_fingerprint=sql("SELECT fingerprint FROM cherry_judging.judge_environment WHERE status='ACTIVE'")
 original_judging=processes[1];original_judging.terminate();original_judging.wait(timeout=15)
 command,config,log=start('judging',{'CHERRY_JUDGE_DEPLOYMENT_MODE':'legacy-local'})
 legacy_process=subprocess.Popen(command,env=config,cwd=OUT,stdout=log,stderr=log);processes.append(legacy_process)
 wait(lambda:raw(f"http://127.0.0.1:{ports['judging']}/actuator/health")['status']=='UP','legacy judging')
 deploy_payload['rowVersion']=request(version_path)['rowVersion']
 legacy_deployed=request(version_path+'/deployment','POST',deploy_payload);assert legacy_deployed['status']=='READY'
 # This intentionally restores legacy's documented UID-readable bind mount.
 legacy_root=OUT/'legacy-assets'
 for item in [legacy_root,*legacy_root.rglob('*')]:item.chmod(0o755 if item.is_dir() else 0o444)
 legacy_env=dict(env,TESTDATA_PATH=str(legacy_root),JUDGE_ENVIRONMENT_FINGERPRINT=actual_fingerprint)
 legacy_compose=['docker','compose','-f',str(ROOT/'compose.yaml'),'-f',str(ROOT/'compose.legacy.yaml'),'-p',NAME]
 run(legacy_compose+['up','-d','--wait','judge'],env=legacy_env)
 assert request(version_path+'/publish-check')['ready']
 # Read-only /judge execution proves the restored mount works without changing calibration.
 judge_request={'submissionId':str(uuid.uuid4()),'problemId':pid,'problemVersionId':vid,'testDataVersionId':asset['id'],'languageId':'cpp','source':source,'limits':{'cpuNs':1000000000,'memoryBytes':268435456},'mode':'submit'}
 with urllib.request.urlopen(urllib.request.Request(f"http://127.0.0.1:{ports['judge']}/judge",json.dumps(judge_request).encode(),{'Content-Type':'application/json'}),timeout=30) as response:legacy_result=json.load(response)
 assert legacy_result['verdict']=='AC',legacy_result
 assert sql('SELECT node_id,HEX(expected_sha256),BIN_TO_UUID(session_id),file_count,available FROM cherry_judging.test_data_node_deployment')==node_receipt
 assert sql('SELECT BIN_TO_UUID(id),status,cpu_ns,memory_bytes FROM cherry_judging.language_calibration')==original_calibration
 print('Legacy rollback: explicit mode + bind mount + actual C++ AC; node receipts and calibration unchanged: PASS.',flush=True)
 legacy_process.terminate();legacy_process.wait(timeout=15)
 command,config,log=start('judging');remote_process=subprocess.Popen(command,env=config,cwd=OUT,stdout=log,stderr=log);processes.append(remote_process)
 wait(lambda:raw(f"http://127.0.0.1:{ports['judging']}/actuator/health")['status']=='UP','restored remote judging')
 run(compose+['up','-d','--wait','judge'],env=env)
 wait(lambda:sql('SELECT COUNT(*) FROM cherry_judging.judge_node WHERE lease_expires_at>UTC_TIMESTAMP(6)')=='1','restored remote registration')
 request(version_path+'/deployment','POST',deploy_payload)
 assert request(version_path+'/publish-check')['ready']
 # Publish the old fixture, then create an editable revision for the new environment.
 published_snapshot=request(version_path+'/publish','POST',{'rowVersion':deploy_payload['rowVersion']})
 deploy_payload['rowVersion']=published_snapshot['rowVersion']
 current_problem=request(f'/api/admin/problems/{pid}')
 revision=request(f'/api/admin/problems/{pid}/versions','POST',{'rowVersion':current_problem['rowVersion'],'reuseTestData':True},expect=201)
 revision_path=f"/api/admin/problems/{pid}/versions/{revision['id']}"
 # Real environment upgrade: different policy, new identity and private volume.
 old_environment=sql("SELECT BIN_TO_UUID(id) FROM cherry_judging.judge_environment WHERE status='ACTIVE'")
 def switch(previous,target,legacy=False):
  command=['python3',str(ROOT/'apps/server/judging-service/scripts/switch-environment.py'),previous,target]
  if legacy:command.append('--legacy-target')
  generated=run(command).stdout.decode()
  return sql('USE cherry_judging;\n'+generated)
 try:
  switch(old_environment,str(uuid.uuid4()))
  raise AssertionError('environment guard accepted a missing destination')
 except subprocess.CalledProcessError:pass
 assert sql("SELECT BIN_TO_UUID(id) FROM cherry_judging.judge_environment WHERE status='ACTIVE'")==old_environment
 upgraded_env=dict(env,JUDGE_NODE_ID=NAME+'-new',JUDGE_TESTDATA_VOLUME=NAME+'-new-testdata',JUDGE_STRICT_WHITESPACE='true')
 extra_volumes.append(NAME+'-new-testdata')
 run(compose+['up','-d','--wait','judge'],env=upgraded_env)
 wait(lambda:sql("SELECT COUNT(*) FROM cherry_judging.judge_environment WHERE status='REGISTERED'")=='1','new environment remains registered')
 new_environment=sql("SELECT BIN_TO_UUID(id) FROM cherry_judging.judge_environment WHERE status='REGISTERED'")
 assert sql("SELECT BIN_TO_UUID(id) FROM cherry_judging.judge_environment WHERE status='ACTIVE'")==old_environment
 switch(old_environment,new_environment)
 request(version_path+'/deployment','POST',deploy_payload)
 new_check=request(version_path+'/publish-check');assert not new_check['ready'] and any(c['code']=='CALIBRATION' and not c['passed'] for c in new_check['checks'])
 new_calibration=request(revision_path+'/calibration','POST',{'languageId':'cpp','cpuNs':1000000000,'memoryBytes':268435456,'clockNs':None,'referenceSource':source,'rowVersion':revision['rowVersion']})
 assert new_calibration['status']=='VALID'
 assert request(revision_path+'/publish-check')['ready']
 # Return to the original environment and private volume; retain both histories.
 run(compose+['up','-d','--wait','judge'],env=env)
 wait(lambda:sql("SELECT COUNT(*) FROM cherry_judging.judge_node WHERE node_id='"+NAME+"' AND lease_expires_at>UTC_TIMESTAMP(6)")=='1','original node recovery')
 switch(new_environment,old_environment)
 deploy_payload['rowVersion']=request(version_path)['rowVersion']
 request(version_path+'/deployment','POST',deploy_payload)
 assert request(version_path+'/publish-check')['ready']
 assert sql("SELECT COUNT(*) FROM cherry_judging.language_calibration WHERE status='VALID'")=='2'
 assert request(version_path)==published_snapshot
 print('Environment switch guards, new identity/private volume, fresh calibration and return to old environment: PASS.',flush=True)
 evidence={'project':NAME,'ports':ports,'problemId':pid,'versionId':vid,'workbenchVersionId':revision['id'],'environmentFingerprint':actual_fingerprint,'testDataVersionId':asset['id'],'environmentId':deployed['environmentId'],'sha256':asset['contentSha256'],'requestIds':request_ids,'result':'pass','legacyRollback':'pass','environmentSwitch':'pass','defaultMode':'node-remote'}
 (OUT/'evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2))
 (OUT/'compose.env.json').write_text(json.dumps({k:v for k,v in env.items() if k.startswith('JUDGE_') or k=='CHERRY_JUDGE_CONTROL_TOKEN'}));os.chmod(OUT/'compose.env.json',0o600)
 print('E2E PASS:',OUT/'evidence.json',flush=True)
 if args.keep:
  print('Awaiting browser verification; create',OUT/'stop','to clean up.',flush=True)
  while not (OUT/'stop').exists():time.sleep(1)
finally:
 try:run(compose+['down','-v'],env=env)
 except Exception:pass
 for volume in extra_volumes:
  try:docker('volume','rm',volume)
  except Exception:pass
 for p in processes:p.terminate()
 for p in processes:
  try:p.wait(timeout=10)
  except subprocess.TimeoutExpired:p.kill();p.wait()
 for container in containers:
  try:docker('rm','-f',container)
  except Exception:pass
 for log in logs:log.close()
 print('Isolated E2E resources cleaned; evidence retained at',OUT,flush=True)
