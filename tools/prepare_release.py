"""Prepare local release attachments from a reviewed candidate. Never publish or tag."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile
from audit_repository import ROOT, audit


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def pinned(item, cache):
    path = cache / item['file']
    if not path.is_file():
        cache.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(item['url'], timeout=60) as response:
            data = response.read(64*1024*1024+1)
        if len(data)>64*1024*1024 or hashlib.sha256(data).hexdigest()!=item['sha256']:
            raise ValueError('Source download hash/size mismatch: '+item['file'])
        path.write_bytes(data)
    if digest(path)!=item['sha256']:
        raise ValueError('Cached source hash mismatch: '+item['file'])
    return path


def main():
    if subprocess.check_output(['git','status','--porcelain'],cwd=ROOT).strip():
        raise SystemExit('Commit and review the worktree before preparing release attachments.')
    checked=audit()
    if checked['findings']:
        raise SystemExit(json.dumps(checked,indent=2))
    candidate=ROOT/'.local/release/candidate'
    metadata=json.loads((candidate/'build.json').read_text())
    product=json.loads((ROOT/'packaging/candidate.json').read_text())
    if metadata.get('candidate')!=product or metadata.get('debuggable') is not False:
        raise SystemExit('Expected a matching non-debuggable product candidate.')
    for name, expected in metadata['source_sha256'].items():
        if digest(ROOT/name)!=expected:
            raise SystemExit('Build is stale: '+name)
    version=product['version_name']
    apk=candidate/f'PicotronNest-{version}-arm64.apk'
    if digest(apk)!=metadata['apk_sha256']:
        raise SystemExit('Candidate APK hash mismatch.')
    contract=metadata['runtime_contract']
    forbidden={contract['archive_sha256'],*(v['sha256'] for v in contract['files'].values())}
    with zipfile.ZipFile(apk) as archive:
        payloads={name for name in archive.namelist() if not name.startswith('META-INF/')}
        if payloads!=set(metadata['apk_payloads']):
            raise SystemExit('Candidate payload allowlist mismatch.')
        for name in archive.namelist():
            if hashlib.sha256(archive.read(name)).hexdigest() in forbidden:
                raise SystemExit('Proprietary bytes in candidate: '+name)
    output=ROOT/'.local/release'/version
    output.mkdir(parents=True,exist_ok=True)
    expected={apk.name, 'build-manifest.json', 'THIRD_PARTY_NOTICES.md', 'PROJECT_NOTICE.txt',
              'RELEASE_NOTES.md', 'TESTING.md', f'PicotronNest-{version}-source.zip',
              f'PicotronNest-{version}-dependency-sources.zip', f'PicotronNest-{version}-screenshots.zip',
              'repository-audit.json', 'SHA256SUMS'}
    if (ROOT/'LICENSE').is_file():
        expected.add('LICENSE')
    if {p.name for p in output.iterdir()}-expected:
        raise SystemExit('Unexpected existing files in release directory; inspect them before continuing.')
    shutil.copyfile(apk,output/apk.name)
    shutil.copyfile(candidate/'build.json',output/'build-manifest.json')
    for name in ('THIRD_PARTY_NOTICES.md', *(['LICENSE'] if (ROOT/'LICENSE').is_file() else [])):
        shutil.copyfile(ROOT/name,output/name)
    shutil.copyfile(ROOT/'packaging/PROJECT_NOTICE.txt',output/'PROJECT_NOTICE.txt')
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    notes=(ROOT/f'docs/releases/{version}.md').read_text(encoding='utf-8')
    notes=notes.replace('(../TESTING.md)',f'(https://github.com/EriArk/PicotronNest/blob/{commit}/docs/TESTING.md)')
    (output/'RELEASE_NOTES.md').write_text(notes,encoding='utf-8')
    testing=(ROOT/'docs/TESTING.md').read_text(encoding='utf-8')
    testing=testing.replace('(ACCEPTANCE.md)',f'(https://github.com/EriArk/PicotronNest/blob/{commit}/docs/ACCEPTANCE.md)')
    (output/'TESTING.md').write_text(testing,encoding='utf-8')
    subprocess.run(['git','archive','--format=zip',f'--prefix=PicotronNest-{version}/',
                    '-o',str(output/f'PicotronNest-{version}-source.zip'),'HEAD'],cwd=ROOT,check=True)
    # Export only the reviewed gallery tracked at this exact commit.
    subprocess.run(['git','archive','--format=zip',
                    '-o',str(output/f'PicotronNest-{version}-screenshots.zip'),'HEAD','docs/screenshots'],cwd=ROOT,check=True)
    proot=json.loads((ROOT/'experiments/execution-model/proot.lock.json').read_text())
    headless=json.loads((ROOT/'experiments/execution-model/headless.lock.json').read_text())
    recipe=json.loads((ROOT/'packaging/sources.lock.json').read_text())
    network=json.loads((ROOT/'experiments/execution-model/network.lock.json').read_text())
    inputs=[*( (item,ROOT/'.local/proot/downloads') for item in proot['sources']+proot['recipes']),
            *( (item,ROOT/'.local/headless/downloads') for item in headless['inputs'] if not item['file'].endswith('.deb')),
            (recipe,ROOT/'.local/release/downloads'),
            *((item,ROOT/'.local/headless/downloads') for item in network['inputs'])]
    with zipfile.ZipFile(output/f'PicotronNest-{version}-dependency-sources.zip','w',zipfile.ZIP_DEFLATED) as bundle:
        for item,cache in inputs:
            bundle.write(pinned(item,cache),'upstream/'+item['file'])
        for name in ('proot.lock.json','headless.lock.json','network.lock.json','prepare_proot.py','prepare_headless.py','prepare_desktop.py','prepare_network.py','build.py'):
            bundle.write(ROOT/'experiments/execution-model'/name,'picotronnest-build/'+name)
        bundle.write(ROOT/'THIRD_PARTY_NOTICES.md','THIRD_PARTY_NOTICES.md')
    checked['commit']=commit
    (output/'repository-audit.json').write_text(json.dumps(checked,indent=2)+'\n',encoding='utf-8')
    if {p.name for p in output.iterdir()}-expected:
        raise SystemExit('Unexpected release attachment.')
    files=sorted(p for p in output.iterdir() if p.name!='SHA256SUMS')
    (output/'SHA256SUMS').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in files),encoding='utf-8')
    print('Prepared local attachments: '+str(output))
    print('No tag or GitHub Release created. Keep the APK, both source archives and notices together.')


if __name__=='__main__':
    main()
