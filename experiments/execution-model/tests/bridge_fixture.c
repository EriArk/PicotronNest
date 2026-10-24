/* Owned pattern source + shared pointer/SDL event translation. No SDL library or Picotron. */
#define _GNU_SOURCE
#include "../native/pointer_protocol.h"
#include "../native/pointer_sdl.h"
#include "../native/keyboard_protocol.h"
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
static void sdl_selftest(void) {
    unsigned char bytes[PN_POINTER_BYTES] = {0};
    PNPointer before = {0}, after = {0}; SDL_Event events[4];
    put(bytes,0x504e4932); put(bytes+4,1); put(bytes+8,5); put(bytes+12,6);
    put(bytes+16,3); put(bytes+20,512); put(bytes+24,(uint32_t)-256);
    if (pn_pointer_apply(&after,bytes,48,27) || pn_pointer_events(events,&before,&after,123,17)!=4) _exit(63);
    if (events[0].type!=SDL_MOUSEMOTION || events[0].motion.state!=(SDL_BUTTON_LMASK|SDL_BUTTON_RMASK)
        || events[0].motion.xrel!=5 || events[0].motion.yrel!=6
        || events[1].type!=SDL_MOUSEBUTTONDOWN || events[1].button.button!=SDL_BUTTON_LEFT
        || events[2].type!=SDL_MOUSEBUTTONDOWN || events[2].button.button!=SDL_BUTTON_RIGHT
        || events[2].button.state!=SDL_PRESSED || events[3].type!=SDL_MOUSEWHEEL
        || events[3].wheel.x || events[3].wheel.y || events[3].wheel.preciseX!=0.5f
        || events[3].wheel.preciseY!=-0.25f || events[3].wheel.direction!=SDL_MOUSEWHEEL_NORMAL
        || events[3].wheel.mouseX!=5 || events[3].wheel.mouseY!=6) _exit(64);
    for (int i=0;i<4;++i) if (events[i].common.timestamp!=123 || events[i].window.windowID!=17) _exit(64);
    before=after; put(bytes+4,2); put(bytes+16,2); put(bytes+24,(uint32_t)-768);
    if (pn_pointer_apply(&after,bytes,48,27) || pn_pointer_events(events,&before,&after,124,17)!=3
        || events[0].motion.state!=SDL_BUTTON_RMASK || events[1].type!=SDL_MOUSEBUTTONUP
        || events[1].button.button!=SDL_BUTTON_LEFT || events[1].button.state!=SDL_RELEASED
        || events[2].wheel.x!=1 || events[2].wheel.y!=-1 || events[2].wheel.preciseY!=-0.75f) _exit(65);
    before=after; put(bytes+4,3); put(bytes+20,0); put(bytes+24,0);
    if (pn_pointer_apply(&after,bytes,48,27) || pn_pointer_events(events,&before,&after,125,17)!=1) _exit(65);
    before=after; put(bytes+4,4); put(bytes+16,0);
    if (pn_pointer_apply(&after,bytes,48,27) || pn_pointer_events(events,&before,&after,126,17)!=2
        || events[0].motion.state || events[1].type!=SDL_MOUSEBUTTONUP || events[1].button.button!=SDL_BUTTON_RIGHT) _exit(65);
}
static void selftest(void) {
    sdl_selftest();
    PNKeyboard keyboard = {0}; unsigned char key[64] = {0};
    put(key,0x504e4b31); put(key+4,1); put(key+8,2); put(key+24,4);
    memcpy(key+28,"\xf0\x9f\x91\x8b",4);
    if (pn_keyboard_apply(&keyboard,key) || keyboard.text_bytes != 4) _exit(54);
    PNKeyboard saved = keyboard; put(key+4,2);
    for (unsigned i = 0; i < 4; ++i) {
        unsigned char old = key[28+i]; key[28+i] = 0xff;
        if (!pn_keyboard_apply(&keyboard,key) || memcmp(&keyboard,&saved,sizeof(saved))) _exit(55);
        key[28+i] = old;
    }
    PNPointer p = {0};
    unsigned char b[PN_POINTER_BYTES] = {0};
    put(b, 0x504e4932); put(b + 4, 1); put(b + 8, 47); put(b + 12, 26); put(b + 16, 1);
    if (pn_pointer_apply(&p, b, 48, 27) || p.downs != 1 || p.x != 47 || p.y != 26) _exit(41);
    PNPointer before = p;
    if (!pn_pointer_apply(&p, b, 48, 27) || memcmp(&p, &before, sizeof(p))) _exit(42);
    put(b + 4, 2);
    for (int offset = 0; offset <= 24; offset += 4) {
        uint32_t old = pn_u32(b + offset); put(b + offset, 0xffffffff);
        if (offset >= 20) put(b + offset, PN_WHEEL_LIMIT + 1);
        if (!pn_pointer_apply(&p, b, 48, 27) || memcmp(&p, &before, sizeof(p))) _exit(43);
        put(b + offset, old);
    }
    if (!pn_pointer_apply(&p, b, 0, 27) || !pn_pointer_apply(&p, b, 48, 4096)) _exit(44);
    put(b + 16, 0);
    if (pn_pointer_apply(&p, b, 27, 48) || p.ups != 1 || p.buttons || p.x != 26) _exit(45);
    // Reject old versions, unsupported buttons, invalid signed extremes and off-frame wheel.
    before = p; put(b + 4, 3);
    put(b, 0x504e4931);
    if (!pn_pointer_apply(&p,b,48,27) || memcmp(&p,&before,sizeof(p))) _exit(61);
    put(b, 0x504e4932); put(b+16,4);
    if (!pn_pointer_apply(&p,b,48,27)) _exit(61);
    put(b+16,0); put(b+20,UINT32_C(0x80000000));
    if (!pn_pointer_apply(&p,b,48,27)) _exit(61);
    put(b+20,1);
    if (!pn_pointer_apply(&p,b,27,48) || memcmp(&p,&before,sizeof(p))) _exit(61);
    put(b+8,5); put(b+12,6); put(b+16,3); put(b+20,512); put(b+24,(uint32_t)-256);
    if (pn_pointer_apply(&p,b,48,27) || p.downs!=3 || p.buttons!=3 || p.wheel_steps_x || p.wheel_steps_y) _exit(62);
    put(b+4,4); put(b+16,2); put(b+20,512); put(b+24,(uint32_t)-768);
    if (pn_pointer_apply(&p,b,48,27) || p.ups!=2 || p.wheel_steps_x!=1 || p.wheel_steps_y!=-1) _exit(62);
    put(b+4,5); put(b+16,0); put(b+20,0); put(b+24,0);
    if (pn_pointer_apply(&p,b,48,27) || p.downs!=p.ups || p.wheel_x || p.wheel_y || p.wheel_steps_x || p.wheel_steps_y) _exit(62);
    put(b+4,6); put(b+20,PN_WHEEL_LIMIT); put(b+24,(uint32_t)-PN_WHEEL_LIMIT);
    if (pn_pointer_apply(&p,b,48,27) || p.wheel_steps_x!=32 || p.wheel_steps_y!=-32) _exit(62);
    before=p; put(b+4,7); put(b+24,(uint32_t)(-PN_WHEEL_LIMIT-1));
    if (!pn_pointer_apply(&p,b,48,27) || memcmp(&p,&before,sizeof(p))) _exit(61);
}
static void send_bytes(int fd, const unsigned char *data, size_t size) {
    size_t offset = 0;
    while (size) {
        size_t limit = offset < 16 ? 7 : 257; // Fragment both header and pixels without flooding tiny writes.
        size_t chunk = size > limit ? limit : size;
        ssize_t n = send(fd, data, chunk, MSG_NOSIGNAL);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) _exit(46);
        data += n; size -= (size_t)n; offset += (size_t)n;
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
    if ((argc != 2 && argc != 3) || strlen(argv[1]) > 90 || (argc == 3 && strlen(argv[2]) > 90)) return 40;
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    struct sockaddr_un addr = {.sun_family = AF_UNIX};
    memcpy(addr.sun_path + 1, argv[1], strlen(argv[1]));
    struct timeval timeout = {.tv_sec = 2};
    if (fd < 0 || setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout)) ||
        connect(fd, (struct sockaddr *)&addr, (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(argv[1])))) return 47;
    printf("READY pid=%ld uid=%ld decoder_tests=PASS sdl_event_tests=PASS\n", (long)getpid(), (long)getuid());
    int keys = -1;
    if (argc == 3) {
        keys = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
        memset(addr.sun_path,0,sizeof(addr.sun_path)); memcpy(addr.sun_path+1,argv[2],strlen(argv[2]));
        if (keys < 0 || connect(keys,(struct sockaddr *)&addr,
                (socklen_t)(offsetof(struct sockaddr_un,sun_path)+1+strlen(argv[2])))) return 56;
    }
    PNKeyboard keyboard = {0}; unsigned char key_bytes[64]; size_t key_used = 0;
    PNPointer pointer = {0};
    unsigned char bytes[PN_POINTER_BYTES]; size_t used = 0;
    int64_t wheel_x = 0, wheel_y = 0, steps_x = 0, steps_y = 0;
    int w = 48, h = 27; uint32_t sequence = 0; uint64_t last_frame = 0;
    for (;;) {
        struct pollfd waits[3] = {{.fd = fd, .events = POLLIN}, {.fd = STDIN_FILENO, .events = POLLIN}, {.fd=keys,.events=POLLIN}};
        if (poll(waits, 3, 20) < 0) { if (errno == EINTR) continue; return 48; }
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
                wheel_x += pointer.wheel_x; wheel_y += pointer.wheel_y;
                steps_x += pointer.wheel_steps_x; steps_y += pointer.wheel_steps_y;
                printf("INPUT %u %d %d %u %u %u %lld %lld %lld %lld\n", pointer.packets, pointer.x, pointer.y,
                       pointer.buttons, pointer.downs, pointer.ups, (long long)wheel_x, (long long)wheel_y,
                       (long long)steps_x, (long long)steps_y);
                used = 0;
            }
        }
        if (keys >= 0 && (waits[2].revents & (POLLIN | POLLHUP))) {
            size_t remaining = sizeof(key_bytes) - key_used;
            ssize_t n = recv(keys,key_bytes+key_used,remaining > 7 ? 7 : remaining,MSG_DONTWAIT);
            if (n == 0) return 57;
            if (n < 0 && errno != EAGAIN && errno != EINTR) return 58;
            if (n > 0) key_used += (size_t)n;
            if (key_used == sizeof(key_bytes)) {
                if (pn_keyboard_apply(&keyboard,key_bytes)) return 59;
                if (pn_u32(key_bytes+8) == 2) {
                    printf("TEXT ");
                    for (unsigned i=0; i<pn_u32(key_bytes+24); ++i) printf("%02x",key_bytes[28+i]);
                    putchar('\n');
                }
                printf("KEY %u %u %u %u %u %u %u\n",keyboard.packets,pn_u32(key_bytes+12),pn_u32(key_bytes+16),
                       pn_key_mods(&keyboard),keyboard.downs,keyboard.ups,keyboard.text_bytes);
                key_used = 0;
            }
        }
        struct timespec tick;
        if (clock_gettime(CLOCK_MONOTONIC,&tick)) return 60;
        uint64_t now = (uint64_t)tick.tv_sec*1000 + (uint64_t)tick.tv_nsec/1000000;
        if (now-last_frame >= 50) { frame(fd, w, h, ++sequence); last_frame = now; }
    }
    printf("DONE packets=%u downs=%u ups=%u held=%u frames=%u\n", pointer.packets,
           pointer.downs, pointer.ups, pointer.buttons, sequence);
    close(fd);
    if (keys >= 0) close(keys);
    printf("KEY_DONE packets=%u downs=%u ups=%u repeats=%u text_bytes=%u\n",
           keyboard.packets,keyboard.downs,keyboard.ups,keyboard.repeats,keyboard.text_bytes);
    return used || key_used || pointer.buttons || keyboard.downs != keyboard.ups ? 53 : 0;
}
