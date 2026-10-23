/* Original libc-free Linux guest. The marker must come through the guest root. */
#if defined(__aarch64__)
enum { SYS_READ=63, SYS_WRITE=64, SYS_OPENAT=56, SYS_CLOSE=57, SYS_EXIT=93,
       SYS_GETPID=172, SYS_GETPPID=173, SYS_GETUID=174 };
static long call3(long n, long a, long b, long c) {
    register long r0 __asm__("x0")=a, r1 __asm__("x1")=b, r2 __asm__("x2")=c;
    register long r8 __asm__("x8")=n;
    __asm__ volatile("svc 0" : "+r"(r0) : "r"(r1), "r"(r2), "r"(r8) : "memory", "cc");
    return r0;
}
__asm__(".global _start\n_start:\nbl guest_main\n");
#elif defined(__x86_64__)
enum { SYS_READ=0, SYS_WRITE=1, SYS_OPENAT=257, SYS_CLOSE=3, SYS_EXIT=60,
       SYS_GETPID=39, SYS_GETPPID=110, SYS_GETUID=102 };
static long call3(long n, long a, long b, long c) {
    long result;
    __asm__ volatile("syscall" : "=a"(result) : "a"(n), "D"(a), "S"(b), "d"(c) : "rcx", "r11", "memory");
    return result;
}
__asm__(".global _start\n_start:\nandq $-16, %rsp\ncall guest_main\n");
#else
#error Unsupported guest architecture
#endif

static void put(const char *text) {
    long length=0;
    while (text[length]) length++;
    call3(SYS_WRITE, 1, (long)text, length);
}
static void number(long value) {
    char digits[32];
    int at=31;
    digits[at]=0;
    do { digits[--at]=(char)('0'+value%10); value/=10; } while (value);
    put(digits+at);
}
__attribute__((noreturn)) static void finish(long status) {
    call3(SYS_EXIT, status, 0, 0);
    for (;;) {}
}
__attribute__((used, noreturn)) void guest_main(void) {
    put("PICONEST_LINUX_GUEST pid="); number(call3(SYS_GETPID,0,0,0));
    put(" ppid="); number(call3(SYS_GETPPID,0,0,0));
    put(" uid="); number(call3(SYS_GETUID,0,0,0)); put("\n");
    long fd=call3(SYS_OPENAT,-100,(long)"/probe/marker.txt",0);
    if (fd<0) { put("PICONEST_MARKER_MISSING\n"); finish(41); }
    char content[128];
    long count=call3(SYS_READ,fd,(long)content,sizeof(content));
    call3(SYS_CLOSE,fd,0,0);
    if (count<=0) finish(42);
    call3(SYS_WRITE,1,(long)content,count);
    put("PICONEST_LINUX_GUEST_OK\n");
    finish(0);
}
