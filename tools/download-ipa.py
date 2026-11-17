"""Download and verify an unsigned IPA from a successful mobile CI run."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import zipfile

import requests

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('run',type=int)
parser.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'delivery/ios')
args=parser.parse_args()
repo='Cc-Cece/PicHarbor-Mobile'
run=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/runs/{args.run}'],text=True,encoding='utf-8'))
if run['conclusion']!='success':raise SystemExit('The build did not finish successfully')
items=json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/runs/{args.run}/artifacts'],text=True,encoding='utf-8'))['artifacts']
artifacts=[a for a in items if a['name'].endswith('.ipa') and not a['expired']]
if len(artifacts)!=1:raise SystemExit('Expected exactly one unexpired IPA artifact')
artifact=artifacts[0]
token=subprocess.check_output(['gh','auth','token'],text=True,encoding='utf-8').strip()
args.output.mkdir(parents=True,exist_ok=True)
target=args.output/'artifact.zip.part'
digest=hashlib.sha256();size=0
# Requests strips Authorization when GitHub redirects to its artifact-storage host.
with requests.Session()as session:
    redirect=session.get(artifact['archive_download_url'],headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json'},timeout=(20,30),allow_redirects=False)
    redirect.raise_for_status()
    signed_url=redirect.headers.get('Location')
    if not signed_url or not signed_url.startswith('https://'):raise SystemExit('Missing secure artifact download redirect')
    direct=requests.Session();direct.trust_env=False
    try:response=direct.get(signed_url,timeout=(10,30),stream=True)
    except requests.RequestException:response=session.get(signed_url,timeout=(20,30),stream=True)
    started=time.monotonic()
    with response:
        response.raise_for_status()
        with target.open('wb')as output:
            for block in response.iter_content(65536):
                output.write(block);digest.update(block);size+=len(block)
                if size%(1024*1024)==0:print(f'Downloaded {size} bytes',flush=True)
                if time.monotonic()-started>300:raise SystemExit('Artifact download exceeded five minutes; retry the download')
if 'sha256:'+digest.hexdigest()!=artifact['digest']:raise SystemExit('GitHub artifact checksum does not match')
with zipfile.ZipFile(target)as archive:
    for item in archive.infolist():
        if not(args.output/item.filename).resolve().is_relative_to(args.output.resolve()):raise SystemExit('Invalid archive path')
    archive.extractall(args.output)
for ipa in list(args.output.rglob('*.ipa')):
    destination=args.output/ipa.name
    if ipa!=destination:ipa.replace(destination)
target.unlink()
(args.output/'BUILD.json').write_text(json.dumps({'repository':repo,'run':args.run,'source':run['head_sha'],'artifact_sha256':digest.hexdigest(),'artifact_bytes':size,'signed':False},indent=2)+'\n',encoding='utf-8')
print('Unsigned IPA downloaded and verified',flush=True)
