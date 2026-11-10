"""Reproducible, marked modification of the pinned SDL dummy output backend."""
from host_tools import executable
from pathlib import Path
import json

HERE = Path(__file__).resolve().parent


def identity():
    from prepare_headless import digest
    return {name: digest(HERE / name) for name in
            ('host_tools.py', 'patch_audio_clock.py', 'native/audio_clock.h', 'native/dummy_audio_clock.h')}


def build(source, sysroot, destination, llvm):
    from prepare_headless import DISABLED, run_logged
    patch(source)
    lib = sysroot / 'lib/aarch64-linux-gnu'
    libraries = ' '.join((lib / name).as_posix() for name in ('libc.so.6', 'libm.so.6', 'libdl.so.2', 'libpthread.so.0'))
    libraries += ' ' + (sysroot / 'usr/lib/aarch64-linux-gnu/libc_nonshared.a').as_posix()
    toolchain = destination / 'audio-toolchain.cmake'
    toolchain.write_text(f'''set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER "{executable(llvm, 'clang').as_posix()}")
set(CMAKE_C_COMPILER_TARGET aarch64-linux-gnu)
set(CMAKE_SYSROOT "{sysroot.as_posix()}")
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
set(CMAKE_C_FLAGS_INIT "-fPIC -mno-outline-atomics")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "-nostdlib -fuse-ld=lld -Wl,-z,defs {libraries}")
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)
''')
    output = destination / 'audio-sdl-build'
    run_logged(['cmake', '-S', source, '-B', output, '-G', 'Ninja',
                '-DCMAKE_TOOLCHAIN_FILE=' + str(toolchain), '-DCMAKE_BUILD_TYPE=Release',
                '-DSDL_SHARED=ON', '-DSDL_JOYSTICK=ON', '-DSDL_HIDAPI=ON', '-DSDL_VIRTUAL_JOYSTICK=ON',
                *['-DSDL_' + name + '=OFF' for name in DISABLED]], destination / 'audio-configure.log')
    run_logged(['cmake', '--build', output, '--target', 'SDL2', '--parallel', '4'], destination / 'audio-build.log')
    return output / 'libSDL2-2.0.so.0.3200.10'


def patch(source):
    directory = source / 'src/audio/dummy'
    implementation = directory / 'SDL_dummyaudio.c'
    header = directory / 'SDL_dummyaudio.h'
    code, types = implementation.read_text(), header.read_text()
    old = '''static int DUMMYAUDIO_OpenDevice(_THIS, const char *devname)
{
    _this->hidden = (void *)0x1; /* just something non-NULL */

    return 0; /* always succeeds. */
}'''
    if code.count(old) != 1 or types.count('    Uint8 *mixbuf;') != 1:
        raise ValueError('Pinned SDL dummy backend changed; review the audio clock patch')
    code = code.replace(old, '/* Modified by PicotronNest: sample-clock output pacing. */\n#include "dummy_audio_clock.h"')
    code = code.replace('impl->OpenDevice = DUMMYAUDIO_OpenDevice;', '''impl->OpenDevice = PN_DUMMY_OpenDevice;
    impl->CloseDevice = PN_DUMMY_CloseDevice;
    impl->GetDeviceBuf = PN_DUMMY_GetDeviceBuf;
    impl->WaitDevice = PN_DUMMY_WaitDevice;''')
    types = types.replace('#include "../SDL_sysaudio.h"', '#include "../SDL_sysaudio.h"\n#include "audio_clock.h"')
    types = types.replace('    Uint8 *mixbuf;', '    /* Modified by PicotronNest: exact sample-count pacing. */\n    PNAudioClock clock;\n    Uint8 *mixbuf;')
    implementation.write_text(code)
    header.write_text(types)
    for name in ('audio_clock.h', 'dummy_audio_clock.h'):
        (directory / name).write_bytes((HERE / 'native' / name).read_bytes())
