/* Owned pattern source + shared pointer decoder. Contains no SDL or Picotron. */
#define _GNU_SOURCE
#include "../native/pointer_protocol.h"
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/time.h>
#include <poll.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>

static void put(unsigned char *p, uint32_t value) {
    for (int i = 0; i < 4; ++i) p[i] = (unsigned char)(value >> (i * 8));
}
static void selftest(void) {
    PNPointer p = {0};
    unsigned char b[20] = {0};
    put(b, 0x504e4931); put(b + 4, 1); put(b + 8, 47); put(b + 12, 26); put(b + 16, 1);
    if (pn_pointer_apply(&p, b, 48, 27) || p.downs != 1 || p.x != 47 || p.y != 26) _exit(41);
    PNPointer before = p;
    if (!pn_pointer_apply(&p, b, 48, 27) || memcmp(&p, &before, sizeof(p))) _exit(42);
    put(b + 4, 2);
    for (int offset = 0; offset <= 16; offset += 4) {
        uint32_t old = pn_u32(b + offset); put(b + offset, 0xffffffff);
        // Sequence 0xffffffff is valid; test zero instead.
        if (offset == 4) put(b + offset, 0);
        if (!pn_pointer_apply(&p, b, 48, 27) || memcmp(&p, &before, sizeof(p))) _exit(43);
        put(b + offset, old);
    }
    if (!pn_pointer_apply(&p, b, 0, 27) || !pn_pointer_apply(&p, b, 48, 4096)) _exit(44);
    put(b + 16, 0);
    if (pn_pointer_apply(&p, b, 27, 48) || p.ups != 1 || p.buttons || p.x != 26) _exit(45);
}
static void send_bytes(int fd, const unsigned char *data, size_t size) {
    while (size) {
        size_t chunk = size > 7 ? 7 : size; // Intentionally fragment frame headers/pixels.
        ssize_t n = send(fd, data, chunk, MSG_NOSIGNAL);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) _exit(46);
        data += n; size -= (size_t)n;
    }
}
static void frame(int fd, int w, int h, uint32_t seq) {
    unsigned char b[16 + 48 * 48 * 4] = {0};
    put(b, 0x504e4631); put(b + 4, (uint32_t)w); put(b + 8, (uint32_t)h); put(b + 12, seq);
    for (int i = 0; i < w * h; i++) {
        b[16 + i * 4] = (unsigned char)(i % w < w / 2 ? 255 : 0);
        b[18 + i * 4] = (unsigned char)(i % w >= w / 2 ? 255 : 0);
        b[19 + i * 4] = 255;
    }
    send_bytes(fd, b, (size_t)(16 + w * h * 4));
}
int main(int argc, char **argv) {
    alarm(90); setvbuf(stdout, NULL, _IOLBF, 0); selftest();
    if (argc != 2 || strlen(argv[1]) > 90) return 40;
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    struct sockaddr_un addr = {.sun_family = AF_UNIX};
    memcpy(addr.sun_path + 1, argv[1], strlen(argv[1]));
    struct timeval timeout = {.tv_sec = 2};
    if (fd < 0 || setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout)) ||
        connect(fd, (struct sockaddr *)&addr, (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(argv[1])))) return 47;
    printf("READY pid=%ld uid=%ld decoder_tests=PASS\n", (long)getpid(), (long)getuid());
    PNPointer pointer = {0};
    unsigned char bytes[20]; size_t used = 0;
    int w = 48, h = 27; uint32_t sequence = 0;
    for (;;) {
        struct pollfd waits[2] = {{.fd = fd, .events = POLLIN}, {.fd = STDIN_FILENO, .events = POLLIN}};
        if (poll(waits, 2, 20) < 0) { if (errno == EINTR) continue; return 48; }
        if (waits[1].revents & POLLIN) {
            unsigned char command;
            if (read(STDIN_FILENO, &command, 1) != 1) return 49;
            if (command == 'Q') break;
            if (command == 'X') _exit(23);
            if (command == 'T') { send_bytes(fd, (unsigned char[]){0x31, 0x46}, 2); _exit(24); }
            if (command == 'R') { w = 27; h = 48; }
        }
        if (waits[0].revents & (POLLIN | POLLHUP)) {
            size_t remaining = sizeof(bytes) - used;
            ssize_t n = recv(fd, bytes + used, remaining > 7 ? 7 : remaining, MSG_DONTWAIT);
            if (n == 0) return used || pointer.buttons ? 50 : 0;
            if (n < 0 && errno != EAGAIN && errno != EINTR) return 51;
            if (n > 0) used += (size_t)n;
            if (used == sizeof(bytes)) {
                if (pn_pointer_apply(&pointer, bytes, w, h)) return 52;
                printf("INPUT %u %d %d %u %u %u\n", pointer.packets, pointer.x, pointer.y,
                       pointer.buttons, pointer.downs, pointer.ups);
                used = 0;
            }
        }
        frame(fd, w, h, ++sequence);
    }
    printf("DONE packets=%u downs=%u ups=%u held=%u frames=%u\n", pointer.packets,
           pointer.downs, pointer.ups, pointer.buttons, sequence);
    close(fd);
    return used || pointer.buttons ? 53 : 0;
}
