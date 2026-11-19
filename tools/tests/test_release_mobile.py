"""Offline release policy and artifact assembly checks, without real signing keys."""
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import plistlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec=importlib.util.spec_from_file_location('release_mobile',Path(__file__).resolve().parents[1]/'release_mobile.py')
release=importlib.util.module_from_spec(spec);spec.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.override=patch.object(release,'ROOT',self.root);self.override.start();self.addCleanup(self.override.stop)
        # Never allow fixture preflights to write to the real workflow's output
        # file or change its publish flag when tests run inside GitHub Actions.
        environment=patch.dict(os.environ,{'GITHUB_OUTPUT':str(self.root/'fixture-output')})
        environment.start();self.addCleanup(environment.stop)
        self.write('android/app/build.gradle.kts','versionName = "2.0.0-alpha.17"\nversionCode = 17\n')
        self.write('ios/VERSION','0.2.42\n')
        self.write('ios/project.yml','MARKETING_VERSION: "0.2.42"\nCURRENT_PROJECT_VERSION: "46"\n')
        self.write('android/gpmc.lock.json','{}');self.write('ios/gpmc.lock.json','{}')
        self.write('android/tools/release-signing.sha256','a'*64)
        for command in [('init','-b','main'),('config','core.autocrlf','false'),('config','core.eol','lf'),('config','user.email','test@example.invalid'),('config','user.name','Release Test'),('add','.'),('commit','-m','fixture')]:
            self.git(*command)
        self.head=self.git('rev-parse','HEAD')
        self.git('update-ref','refs/remotes/origin/main',self.head)
        self.git('tag','v1.0.0')

    def git(self,*args):
        return subprocess.check_output(['git',*args],cwd=self.root,text=True,stderr=subprocess.DEVNULL).strip()

    def write(self,name,text):
        path=self.root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8');return path

    def inputs(self):
        folder=self.root/'inputs';folder.mkdir()
        apk=folder/'app-release.apk';apk.write_bytes(b'fixture APK; signature verified separately by the builder')
        ipa=folder/'app.ipa'
        with zipfile.ZipFile(ipa,'w') as archive:
            archive.writestr('Payload/PicHarbor.app/Info.plist',plistlib.dumps({'CFBundleShortVersionString':'0.2.42','CFBundleVersion':'46'}))
        release.record('android',apk,folder/'BUILD-android.json');release.record('ios',ipa,folder/'BUILD-ios.json')
        source=folder/f'PicHarbor-Mobile-{self.head[:12]}-source.zip'
        self.git('archive','--format=zip','--output='+str(source),'HEAD')
        with zipfile.ZipFile(source,'a') as archive:archive.writestr('third_party/clash/SOURCE.json','{}')
        source.with_suffix('.sha256').write_text(release.digest(source)+'  '+source.name+'\n')
        return folder,source

    def assemble(self,folder):
        with contextlib.redirect_stdout(io.StringIO()):release.assemble(folder,self.root/'output','v1.0.0')

    def test_version_tags_and_prereleases(self):
        for tag in ['v1.0.0','v2.0.0-beta.1','v0.2.42-rc.2']:
            self.assertEqual(release.validate_tag(tag),tag)
        for tag in ['main','v../x','v1\ninject','v1.0','v1.0.0;echo']:
            with self.assertRaises(ValueError):release.validate_tag(tag)

    def test_main_tag_can_publish(self):
        with patch.object(release,'release_absent') as absence,contextlib.redirect_stdout(io.StringIO()):
            release.preflight('v1.0.0',True)
        absence.assert_called_once_with('v1.0.0')

    def test_feature_commit_cannot_publish(self):
        self.write('unmerged.py','pass');self.git('add','.');self.git('commit','-m','not on main');self.git('tag','v1.0.1')
        with patch.object(release,'release_absent') as absence:
            with self.assertRaisesRegex(ValueError,'not on origin/main'):release.preflight('v1.0.1',True)
        absence.assert_not_called()

    def test_existing_tag_must_match_checkout(self):
        self.write('later.py','pass');self.git('add','.');self.git('commit','-m','later')
        with self.assertRaisesRegex(ValueError,'exact checkout'):release.preflight('v1.0.0',True)

    def test_dry_run_does_not_publish_or_need_a_tag(self):
        with patch.object(release,'release_absent') as absence,contextlib.redirect_stdout(io.StringIO()):release.preflight('',False)
        absence.assert_not_called()

    def test_fixture_output_is_confined_to_the_test_directory(self):
        with contextlib.redirect_stdout(io.StringIO()):release.preflight('',False)
        output=Path(os.environ['GITHUB_OUTPUT'])
        self.assertEqual(output.parent,self.root)
        self.assertIn('publish=false',output.read_text())

    def test_release_absence_fails_closed(self):
        with patch.dict('os.environ',{'GITHUB_REPOSITORY':'test/project'}):
            for status,message,error in [(0,'',ValueError),(1,'HTTP 403',RuntimeError)]:
                with patch.object(release.subprocess,'run',return_value=subprocess.CompletedProcess([],status,'',message)):
                    with self.assertRaises(error):release.release_absent('v1.0.0')
            with patch.object(release.subprocess,'run',return_value=subprocess.CompletedProcess([],1,'','HTTP 404')):
                release.release_absent('v1.0.0')

    def test_versions_must_be_consistent(self):
        self.write('ios/VERSION','9.0.0')
        with self.assertRaises(ValueError):release.versions()

    def test_assemble_preserves_native_versions_and_checksums(self):
        folder,_=self.inputs();self.assemble(folder)
        output=self.root/'output'
        manifest=json.loads((output/'MANIFEST.json').read_text())
        self.assertEqual(manifest['source'],self.head)
        self.assertEqual(manifest['versions']['android']['version'],'2.0.0-alpha.17')
        self.assertTrue((output/'PicHarbor-iOS-v1.0.0-unsigned.ipa').exists())
        for line in (output/'SHA256SUMS.txt').read_text().splitlines():
            digest,name=line.split('  ');self.assertEqual(digest,release.digest(output/name))

    def test_tampered_package_is_rejected(self):
        folder,_=self.inputs();(folder/'app-release.apk').write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError,'checksum mismatch'):self.assemble(folder)

    def test_other_source_build_is_rejected(self):
        folder,_=self.inputs();path=folder/'BUILD-ios.json';metadata=json.loads(path.read_text());metadata['source']='b'*40;path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError,'does not match this source'):self.assemble(folder)

    def test_wrong_signer_is_rejected(self):
        folder,_=self.inputs();path=folder/'BUILD-android.json';metadata=json.loads(path.read_text());metadata['certificate_sha256']='b'*64;path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError,'existing release signing identity'):self.assemble(folder)

    def test_private_document_in_source_is_rejected(self):
        folder,source=self.inputs()
        with zipfile.ZipFile(source,'a') as archive:archive.writestr('local-docs/private.md','private')
        source.with_suffix('.sha256').write_text(release.digest(source))
        with self.assertRaisesRegex(ValueError,'local documents'):self.assemble(folder)

    def test_stale_source_contents_are_rejected(self):
        folder,source=self.inputs()
        with self.assertWarns(UserWarning),zipfile.ZipFile(source,'a') as archive:archive.writestr('ios/project.yml','outdated source')
        source.with_suffix('.sha256').write_text(release.digest(source))
        with self.assertRaisesRegex(ValueError,'does not match the release commit'):self.assemble(folder)


if __name__=='__main__':unittest.main()
