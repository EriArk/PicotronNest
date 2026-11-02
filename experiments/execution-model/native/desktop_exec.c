/* Original diagnostic launcher; purchased runtime bytes remain unchanged. */
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>

int main(void) {
    printf("PICONEST_DESKTOP_EXEC pid=%ld ppid=%ld uid=%ld\n",
           (long)getpid(), (long)getppid(), (long)getuid());
    if (fflush(stdout) != 0) return 70;
    if (setenv("LD_LIBRARY_PATH", "/lib", 1) || setenv("LD_PRELOAD", "/lib/libdesktop_trace.so:/lib/libnetwork_resolver.so", 1) ||
        setenv("SDL_VIDEODRIVER", "dummy", 1) || setenv("SDL_AUDIODRIVER", "dummy", 1) ||
        setenv("SDL_RENDER_DRIVER", "software", 1) || setenv("HOME", "/home", 1)) return 71;
    char *const args[] = {"/runtime/picotron", "-home", "/home", NULL};
    execv(args[0], args);
    printf("PICONEST_DESKTOP_EXEC_FAILED errno=%d\n", errno);
    return 72;
}
