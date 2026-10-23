/* Owned glibc launch fixture. exec preserves this PID; Lua proves runtime execution. */
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <gnu/libc-version.h>
#include "SDL.h"

int main(void) {
    SDL_version version;
    SDL_GetVersion(&version);
    printf("PICONEST_GLIBC glibc=%s sdl=%u.%u.%u\n", gnu_get_libc_version(),
           version.major, version.minor, version.patch);
    printf("PICONEST_EXEC_PICOTRON pid=%ld ppid=%ld uid=%ld\n",
           (long)getpid(), (long)getppid(), (long)getuid());
    if (fflush(stdout) != 0) return 70;
    if (setenv("LD_LIBRARY_PATH", "/lib", 1) != 0 ||
        setenv("SDL_VIDEODRIVER", "dummy", 1) != 0 ||
        setenv("SDL_AUDIODRIVER", "dummy", 1) != 0 ||
        setenv("HOME", "/home", 1) != 0) return 71;
    char *const args[] = {"/runtime/picotron", "-home", "/home", "-x", "/probe/check.lua", NULL};
    execv(args[0], args);
    printf("PICONEST_EXEC_FAILED errno=%d\n", errno);
    return 72;
}
