"""Check both snapshots, or update both explicitly from the canonical checkout."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',type=Path,help='Canonical gpmc checkout; omitted for offline read-only checks')
args=parser.parse_args()
for platform in ('android','ios'):
    command=[sys.executable,str(root/platform/'tools/sync_gpmc.py')]
    command+=['--source',str(args.source.resolve())] if args.source else ['--check']
    subprocess.run(command,check=True,cwd=root/platform)
locks=[json.loads((root/p/'gpmc.lock.json').read_text(encoding='utf-8')) for p in ('android','ios')]
if locks[0]['source_tree_sha256']!=locks[1]['source_tree_sha256']:
    raise SystemExit('Client business-core snapshots differ')
print('Both clients use the same canonical core:',locks[0]['source_tree_sha256'])
