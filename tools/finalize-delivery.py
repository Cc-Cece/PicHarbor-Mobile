"""Record verified build artifacts and the actual install; never launch apps or tests."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

root=Path(__file__).resolve().parents[1]
out=root/'delivery';out.mkdir(exist_ok=True)
code='04918862798f3365a67b01a8e6b80b9ae2c5d974'
subprocess.run(['git','diff','--exit-code',code,'--','android/app','ios/Sources','ios/Resources','ios/project.yml'],cwd=root,check=True)
def run_info(identifier):
    result=json.loads(subprocess.check_output(['gh','run','view',str(identifier),'--json','status,conclusion,headSha,url'],cwd=root,text=True,encoding='utf-8'))
    if result['conclusion']!='success' or result['headSha']!=code:raise SystemExit('Final build has not succeeded for the frozen source')
    return result
builds={'android':run_info(44120531527),'ios':run_info(44120531441)}
status=json.loads((Path(os.environ['LOCALAPPDATA'])/'PicHarborMobileBuild/device-preview/latest.json').read_text(encoding='utf-8-sig'))
apk=root/'android/app/build/outputs/apk/release/app-release.apk'
if status['status']!='installed' or status['device']!='691a42fe' or Path(status['apk']).resolve()!=apk.resolve():raise SystemExit('Unified repository APK has not been installed')
if hashlib.sha256(apk.read_bytes()).hexdigest()!=status['sha256']:raise SystemExit('Current APK differs from the installed artifact')
target=out/'PicHarbor-2.0.0-alpha.2-arm64.apk';shutil.copy2(apk,target)
ipas=list((out/'ios-final').glob('*.ipa'))
if len(ipas)!=1:raise SystemExit('Expected one final unsigned IPA')
ipa_build=json.loads((out/'ios-final/BUILD.json').read_text(encoding='utf-8'))
if ipa_build['source']!=code:raise SystemExit('IPA was built from a different source')
sources=sorted(out.glob('PicHarbor-Mobile-*-source.zip'),key=lambda p:p.stat().st_mtime)
if not sources:raise SystemExit('Committed mobile source archive is missing')
artifacts={}
for file in (target,ipas[0],sources[-1]):
    artifacts[file.relative_to(out).as_posix()]={'bytes':file.stat().st_size,'sha256':hashlib.sha256(file.read_bytes()).hexdigest()}
manifest={'date':'2026-11-18','repository':'Cc-Cece/PicHarbor-Mobile','branch':'feat/mobile-framework-parity',
          'native_source_commit':code,'delivery_source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
          'status':'development and build delivery complete; user device acceptance pending',
          'functional_tests_run':False,'phone_ui_opened':False,'ios_signed':False,'builds':builds,
          'core_tree_sha256':'0a4472b9ffd4537b0e4b4c14e5fb2ba66aeec757fdba91cfd4e046ff77fe2215',
          'android_certificate_sha256':'65de9c795940ee1a05dade807814a8e70ca395f6a1ab1e0075faab7cea2133f7',
          'install':{key:status[key]for key in ('device','applicationId','status','finished','sha256')},'artifacts':artifacts}
(out/'MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
shutil.copy2(root/'specs/DELIVERY-2026-11-18.md',out/'DELIVERY.md')
print(json.dumps(artifacts,indent=2))
