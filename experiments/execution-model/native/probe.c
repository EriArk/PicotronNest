/* Original PicotronNest diagnostic fixture. Never loads or patches Picotron. */
#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static int wait_child(pid_t child, int *status) {
    pid_t result;
    do { result = waitpid(child, status, 0); } while (result < 0 && errno == EINTR);
    return result == child ? 0 : -1;
}

static int ptrace_probe(void) {
    pid_t parent = getpid();
    pid_t child = fork();
    if (child < 0) { perror("fork"); return 20; }
    if (child == 0) {
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) != 0 || getppid() != parent) _exit(21);
        alarm(3);
        if (ptrace(PTRACE_TRACEME, 0, NULL, NULL) != 0) { perror("ptrace_traceme"); _exit(22); }
        raise(SIGSTOP);
        _exit(37);
    }
    int status;
    if (wait_child(child, &status) != 0) { perror("waitpid"); return 23; }
    if (!WIFSTOPPED(status) || WSTOPSIG(status) != SIGSTOP) {
        fprintf(stderr, "unexpected_trace_stop=%d\n", status);
        return 24;
    }
    if (ptrace(PTRACE_SETOPTIONS, child, NULL, (void *) PTRACE_O_EXITKILL) != 0
            || ptrace(PTRACE_CONT, child, NULL, NULL) != 0) {
        perror("ptrace_continue");
        kill(child, SIGKILL);
        wait_child(child, &status);
        return 25;
    }
    if (wait_child(child, &status) != 0) { perror("waitpid"); return 26; }
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 37) {
        fprintf(stderr, "unexpected_trace_exit=%d\n", status);
        return 27;
    }
    puts("PICONEST_PTRACE_OK child_exit=37");
    return 0;
}

int main(int argc, char **argv) {
    setvbuf(stdout, NULL, _IONBF, 0);
    int hanging = argc == 2 && strcmp(argv[1], "hang") == 0;
    alarm(hanging ? 10 : 4); /* Allow bounded startup before the host's hang deadline. */
    pid_t parent = getppid();
    if (prctl(PR_SET_PDEATHSIG, SIGKILL) != 0 || getppid() != parent || parent == 1) return 10;
    printf("PICONEST_EXEC_OK pid=%d ppid=%d uid=%d\n", getpid(), parent, getuid());
    char context[256] = {0};
    FILE *file = fopen("/proc/self/attr/current", "r");
    if (file) {
        if (fgets(context, sizeof(context), file)) printf("selinux=%s\n", context);
        fclose(file);
    }
    printf("seccomp=%d\n", prctl(PR_GET_SECCOMP, 0, 0, 0, 0));
    if (argc == 2 && strcmp(argv[1], "ptrace") == 0) return ptrace_probe();
    if (hanging) { puts("PICONEST_HANG_READY"); for (;;) pause(); }
    if (argc == 2 && strcmp(argv[1], "identity") == 0) return 0;
    fputs("unsupported probe mode\n", stderr);
    return 2;
}
