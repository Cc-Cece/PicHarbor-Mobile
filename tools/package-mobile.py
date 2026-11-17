"""Package the exact committed native sources and GPL proxy source; no local secrets."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import zipfile

root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'android/tools'))
from prepare_proxy import COMMIT
out=root/'delivery';out.mkdir(exist_ok=True)
head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
archive=out/('PicHarbor-Mobile-'+head[:12]+'-source.zip')
subprocess.run(['git','archive','--format=zip','--output='+str(archive),head],cwd=root,check=True)
source=root/'android/.runtime-cache/clash-src'
if subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip()!=COMMIT:
    raise SystemExit('Proxy source does not match the fixed commit')
if subprocess.check_output(['git','-C',str(source),'status','--porcelain'],text=True).strip():
    raise SystemExit('Proxy source is modified')
with zipfile.ZipFile(archive,'a',zipfile.ZIP_DEFLATED)as bundle:
    for name in subprocess.check_output(['git','-C',str(source),'ls-files'],text=True).splitlines():
        file=source/name
        if file.is_file():bundle.write(file,'third_party/clash/'+name)
    bundle.writestr('third_party/clash/SOURCE.json',json.dumps({'repository':'https://github.com/ProjectClash/Clash','commit':COMMIT}))
digest=hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix('.sha256').write_text(digest+'  '+archive.name+'\n',encoding='utf-8')
print(json.dumps({'archive':str(archive),'sha256':digest,'source_commit':head}))
