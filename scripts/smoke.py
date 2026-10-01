#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
"""Validate the real document workflow in an explicitly authorized disposable local database."""
import argparse, json, urllib.request, urllib.error, urllib.parse, http.cookiejar, secrets, datetime, os
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8099');p.add_argument('--allow-test-data',action='store_true');p.add_argument('--browser-credentials');p.add_argument('--verify-existing',type=int);args=p.parse_args()
assert args.allow_test_data and urllib.parse.urlsplit(args.base).hostname in {'127.0.0.1','localhost','::1'},'An explicitly authorized local test deployment is required'
root=Path(__file__).resolve().parents[1];env=dict(x.split('=',1) for x in (root/'.env').read_text().splitlines() if '=' in x and not x.startswith('#'))
checks=[]
def check(name,value):
    assert value,name
    checks.append(name);print('PASS '+name,flush=True)
class Client:
    def __init__(self):self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.csrf=None
    def request(self,method,path,body=None,expected=200,csrf=True):
        if method!='GET' and csrf and self.csrf is None:self.csrf=self.request('GET','/api/auth/csrf')
        h={'Content-Type':'application/json'}
        if method!='GET' and csrf:h[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(args.base+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method)
        try:
            with self.http.open(req,timeout=30) as r:status=r.status;content=r.read()
        except urllib.error.HTTPError as e:status=e.code;content=e.read()
        v=json.loads(content) if content else None
        assert status==expected,f'{method} {path}: expected {expected}, got {status}, code={v.get("code") if isinstance(v,dict) else "unknown"}'
        return v
    def login(self,u,password):return self.request('POST','/api/auth/login',{'username':u,'password':password})
admin=Client();anonymous=Client()
check('healthy backend',anonymous.request('GET','/actuator/health')['status']=='UP')
check('anonymous blocked',anonymous.request('GET','/api/documents',expected=401)['code']=='UNAUTHENTICATED')
check('administrator login',admin.login('admin',env['ADMIN_PASSWORD'])['username']=='admin')
if args.verify_existing:
    d=admin.request('GET',f'/api/documents/{args.verify_existing}/report.json')
    check('persistent report survived restart',d['kind']=='CONTROLLED_DOCUMENT_REPORT' and len(d['report']['events'])>=8)
    print(json.dumps({'checks':len(checks),'documentId':args.verify_existing,'result':'PASS'}));raise SystemExit
check('CSRF blocked',admin.request('POST','/api/admin/departments',{'name':'must not save'},expected=403,csrf=False)['code']=='FORBIDDEN')
suffix=secrets.token_hex(3);password='Aa9'+secrets.token_urlsafe(20)
dept=admin.request('POST','/api/admin/departments',{'name':'文控验收部门-'+suffix})['id'];roles={r['name']:r['id'] for r in admin.request('GET','/api/admin/roles')}
def user(prefix,name,role,department=dept):
    username='qa-'+prefix+'-'+suffix
    a=admin.request('POST','/api/admin/users',{'username':username,'displayName':name+'（验收）','password':password,'roleId':roles[role],'departmentId':department,'enabled':True})
    client=Client();client.login(username,password);return client,a['id'],username
writer,writer_id,writer_name=user('writer','文件编写','文件编写人');owner,owner_id,owner_name=user('owner','文控负责','文控责任人');reviewer,reviewer_id,reviewer_name=user('reviewer','独立审批','独立审批人');reader,reader_id,reader_name=user('reader','文件阅读','文件阅读人');reader2,reader2_id,_=user('reader2','第二阅读人','文件阅读人');stranger,_,_=user('stranger','未分发人员','文件阅读人')
check('account hashes redacted',all('passwordHash' not in a for a in admin.request('GET','/api/admin/users')))
check('reader administration denied',reader.request('GET','/api/admin/users',expected=403)['code']=='FORBIDDEN')
today=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).date()
catalog={'title':'验收：文件发布与换版规程','category':'SOP','departmentId':dept,'ownerId':owner_id}
d=writer.request('POST','/api/documents',catalog);cid=d['document']['id'];rid=d['revisions'][0]['revision']['id']
check('catalog persisted with draft',d['document']['status']=='ACTIVE' and d['revisions'][0]['revision']['status']=='DRAFT')
check('unpublished document hidden from reader',reader.request('GET',f'/api/documents/{cid}',expected=403)['code']=='OUT_OF_SCOPE')
check('unassigned directory empty',stranger.request('GET','/api/documents')['total']==0)
def cmd(**v):return dict({'version':d['document']['version'],'requestKey':secrets.token_hex(16),'note':'验收：正文步骤、职责与版本信息明确'},**v)
def path():return f'/api/documents/{cid}/revisions/{rid}'
def act(client,action,body=None):return client.request('POST',path()+'/'+action,body or cmd())
def draft(content=None,summary='初次建立规程'):
    return {'version':d['document']['version'],'title':catalog['title'],'content':content or '【虚构验收资料】\n1. 文件编写人登记正文及变更说明。\n2. 独立审批人核对职责、步骤和生效日期。\n3. 文控责任人发布已批准版本，分发给阅读人员。\n4. 阅读人员核对当前版本并确认已阅读。\n5. 发现版本或内容异常，应暂停使用并联系文控责任人。','changeSummary':summary,'reviewerId':reviewer_id,'effectiveDate':today.isoformat(),'reviewDate':(today+datetime.timedelta(days=365)).isoformat(),'acknowledgementDue':(today+datetime.timedelta(days=7)).isoformat(),'recipients':[reader_id,reader2_id]}
v=draft();bad=dict(v,recipients=[]);check('empty audience rejected',writer.request('PUT',path(),bad,expected=400)['code']=='RECIPIENTS_REQUIRED')
bad=dict(v,reviewerId=owner_id);check('independent review enforced',writer.request('PUT',path(),bad,expected=400)['code']=='INVALID_ASSIGNEE')
d=writer.request('PUT',path(),v);check('revision body persisted',bool(d['revisions'][0]['revision']['contentHash']))
check('stale save rejected',writer.request('PUT',path(),v,expected=409)['code']=='STALE_VERSION')
c=cmd();d=act(writer,'submit',c);check('submission moves to review',d['revisions'][0]['revision']['status']=='REVIEW');again=act(writer,'submit',c);check('retry has single event',len(again['events'])==len(d['events']))
check('review content frozen',writer.request('PUT',path(),draft(),expected=409)['code']=='INVALID_STATE')
check('administrator cannot replace reviewer',admin.request('POST',path()+'/approve',cmd(),expected=403)['code']=='NOT_REVIEWER')
d=act(reviewer,'reject',cmd(note='验收：请补充版本失效后的处理步骤'));check('return retains content',d['revisions'][0]['revision']['status']=='REJECTED')
d=writer.request('PUT',path(),draft());d=act(writer,'submit');d=act(reviewer,'approve');check('independent approval persisted',d['revisions'][0]['revision']['status']=='APPROVED')
check('administrator cannot replace owner',admin.request('POST',path()+'/publish',cmd(),expected=403)['code']=='NOT_OWNER')
d=act(owner,'publish');check('publication creates two tasks',len(d['assignments'])==2 and d['document']['currentRevisionId']==rid)
check('reader sees only own assignment',len(reader.request('GET',f'/api/documents/{cid}')['assignments'])==1)
check('unassigned detail denied',stranger.request('GET',f'/api/documents/{cid}',expected=403)['code']=='OUT_OF_SCOPE')
check('personal workbench populated',len(reader.request('GET','/api/workbench')['reads'])==1)
old_rid=rid;old_hash=d['revisions'][0]['revision']['contentHash'];old_aids={a['accountId']:a['id'] for a in d['assignments']}
check('wrong content acknowledgement rejected',reader.request('POST',f'/api/documents/{cid}/assignments/{old_aids[reader_id]}/acknowledge',{'contentHash':'changed'},expected=409)['code']=='CONTENT_CHANGED')
check('impersonated acknowledgement rejected',reader2.request('POST',f'/api/documents/{cid}/assignments/{old_aids[reader_id]}/acknowledge',{'contentHash':old_hash},expected=403)['code']=='NOT_RECIPIENT')
reader.request('POST',f'/api/documents/{cid}/assignments/{old_aids[reader_id]}/acknowledge',{'contentHash':old_hash});d=owner.request('GET',f'/api/documents/{cid}');n=len(d['events']);reader.request('POST',f'/api/documents/{cid}/assignments/{old_aids[reader_id]}/acknowledge',{'contentHash':old_hash});d=owner.request('GET',f'/api/documents/{cid}');check('acknowledgement retry preserved evidence',len(d['events'])==n and any(a['acknowledgedHash']==old_hash for a in d['assignments']))
d=writer.request('POST',f'/api/documents/{cid}/revisions',{'version':d['document']['version']});rid=d['revisions'][0]['revision']['id'];check('new revision copies previous body',d['revisions'][0]['revision']['contentHash']==old_hash)
v=draft('【虚构验收资料 · R2】\n1. 打开受控目录核对当前生效版本。\n2. 检查生效日期、文控责任人与复审日期。\n3. 旧版只供历史查阅，不可作为当前作业文件。\n4. 阅读新版全部正文后完成本人的版本签收。\n5. 发现异常暂停使用并报告文控责任人。','补充旧版失效与新版签收要求')
d=writer.request('PUT',path(),v);d=act(writer,'submit');d=act(reviewer,'approve');d=act(owner,'publish');check('replacement keeps two versions',len(d['revisions'])==2 and d['revisions'][1]['revision']['status']=='SUPERSEDED')
check('old pending task closed',any(a['revisionId']==old_rid and a['accountId']==reader2_id and a['status']=='SUPERSEDED' for a in d['assignments']))
check('old acknowledged evidence retained',any(a['revisionId']==old_rid and a['status']=='ACKNOWLEDGED' and a['acknowledgedHash']==old_hash for a in d['assignments']))
check('old version cannot be signed',reader2.request('POST',f'/api/documents/{cid}/assignments/{old_aids[reader2_id]}/acknowledge',{'contentHash':old_hash},expected=409)['code']=='OLD_REVISION')
d=act(owner,'distribute',cmd(recipients=[reader_id],dueDate=(today+datetime.timedelta(days=9)).isoformat()));check('additional distribution deduplicated',len(d['assignments'])==4)
report=owner.request('GET',f'/api/documents/{cid}/report.json');check('report is complete and contains no advertising',report['kind']=='CONTROLLED_DOCUMENT_REPORT' and 'zhuatech2' not in json.dumps(report) and len(report['report']['events'])>=12)
check('published history delete blocked',writer.request('DELETE',f'/api/documents/{cid}?version={d["document"]["version"]}',expected=409)['code']=='HISTORY_PROTECTED')
check('scoped title search works',owner.request('GET','/api/documents?search='+urllib.parse.quote('换版')+'&size=1&sort=title')['total']==1)
check('unsafe sort blocked',owner.request('GET','/api/documents?sort=title%20desc',expected=400)['code']=='INVALID_INPUT')
check('dashboard tracks scoped pending tasks',owner.request('GET','/api/dashboard')['pending']==2)
check('audit records publication',any(a['action']=='PUBLISH' for a in reviewer.request('GET','/api/audit')))
remote_dept=admin.request('POST','/api/admin/departments',{'name':'验收隔离部门-'+suffix})['id'];remote,_,_=user('remote','其他部门','文控责任人',remote_dept)
check('other department denied',remote.request('GET',f'/api/documents/{cid}',expected=403)['code']=='OUT_OF_SCOPE')
check('other department directory empty',remote.request('GET','/api/documents')['total']==0)
# A separate unfinished revision supports browser-only writing, review and publication acceptance.
ui=writer.request('POST','/api/documents',dict(catalog,title='验收：设备启用前核对指引'));ui_id=ui['document']['id'];ui_rid=ui['revisions'][0]['revision']['id']
if args.browser_credentials:
    target=Path(args.browser_credentials);assert root not in target.parents,'Credentials stay outside source tree'
    fd=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as out:json.dump({'writer':writer_name,'owner':owner_name,'reviewer':reviewer_name,'reader':reader_name,'password':password,'documentId':cid,'uiDocumentId':ui_id,'uiRevisionId':ui_rid,'readerId':reader_id,'reviewerId':reviewer_id,'departmentId':dept},out)
print(json.dumps({'checks':len(checks),'documentId':cid,'uiDocumentId':ui_id,'result':'PASS'}))
