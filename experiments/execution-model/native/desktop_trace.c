/* Original SDL call observer. No copied PikoNest shim or proprietary code. */
#define _GNU_SOURCE
#include "SDL.h"
#include "pointer_protocol.h"
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/time.h>
#include <stddef.h>
#include <errno.h>
#include <string.h>

static unsigned frames, updates, surfaces;
static Uint64 started;
static int quit_sent;
static int frame_socket = -1;
static unsigned streamed;
static Uint64 last_stream;
static unsigned char *frame_pixels;
static size_t frame_capacity;
static SDL_Window *trace_window;
static int window_width, window_height;
static PNPointer pointer;
static unsigned char input_bytes[20];
static size_t input_used;
static SDL_Event pending[2];
static unsigned pending_count, pending_index;
static Uint64 duration_ms = 20000;

static int send_all(const void *data, size_t length) {
    const unsigned char *p = data;
    while (length) {
        ssize_t n = send(frame_socket, p, length, MSG_NOSIGNAL);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return -1;
        p += n;
        length -= (size_t)n;
    }
    return 0;
}

static void *symbol(const char *name) {
    void *p = dlsym(RTLD_NEXT, name);
    if (!p) { fprintf(stderr, "PICONEST_TRACE_MISSING %s\n", name); _exit(73); }
    return p;
}

static Uint64 now_ms(void) {
    struct timespec t;
    if (clock_gettime(CLOCK_MONOTONIC, &t)) _exit(74);
    return (Uint64)t.tv_sec * 1000 + (Uint64)t.tv_nsec / 1000000;
}

__attribute__((constructor)) static void begin(void) {
    started = now_ms();
    setvbuf(stdout, NULL, _IOLBF, 0);
    printf("PICONEST_TRACE_LOADED pid=%ld uid=%ld\n", (long)getpid(), (long)getuid());
    const char *name = getenv("PICONEST_FRAME_SOCKET");
    if (name) {
        struct sockaddr_un address = {0};
        size_t length = strlen(name);
        if (length == 0 || length > sizeof(address.sun_path) - 2) _exit(75);
        address.sun_family = AF_UNIX;
        memcpy(address.sun_path + 1, name, length);
        frame_socket = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
        struct timeval timeout = {.tv_sec = 1, .tv_usec = 0};
        if (frame_socket < 0 || setsockopt(frame_socket, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout)) ||
            connect(frame_socket, (struct sockaddr *)&address,
                    (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + length))) _exit(76);
        puts("PICONEST_FRAME_CONNECTED");
    }
}

__attribute__((destructor)) static void finish(void) {
    printf("PICONEST_TRACE_DONE frames=%u updates=%u surfaces=%u quit=%d\n", frames, updates, surfaces, quit_sent);
    printf("PICONEST_STREAM_DONE frames=%u\n", streamed);
    printf("PICONEST_INPUT_DONE packets=%u downs=%u ups=%u held=%u\n",
           pointer.packets, pointer.downs, pointer.ups, pointer.buttons);
    if (frame_socket >= 0) close(frame_socket);
    free(frame_pixels);
}

int SDL_Init(Uint32 flags) {
    int (*real)(Uint32) = symbol("SDL_Init");
    int result = real(flags);
    printf("PICONEST_SDL_INIT flags=%u result=%d error=%s\n", flags, result, SDL_GetError());
    return result;
}

SDL_Window *SDL_CreateWindow(const char *title, int x, int y, int w, int h, Uint32 flags) {
    SDL_Window *(*real)(const char *, int, int, int, int, Uint32) = symbol("SDL_CreateWindow");
    SDL_Window *window = real(title, x, y, w, h, flags);
    if (window) trace_window = window;
    printf("PICONEST_WINDOW width=%d height=%d flags=%u ok=%d error=%s\n", w, h, flags, window != NULL, SDL_GetError());
    return window;
}

SDL_Renderer *SDL_CreateRenderer(SDL_Window *window, int index, Uint32 flags) {
    SDL_Renderer *(*real)(SDL_Window *, int, Uint32) = symbol("SDL_CreateRenderer");
    SDL_Renderer *renderer = real(window, index, flags);
    SDL_RendererInfo info = {0};
    if (renderer) SDL_GetRendererInfo(renderer, &info);
    printf("PICONEST_RENDERER flags=%u ok=%d name=%s error=%s\n", flags, renderer != NULL,
           info.name ? info.name : "none", SDL_GetError());
    return renderer;
}

SDL_Texture *SDL_CreateTexture(SDL_Renderer *renderer, Uint32 format, int access, int w, int h) {
    SDL_Texture *(*real)(SDL_Renderer *, Uint32, int, int, int) = symbol("SDL_CreateTexture");
    SDL_Texture *texture = real(renderer, format, access, w, h);
    printf("PICONEST_TEXTURE width=%d height=%d format=%s access=%d ok=%d\n",
           w, h, SDL_GetPixelFormatName(format), access, texture != NULL);
    return texture;
}

int SDL_UpdateTexture(SDL_Texture *texture, const SDL_Rect *rect, const void *pixels, int pitch) {
    int (*real)(SDL_Texture *, const SDL_Rect *, const void *, int) = symbol("SDL_UpdateTexture");
    int result = real(texture, rect, pixels, pitch);
    static int logged_success;
    if (++updates == 1 || (result == 0 && !logged_success))
        printf("PICONEST_UPDATE_TEXTURE pitch=%d result=%d\n", pitch, result);
    if (result == 0) logged_success = 1;
    return result;
}

SDL_Surface *SDL_GetWindowSurface(SDL_Window *window) {
    SDL_Surface *(*real)(SDL_Window *) = symbol("SDL_GetWindowSurface");
    SDL_Surface *surface = real(window);
    if (++surfaces == 1 && surface) printf("PICONEST_SURFACE width=%d height=%d pitch=%d format=%s\n",
            surface->w, surface->h, surface->pitch, SDL_GetPixelFormatName(surface->format->format));
    return surface;
}

int SDL_UpdateWindowSurface(SDL_Window *window) {
    int (*real)(SDL_Window *) = symbol("SDL_UpdateWindowSurface");
    static int logged;
    if (!logged++) puts("PICONEST_UPDATE_WINDOW_SURFACE");
    return real(window);
}

void SDL_RenderPresent(SDL_Renderer *renderer) {
    void (*real)(SDL_Renderer *) = symbol("SDL_RenderPresent");
    int w = 0, h = 0;
    SDL_GetRendererOutputSize(renderer, &w, &h);
    ++frames;
    static int logged_valid;
    if (frames == 1 || (w > 0 && h > 0 && !logged_valid))
        printf("PICONEST_PRESENT width=%d height=%d\n", w, h);
    if (w > 0 && h > 0) logged_valid = 1;
    if (w > 0 && h > 0) { window_width = w; window_height = h; }
    int snapshot = frames == 60 || frames == 180;
    int stream = frame_socket >= 0 && now_ms() - last_stream >= 100;
    if ((snapshot || stream) && w > 0 && h > 0 && w <= 2048 && h <= 2048) {
        size_t size = (size_t)w * (size_t)h * 4;
        if (size > frame_capacity) {
            unsigned char *next = realloc(frame_pixels, size);
            if (!next) _exit(77);
            frame_pixels = next;
            frame_capacity = size;
        }
        if (SDL_RenderReadPixels(renderer, NULL, SDL_PIXELFORMAT_RGBA32, frame_pixels, w * 4) == 0) {
            unsigned header[4] = {0x504e4631, (unsigned)w, (unsigned)h, frames};
            if (stream) {
                if (send_all(header, sizeof(header)) || send_all(frame_pixels, size)) {
                    puts("PICONEST_FRAME_SEND_FAILED");
                    close(frame_socket);
                    frame_socket = -1;
                } else {
                    ++streamed;
                    last_stream = now_ms();
                }
            }
            FILE *out = snapshot ? fopen("/probe/frame.tmp", "wb") : NULL;
            if (out) {
                int ok = fwrite(header, sizeof(header), 1, out) == 1 && fwrite(frame_pixels, size, 1, out) == 1;
                if (fclose(out)) ok = 0;
                if (ok && rename("/probe/frame.tmp", "/probe/frame.rgba") == 0)
                    printf("PICONEST_FRAME_CAPTURE sequence=%u width=%d height=%d pitch=%d format=RGBA32\n", frames, w, h, w * 4);
            }
        }
    }
    real(renderer);
}

static int pointer_event(SDL_Event *event) {
    if (pending_index < pending_count) {
        *event = pending[pending_index++];
        return 1;
    }
    if (frame_socket < 0) return 0;
    while (input_used < sizeof(input_bytes)) {
        ssize_t count = recv(frame_socket, input_bytes + input_used, sizeof(input_bytes) - input_used, MSG_DONTWAIT);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) return 0;
        if (count <= 0) return 0;
        input_used += (size_t)count;
    }
    int previous_x = pointer.x, previous_y = pointer.y;
    Uint32 previous_buttons = pointer.buttons;
    input_used = 0;
    if (pn_pointer_apply(&pointer, input_bytes, window_width, window_height)) {
        puts("PICONEST_INPUT_INVALID");
        _exit(78);
    }
    pending_index = 0; pending_count = 1;
    memset(pending, 0, sizeof(pending));
    pending[0].type = SDL_MOUSEMOTION;
    pending[0].motion.timestamp = SDL_GetTicks();
    pending[0].motion.windowID = trace_window ? SDL_GetWindowID(trace_window) : 0;
    pending[0].motion.state = pointer.buttons;
    pending[0].motion.x = pointer.x; pending[0].motion.y = pointer.y;
    pending[0].motion.xrel = pointer.x - previous_x; pending[0].motion.yrel = pointer.y - previous_y;
    if (previous_buttons != pointer.buttons) {
        pending_count = 2;
        pending[1].type = pointer.buttons ? SDL_MOUSEBUTTONDOWN : SDL_MOUSEBUTTONUP;
        pending[1].button.timestamp = SDL_GetTicks();
        pending[1].button.windowID = pending[0].motion.windowID;
        pending[1].button.button = SDL_BUTTON_LEFT;
        pending[1].button.state = pointer.buttons ? SDL_PRESSED : SDL_RELEASED;
        pending[1].button.clicks = 1;
        pending[1].button.x = pointer.x; pending[1].button.y = pointer.y;
    }
    if (pointer.packets <= 32) printf("PICONEST_POINTER seq=%u x=%d y=%d buttons=%u\n",
                                     pointer.sequence, pointer.x, pointer.y, pointer.buttons);
    *event = pending[pending_index++];
    return 1;
}

Uint32 SDL_GetMouseState(int *x, int *y) {
    Uint32 (*real)(int *, int *) = symbol("SDL_GetMouseState");
    if (frame_socket < 0) return real(x, y);
    if (x) *x = pointer.x;
    if (y) *y = pointer.y;
    return pointer.buttons;
}

void SDL_WarpMouseInWindow(SDL_Window *window, int x, int y) {
    void (*real)(SDL_Window *, int, int) = symbol("SDL_WarpMouseInWindow");
    pointer.x = x; pointer.y = y;
    real(window, x, y);
}

int SDL_PollEvent(SDL_Event *event) {
    int (*real)(SDL_Event *) = symbol("SDL_PollEvent");
    if (event && !quit_sent && now_ms() - started >= duration_ms) {
        SDL_zero(*event);
        event->type = SDL_QUIT;
        quit_sent = 1;
        puts("PICONEST_TRACE_QUIT");
        return 1;
    }
    if (event && !quit_sent && pointer_event(event)) return 1;
    return real(event);
}
