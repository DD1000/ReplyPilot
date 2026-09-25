"""Package the verified build without local secrets or development caches."""
from pathlib import Path
import hashlib, shutil, zipfile
root=Path(__file__).resolve().parents[1]
dist=root/'dist';dist.mkdir(exist_ok=True)
version='0.12.2'
apk=dist/f'Reply-Pilot-{version}.apk'
shutil.copy2(root/'app/build/outputs/apk/release/app-release.apk',apk)
shutil.copy2(apk,dist/'Reply-Pilot-preview.apk')
install=dist/f'Reply-Pilot-{version}-install.zip'
with zipfile.ZipFile(install,'w',zipfile.ZIP_DEFLATED) as archive:
    archive.write(apk,apk.name)
shutil.copy2(install,dist/'Reply-Pilot-install.zip')
shutil.copy2(root/'INSTALL.md',dist/'INSTALL.md')
shutil.copy2(root/'VALIDATION.md',dist/'VALIDATION.md')
shutil.copy2(root/'SECURITY-AUDIT.md',dist/'SECURITY-AUDIT.md')
excluded={'dist','build','.gradle','.git','.idea','node_modules','__pycache__','local.properties'}
def include(path):
    relative=path.relative_to(root)
    return not any(part in excluded or part.startswith('.local-') or part.startswith('.env') for part in relative.parts) and path.suffix not in {'.keystore','.jks','.pem','.key','.p12','.log','.pyc'}
source=dist/'Reply-Pilot-source.zip'
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob('*')):
        if path.is_file() and not path.is_symlink() and include(path):archive.write(path,Path('reply-pilot')/path.relative_to(root))
service=dist/'Reply-Pilot-service.zip'
with zipfile.ZipFile(service,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in sorted((root/'relay').rglob('*')):
        if path.is_file() and not path.is_symlink() and include(path):archive.write(path,Path('relay')/path.relative_to(root/'relay'))
files=[apk,install,dist/'Reply-Pilot-preview.apk',dist/'Reply-Pilot-install.zip',source,service]
(dist/'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in files))
with zipfile.ZipFile(source) as archive:
    assert not any(any(t in name for t in ['.local-secrets/','.local-signing/','local.properties','.keystore','.env']) for name in archive.namelist())
    assert archive.testzip() is None
with zipfile.ZipFile(install) as archive:
    assert archive.namelist()==[apk.name]
    assert hashlib.sha256(archive.read(apk.name)).digest()==hashlib.sha256(apk.read_bytes()).digest()
print('\n'.join(f'{p.name}: {p.stat().st_size:,} bytes' for p in files))
print('Archives verified; private keys, local secret files and build caches excluded.')
