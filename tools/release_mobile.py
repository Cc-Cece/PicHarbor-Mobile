"""Validate tag intent and assemble verified multi-platform release assets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TAG = re.compile(r"v\d+\.\d+\.\d+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def validate_tag(value):
    if not TAG.fullmatch(value):
        raise ValueError('Use a version tag such as v1.0.0 or v1.0.0-beta.1')
    return value


def versions():
    android = (ROOT/'android/app/build.gradle.kts').read_text(encoding='utf-8')
    ios = (ROOT/'ios/project.yml').read_text(encoding='utf-8')
    if re.search(r'MARKETING_VERSION:\s*"([^"]+)"', ios)[1] != (ROOT/'ios/VERSION').read_text().strip():
        raise ValueError('iOS VERSION and MARKETING_VERSION disagree')
    return {'android': {'version': re.search(r'versionName\s*=\s*"([^"]+)"', android)[1],
                        'build': int(re.search(r'versionCode\s*=\s*(\d+)', android)[1])},
            'ios': {'version': (ROOT/'ios/VERSION').read_text().strip(),
                    'build': re.search(r'CURRENT_PROJECT_VERSION:\s*"(\d+)"', ios)[1]}}


def release_absent(tag):
    result = subprocess.run(['gh', 'api', f"repos/{os.environ['GITHUB_REPOSITORY']}/releases/tags/{tag}"],
                            capture_output=True, text=True)
    if result.returncode == 0:
        raise ValueError('Release already exists; do not overwrite published assets')
    if 'HTTP 404' not in result.stderr:
        raise RuntimeError('Could not confirm Release absence; refusing publication')


def preflight(tag, publish):
    head = git('rev-parse', 'HEAD')
    tag = validate_tag(tag) if tag else f'preview-{head[:12]}'
    if publish:
        validate_tag(tag)
        if git('rev-parse', f'refs/tags/{tag}^{{commit}}') != head:
            raise ValueError('Tag must already exist and point to this exact checkout')
        if subprocess.run(['git', 'merge-base', '--is-ancestor', head, 'refs/remotes/origin/main'], cwd=ROOT).returncode:
            raise ValueError('Release commit is not on origin/main')
        release_absent(tag)
    if output := os.environ.get('GITHUB_OUTPUT'):
        with Path(output).open('a', encoding='utf-8') as stream:
            stream.write(f'tag={tag}\nsource={head}\npublish={str(publish).lower()}\n')
    print(f'PASS: {"publish" if publish else "build-only"} preflight: {tag}, source {head}')


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def record(platform, package, output):
    value = {'source': git('rev-parse', 'HEAD'), 'platform': platform, **versions()[platform],
             'file': package.name, 'sha256': digest(package)}
    if platform == 'android':
        value['signed'] = True
        value['certificate_sha256'] = (ROOT/'android/tools/release-signing.sha256').read_text().strip()
    else:
        value['signed'] = False
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8')


def one(folder, pattern):
    values = [path for path in folder.rglob(pattern) if path.is_file()]
    if len(values) != 1:
        raise ValueError(f'Expected one {pattern}; found {len(values)}')
    return values[0]


def assemble(artifacts, output, tag):
    if not (TAG.fullmatch(tag) or re.fullmatch(r'preview-[0-9a-f]{12}', tag)):
        raise ValueError('Invalid release asset tag')
    head, native = git('rev-parse', 'HEAD'), versions()
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise ValueError('Release output must be empty')
    files = []
    for platform, pattern, suffix in [('android', '*.apk', 'arm64.apk'), ('ios', '*.ipa', 'unsigned.ipa')]:
        package = one(artifacts, pattern)
        metadata = json.loads(one(artifacts, f'BUILD-{platform}.json').read_text())
        if any(metadata.get(k) != v for k, v in {'source': head, 'platform': platform, **native[platform]}.items()):
            raise ValueError(f'{platform} build metadata does not match this source')
        if metadata['sha256'] != digest(package):
            raise ValueError(f'{platform} artifact checksum mismatch')
        if platform == 'android':
            pinned = (ROOT/'android/tools/release-signing.sha256').read_text().strip()
            if metadata.get('signed') is not True or metadata.get('certificate_sha256') != pinned:
                raise ValueError('Android must use the existing release signing identity')
        else:
            if metadata.get('signed') is not False:
                raise ValueError('iOS artifact must be explicitly marked unsigned')
            with zipfile.ZipFile(package) as archive:
                plist = next(p for p in archive.namelist() if p.startswith('Payload/') and p.count('/') == 2 and p.endswith('.app/Info.plist'))
                info = plistlib.loads(archive.read(plist))
                if info['CFBundleShortVersionString'] != native['ios']['version'] or str(info['CFBundleVersion']) != native['ios']['build']:
                    raise ValueError('IPA version does not match source')
        title = {'android': 'Android', 'ios': 'iOS'}[platform]
        destination = output/f'PicHarbor-{title}-{tag}-{suffix}'
        shutil.copyfile(package, destination)
        files.append(destination)
    source = one(artifacts, f'PicHarbor-Mobile-{head[:12]}-source.zip')
    expected = one(artifacts, source.stem+'.sha256').read_text().split()[0]
    if digest(source) != expected:
        raise ValueError('Source archive checksum mismatch')
    with zipfile.ZipFile(source) as archive:
        names = archive.namelist()
        if 'third_party/clash/SOURCE.json' not in names or any(p.startswith(('local-docs/', 'specs/', 'android/docs/', 'ios/docs/')) for p in names):
            raise ValueError('Source archive contains local documents or lacks proxy sources')
        for name in ['android/gpmc.lock.json','ios/gpmc.lock.json','android/app/build.gradle.kts','ios/project.yml']:
            # Git archives preserve committed bytes, not working-tree EOL conversions.
            if archive.read(name) != subprocess.check_output(['git', 'show', f'{head}:{name}'], cwd=ROOT):
                raise ValueError('Source archive does not match the release commit')
    destination = output/f'PicHarbor-Mobile-{tag}-source.zip'
    shutil.copyfile(source, destination)
    files.append(destination)
    manifest = {'tag': tag, 'source': head, 'versions': native, 'android_signed': True, 'ios_signed': False,
                'files': [{'name': p.name, 'bytes': p.stat().st_size, 'sha256': digest(p)} for p in files]}
    manifest_file = output/'MANIFEST.json'
    manifest_file.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    files.append(manifest_file)
    (output/'SHA256SUMS.txt').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in files), encoding='utf-8')
    notes = ['Android signed APK and iOS unsigned IPA from the same source commit.', '',
             f'- Release: `{tag}`', f'- Source: `{head}`',
             f"- Android: `{native['android']['version']}` (build {native['android']['build']}), ARM64",
             f"- iOS: `{native['ios']['version']}` (build {native['ios']['build']}), requires signing before installation",
             '- Source archive includes the pinned GPL proxy source.',
             '- Verify downloads using `SHA256SUMS.txt`.', '',
             'The release tag identifies the combined release; native platform versions are listed above.', '',
             'iOS setup: [English](https://github.com/Cc-Cece/PicHarbor-Mobile/blob/'+head+'/guides/ios-auto-renewal.md) / '
             '[简体中文](https://github.com/Cc-Cece/PicHarbor-Mobile/blob/'+head+'/guides/ios-auto-renewal.zh-CN.md).']
    (output.parent/'release-notes.md').write_text('\n'.join(notes)+'\n', encoding='utf-8')
    print(json.dumps(manifest, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    pre = commands.add_parser('preflight'); pre.add_argument('--tag', default=''); pre.add_argument('--publish', action='store_true')
    rec = commands.add_parser('record'); rec.add_argument('--platform', choices=['android','ios'], required=True)
    rec.add_argument('--package', type=Path, required=True); rec.add_argument('--output', type=Path, required=True)
    pack = commands.add_parser('assemble'); pack.add_argument('--tag', required=True)
    pack.add_argument('--artifacts', type=Path, required=True); pack.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'preflight': preflight(args.tag, args.publish)
    elif args.command == 'record': record(args.platform, args.package, args.output)
    else: assemble(args.artifacts, args.output, args.tag)


if __name__ == '__main__':
    main()
