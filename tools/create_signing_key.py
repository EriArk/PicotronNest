"""Create a local release identity once; never overwrite a key or print credentials."""
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]


if __name__ == '__main__':
    folder = ROOT / '.local/release/signing'
    folder.mkdir(parents=True, exist_ok=True)
    key, password = folder / 'picotronnest.jks', folder / 'password.txt'
    if key.exists() or password.exists():
        raise SystemExit('Signing files already exist; preserve and reuse them. Nothing overwritten.')
    with password.open('x', encoding='utf-8') as out:
        out.write(secrets.token_urlsafe(48)+'\n')
    subprocess.run([r'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe', '-genkeypair',
                    '-keystore', str(key), '-storetype', 'JKS', '-storepass:file', str(password),
                    '-keypass:file', str(password), '-alias', 'picotronnest', '-keyalg', 'RSA',
                    '-keysize', '3072', '-validity', '10000', '-dname', 'CN=PicotronNest'], check=True)
    print('Created local signing identity under .local/release/signing. Back up both files privately; never publish them.')
