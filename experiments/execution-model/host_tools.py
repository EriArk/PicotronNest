"""Resolve pinned Android build tools on supported Windows/Linux x86-64 hosts."""
import os
from pathlib import Path
import platform
import shutil
import sys


def executable(directory, name, host=None):
    return Path(directory) / (name + ('.exe' if (host or sys.platform) == 'win32' else ''))


def ndk_host(host=None, machine=None):
    host, machine = host or sys.platform, (machine or platform.machine()).lower()
    if host not in ('win32', 'linux') or machine not in ('amd64', 'x86_64'):
        raise ValueError('This build supports Linux/Windows x86-64 hosts; select a supported host toolchain.')
    return 'windows-x86_64' if host == 'win32' else 'linux-x86_64'


def default_sdk():
    configured = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if configured:
        return Path(configured)
    if sys.platform == 'win32' and os.environ.get('LOCALAPPDATA'):
        return Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'
    return Path.home() / 'Android/Sdk'


def default_jdk():
    if os.environ.get('JAVA_HOME'):
        return Path(os.environ['JAVA_HOME'])
    compiler = shutil.which('javac')
    if compiler:
        return Path(compiler).resolve().parent.parent
    if sys.platform == 'win32':
        return Path(r'C:\Program Files\Android\Android Studio\jbr')
    return None
