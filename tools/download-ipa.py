"""Download and verify an unsigned IPA from a successful mobile CI run."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import subprocess
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
# Resolve a fresh signed URL without forwarding the GitHub token to storage.
with requests.Session()as session:
    redirect=session.get(artifact['archive_download_url'],headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json'},timeout=(20,30),allow_redirects=False)
    redirect.raise_for_status()
    signed_url=redirect.headers.get('Location')
    if not signed_url or not signed_url.startswith('https://'):raise SystemExit('Missing secure artifact download redirect')
def get_range(start,end):
    client=requests.Session();client.trust_env=False
    try:response=client.get(signed_url,headers={'Range':f'bytes={start}-{end}'},timeout=(10,45),stream=True)
    except requests.RequestException:
        client.close();client=requests.Session()
        response=client.get(signed_url,headers={'Range':f'bytes={start}-{end}'},timeout=(20,45),stream=True)
    if response.status_code!=206:
        response.close();client.close();raise RuntimeError('Artifact storage did not honor the byte range')
    return client,response
client,probe=get_range(0,0)
try:
    total=int(probe.headers['Content-Range'].split('/')[-1])
    if probe.content!=b'P':raise SystemExit('Artifact is not the expected ZIP stream')
finally:probe.close();client.close()
count=8;part_size=(total+count-1)//count
def download_part(index):
    start=index*part_size;end=min(total-1,start+part_size-1)
    part=args.output/f'artifact.zip.part.{index}'
    for attempt in range(2):
        try:
            client,response=get_range(start,end)
            try:
                if response.headers.get('Content-Range')!=f'bytes {start}-{end}/{total}':raise RuntimeError('Incorrect artifact byte range')
                with response,part.open('wb')as output:
                    for block in response.iter_content(65536):output.write(block)
                if part.stat().st_size!=end-start+1:raise RuntimeError('Incomplete artifact part')
            finally:response.close();client.close()
            print(f'Downloaded part {index+1}/{count}',flush=True)
            return part
        except requests.RequestException:
            if attempt:raise
with ThreadPoolExecutor(max_workers=count)as pool:parts=list(pool.map(download_part,range(count)))
with target.open('wb')as output:
    for part in parts:
        with part.open('rb')as source:
            while block:=source.read(1024*1024):output.write(block);digest.update(block);size+=len(block)
if 'sha256:'+digest.hexdigest()!=artifact['digest']:raise SystemExit('GitHub artifact checksum does not match')
with zipfile.ZipFile(target)as archive:
    for item in archive.infolist():
        if not(args.output/item.filename).resolve().is_relative_to(args.output.resolve()):raise SystemExit('Invalid archive path')
    archive.extractall(args.output)
for ipa in list(args.output.rglob('*.ipa')):
    destination=args.output/ipa.name
    if ipa!=destination:ipa.replace(destination)
target.unlink()
for part in parts:part.unlink()
(args.output/'BUILD.json').write_text(json.dumps({'repository':repo,'run':args.run,'source':run['head_sha'],'artifact_sha256':digest.hexdigest(),'artifact_bytes':size,'signed':False},indent=2)+'\n',encoding='utf-8')
print('Unsigned IPA downloaded and verified',flush=True)
