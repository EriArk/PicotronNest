"""Build pinned HTTP(S) libcurl/TLS for the guest; never include an owner runtime."""
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path

from prepare_headless import HERE, ROOT, digest, fetch, regular_files, run_logged
from prepare_proot import deb_payload


def prepare(destination, llvm, manifest):
    lock = json.loads((HERE / 'network.lock.json').read_text())
    # Cache only this reproducible dependency build, not the Android/native adapters.
    key = hashlib.sha256((digest(Path(__file__)) + digest(HERE / 'network.lock.json')
                         + digest(HERE / 'headless.lock.json') + digest(llvm / 'clang.exe')).encode()).hexdigest()
    work = ROOT / '.local/network' / key
    work.mkdir(parents=True, exist_ok=True)
    receipt = work / 'built.json'
    if receipt.exists():
        outputs = json.loads(receipt.read_text())
        for name, expected in outputs.items():
            if digest(work / name) != expected:
                raise ValueError('Network build cache integrity mismatch: ' + name)
    else:
        for item in lock['inputs']:
            regular_files(fetch(item), work / 'source')
        headless = json.loads((HERE / 'headless.lock.json').read_text())
        sysroot = work / 'sysroot'
        for item in headless['inputs']:
            if item['file'].endswith('.deb'):
                regular_files(deb_payload(fetch(item)), sysroot, ('usr/include/linux/netfilter',))
        lib = sysroot / 'lib/aarch64-linux-gnu'
        libraries = ' '.join((lib / name).as_posix() for name in ('libc.so.6', 'libm.so.6', 'libdl.so.2', 'libpthread.so.0'))
        libraries += ' ' + (sysroot / 'usr/lib/aarch64-linux-gnu/libc_nonshared.a').as_posix()
        libraries += ' ' + (llvm.parent / 'lib/clang/19/lib/linux/libclang_rt.builtins-aarch64-android.a').as_posix()
        toolchain = work / 'toolchain.cmake'
        toolchain.write_text(f'''set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER "{llvm.as_posix()}/clang.exe")
set(CMAKE_C_COMPILER_TARGET aarch64-linux-gnu)
set(CMAKE_SYSROOT "{sysroot.as_posix()}")
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_C_FLAGS_INIT "-fPIC -mno-outline-atomics")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "-nostdlib -fuse-ld=lld -Wl,-z,defs {libraries}")
set(CMAKE_EXE_LINKER_FLAGS_INIT "-nostdlib -fuse-ld=lld {sysroot.as_posix()}/usr/lib/aarch64-linux-gnu/Scrt1.o {sysroot.as_posix()}/usr/lib/aarch64-linux-gnu/crti.o {libraries} {sysroot.as_posix()}/usr/lib/aarch64-linux-gnu/crtn.o -Wl,--dynamic-linker=/lib/ld-linux-aarch64.so.1")
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)
''', encoding='utf-8')
        common = ['-G', 'Ninja', '-DCMAKE_TOOLCHAIN_FILE=' + str(toolchain), '-DCMAKE_BUILD_TYPE=Release']
        tls = work / 'source/mbedtls-3.6.7'
        tls_build = work / 'tls-build'
        run_logged(['cmake', '-S', tls, '-B', tls_build, *common, '-DENABLE_TESTING=OFF',
                    '-DENABLE_PROGRAMS=OFF', '-DUSE_STATIC_MBEDTLS_LIBRARY=ON',
                    '-DUSE_SHARED_MBEDTLS_LIBRARY=OFF'], work / 'tls-configure.log')
        run_logged(['cmake', '--build', tls_build, '--parallel', '8'], work / 'tls-build.log')
        curl = work / 'source/curl-8.23.0'
        curl_build = work / 'curl-build'
        options = {'BUILD_CURL_EXE':'OFF', 'BUILD_TESTING':'OFF', 'BUILD_SHARED_LIBS':'ON',
                   'BUILD_STATIC_LIBS':'OFF', 'BUILD_LIBCURL_DOCS':'OFF', 'BUILD_MISC_DOCS':'OFF',
                   'ENABLE_CURL_MANUAL':'OFF', 'CURL_USE_MBEDTLS':'ON', 'CURL_USE_OPENSSL':'OFF',
                   'CURL_USE_LIBPSL':'OFF', 'CURL_USE_LIBSSH2':'OFF', 'CURL_USE_LIBSSH':'OFF',
                   'USE_LIBIDN2':'OFF', 'CURL_USE_GSSAPI':'OFF', 'CURL_ZLIB':'OFF',
                   'CURL_USE_PKGCONFIG':'OFF', 'CURL_USE_CMAKECONFIG':'OFF',
                   'CURL_BROTLI':'OFF', 'CURL_ZSTD':'OFF', 'USE_NGHTTP2':'OFF',
                   'ENABLE_ARES':'OFF', 'ENABLE_THREADED_RESOLVER':'ON', 'CURL_CA_FALLBACK':'OFF',
                   'CURL_CA_BUNDLE':'/etc/ssl/certs/ca-certificates.crt', 'CURL_CA_PATH':'none',
                   'MBEDTLS_INCLUDE_DIR':str(tls / 'include'),
                   'MBEDTLS_LIBRARY':str(tls_build / 'library/libmbedtls.a'),
                   'MBEDX509_LIBRARY':str(tls_build / 'library/libmbedx509.a'),
                   'MBEDCRYPTO_LIBRARY':str(tls_build / 'library/libmbedcrypto.a')}
        for protocol in ('DICT','FILE','FTP','GOPHER','IMAP','IPFS','LDAP','LDAPS','MQTT',
                         'POP3','RTSP','SMTP','TELNET','TFTP','WEBSOCKETS'):
            options['CURL_DISABLE_'+protocol]='ON'
        run_logged(['cmake', '-S', curl, '-B', curl_build, *common,
                    *[f'-D{k}={v}' for k,v in options.items()]], work / 'curl-configure.log')
        run_logged(['cmake', '--build', curl_build, '--target', 'libcurl_shared', '--parallel', '8'], work / 'curl-build.log')
        output = next(p for p in (curl_build / 'lib').glob('libcurl.so.*') if p.is_file() and not p.is_symlink())
        shutil.copyfile(output, work / 'libcurl.so.4')
        shutil.copyfile(curl / 'COPYING', work / 'CURL-COPYING.txt')
        # Preserve all license choices/notices in the official release, including bundled crypto code.
        license_files = [p for p in tls.rglob('*') if p.is_file() and
                         (p.name.startswith(('LICENSE', 'COPYING', 'NOTICE')) or p.parent.name == 'LICENSES')]
        (work / 'MBEDTLS-NOTICES.txt').write_text('\n\n'.join(str(p.relative_to(tls))+'\n'+p.read_text(encoding='utf-8')
                                                       for p in sorted(license_files)), encoding='utf-8')
        outputs = {name:digest(work / name) for name in ('libcurl.so.4','CURL-COPYING.txt','MBEDTLS-NOTICES.txt')}
        receipt.write_text(json.dumps(outputs, indent=2)+'\n', encoding='utf-8')
    elf = subprocess.check_output([str(llvm / 'llvm-readelf.exe'), '-h', '-d', str(work / 'libcurl.so.4')], text=True)
    needed = re.findall(r'\(NEEDED\).*?\[([^\]]+)\]', elf)
    if 'AArch64' not in elf or any('lib/'+n not in manifest['files'] for n in needed):
        raise ValueError('Network dependency closure is incomplete')
    files = {}
    for name in ('libcurl.so', 'libcurl.so.4'):
        target = destination / 'assets/headless/lib' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(work / 'libcurl.so.4', target)
        manifest['files']['lib/'+name] = {'sha256':digest(target), 'size':target.stat().st_size, 'needed':needed}
        files['assets/headless/lib/'+name] = target
    for name in ('CURL-COPYING.txt','MBEDTLS-NOTICES.txt'):
        files['assets/licenses/'+name] = work / name
    manifest['network_inputs'] = lock
    return files
