/* Original SDL call observer. No copied PikoNest shim or proprietary code. */
#define _GNU_SOURCE
#include "SDL.h"
#include "pointer_protocol.h"
#include "pointer_sdl.h"
#include "keyboard_channel.h"
#include "ordered_input.h"
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
static unsigned char input_bytes[PN_POINTER_BYTES];
static size_t input_used;
static SDL_Event pending[4];
static unsigned pending_count, pending_index;
static Uint64 duration_ms = 20000;
static int keyboard_socket=-1, keyboard_enabled, keyboard_failed, prefer_keyboard=1;
static PNKeyboardChannel key_channel;
static PNOrderedInput ordered;
static int ordered_enabled;
static int managed, texture_width, texture_height;

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

#include "audio_bridge.h"
#include "gamepad_sdl.h"

__attribute__((constructor)) static void begin(void) {
    started = now_ms();
    managed=getenv("PICONEST_MANAGED_SESSION")!=NULL;
    if (managed) {
        ordered.gamepad_apply=pn_gamepad_apply;
        // The SDL dummy window never gains OS focus. Android owns focus and releases.
        SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS,"1");
    }
    if (managed) duration_ms=0;
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
    name=getenv("PICONEST_INPUT_SOCKET");
    ordered_enabled=name!=NULL;
    ordered.sample_edges=ordered_enabled;
    if (!name) name=getenv("PICONEST_KEYBOARD_SOCKET");
    if (name) {
        struct sockaddr_un address={0}; size_t length=strlen(name);
        if (!length || length>sizeof(address.sun_path)-2 || frame_socket<0) _exit(79);
        address.sun_family=AF_UNIX; memcpy(address.sun_path+1,name,length);
        keyboard_socket=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);
        struct timeval timeout={.tv_sec=1};
        struct ucred peer; socklen_t size=sizeof(peer);
        if (keyboard_socket<0 || setsockopt(keyboard_socket,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout))
            || connect(keyboard_socket,(struct sockaddr *)&address,(socklen_t)(offsetof(struct sockaddr_un,sun_path)+1+length))
            || getsockopt(keyboard_socket,SOL_SOCKET,SO_PEERCRED,&peer,&size) || size!=sizeof(peer) || peer.uid!=getuid()) _exit(79);
        keyboard_enabled=1; puts("PICONEST_KEYBOARD_CONNECTED");
        if (ordered_enabled) puts("PICONEST_ORDERED_CONNECTED");
    }
}

__attribute__((destructor)) static void finish(void) {
    if (ordered_enabled) pointer=ordered.pointer;
    printf("PICONEST_TRACE_DONE frames=%u updates=%u surfaces=%u quit=%d\n", frames, updates, surfaces, quit_sent);
    printf("PICONEST_STREAM_DONE frames=%u\n", streamed);
    printf("PICONEST_INPUT_DONE packets=%u downs=%u ups=%u held=%u\n",
           pointer.packets, pointer.downs, pointer.ups, pointer.buttons);
    if (frame_socket >= 0) close(frame_socket);
    if (keyboard_enabled) {
        const PNKeyboardSDL *keyboard=ordered_enabled ? &ordered.key : &key_channel.bridge;
        unsigned held=0; for (unsigned i=1;i<512;++i) held+=keyboard->delivered[i];
        printf("PICONEST_KEYBOARD_DONE packets=%u downs=%u ups=%u repeats=%u held=%u events=%u text_events=%u text_bytes=%u suppressed=%u pending=%u buffered=%zu failed=%d\n",
               keyboard->protocol.packets,keyboard->protocol.downs,keyboard->protocol.ups,keyboard->protocol.repeats,held,keyboard->events,
               keyboard->text_events,keyboard->protocol.text_bytes,ordered_enabled ? ordered.suppressed : key_channel.suppressed,
               keyboard->count-keyboard->index,ordered_enabled ? ordered.used : key_channel.used,keyboard_failed);
    }
    if (ordered_enabled) printf("PICONEST_ORDERED_DONE packets=%u acknowledged=%u events=%u quits=%u pending=%u failed=%d\n",
        ordered.packets,ordered.acknowledged,ordered.events,ordered.quits,
        ordered.count-ordered.index+ordered.key.count-ordered.key.index,ordered.error);
    if (managed) printf("PICONEST_GAMEPAD_DONE packets=%u\n",ordered.gamepad_packets);
    if (keyboard_socket>=0) close(keyboard_socket);
    free(frame_pixels);
}

int SDL_Init(Uint32 flags) {
    int (*real)(Uint32) = symbol("SDL_Init");
    int result = real(flags);
    if (managed && result==0 && (flags&(SDL_INIT_JOYSTICK|SDL_INIT_GAMECONTROLLER)) && pn_gamepad_initialize()) {
        fprintf(stderr,"PICONEST_GAMEPAD_INIT_FAILED %s\n",SDL_GetError()); return -1;
    }
    printf("PICONEST_SDL_INIT flags=%u result=%d error=%s\n", flags, result, SDL_GetError());
    return result;
}

SDL_Window *SDL_CreateWindow(const char *title, int x, int y, int w, int h, Uint32 flags) {
    SDL_Window *(*real)(const char *, int, int, int, int, Uint32) = symbol("SDL_CreateWindow");
    if (managed) flags &= ~(SDL_WINDOW_FULLSCREEN_DESKTOP);
    SDL_Window *window = real(title, x, y, w, h, flags);
    if (window) trace_window = window;
    printf("PICONEST_WINDOW width=%d height=%d flags=%u ok=%d error=%s\n", w, h, flags, window != NULL, SDL_GetError());
    return window;
}

int SDL_SetWindowFullscreen(SDL_Window *window, Uint32 flags) {
    int (*real)(SDL_Window *,Uint32)=symbol("SDL_SetWindowFullscreen");
    return real(window,managed ? 0 : flags);
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
    // Match the actual official streaming texture, including resolution changes.
    // Android owns physical fullscreen scaling; SDL must not add a second border.
    if (managed && texture && trace_window && access==SDL_TEXTUREACCESS_STREAMING
        && w>0 && h>0 && w<=2048 && h<=2048 && (w!=texture_width || h!=texture_height)) {
        texture_width=w; texture_height=h;
        SDL_SetWindowSize(trace_window,w,h);
        printf("PICONEST_CONTENT_WINDOW width=%d height=%d\n",w,h);
    }
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
    int result=real(window);
    if (ordered_enabled) pn_ordered_end_poll(&ordered);
    return result;
}

void SDL_RenderPresent(SDL_Renderer *renderer) {
    // Picotron may pump SDL more than once per native frame. Keep short edges
    // visible until that frame has actually reached presentation.
    if (ordered_enabled) pn_ordered_end_poll(&ordered);
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
    int stream = frame_socket >= 0 && (managed || now_ms() - last_stream >= 100);
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
    PNPointer before = pointer;
    input_used = 0;
    if (pn_pointer_apply(&pointer, input_bytes, window_width, window_height)) {
        puts("PICONEST_INPUT_INVALID");
        _exit(78);
    }
    pending_index = 0;
    pending_count = pn_pointer_events(pending, &before, &pointer, SDL_GetTicks(), trace_window ? SDL_GetWindowID(trace_window) : 0);
    if (pointer.packets <= 32) printf("PICONEST_POINTER seq=%u x=%d y=%d buttons=%u wheel=%d,%d\n",
                                     pointer.sequence, pointer.x, pointer.y, pointer.buttons, pointer.wheel_x, pointer.wheel_y);
    *event = pending[pending_index++];
    return 1;
}

Uint32 SDL_GetMouseState(int *x, int *y) {
    Uint32 (*real)(int *, int *) = symbol("SDL_GetMouseState");
    if (ordered_enabled) {
        if (x) *x=ordered.x; if (y) *y=ordered.y;
        return pn_mouse_mask(ordered.buttons);
    }
    if (frame_socket < 0) return real(x, y);
    if (x) *x = pointer.x;
    if (y) *y = pointer.y;
    return pn_mouse_mask(pointer.buttons);
}

void SDL_WarpMouseInWindow(SDL_Window *window, int x, int y) {
    void (*real)(SDL_Window *, int, int) = symbol("SDL_WarpMouseInWindow");
    pointer.x = x; pointer.y = y;
    if (ordered_enabled) { ordered.pointer.x=ordered.x=x; ordered.pointer.y=ordered.y=y; }
    real(window, x, y);
}

static int keyboard_event(SDL_Event *event) {
    if (!keyboard_enabled) return 0;
    key_channel.suppress_text=!SDL_IsTextInputActive();
    if (keyboard_socket<0) return pn_keyboard_pop(&key_channel.bridge,event);
    int ready=pn_keyboard_read(&key_channel,keyboard_socket,event,SDL_GetTicks(),
                              trace_window ? SDL_GetWindowID(trace_window) : 0,SDL_GetKeyFromScancode);
    if (key_channel.error) {
        keyboard_failed=1;
        printf("PICONEST_KEYBOARD_ERROR code=%d\n",key_channel.error); // Never print text payloads.
        close(keyboard_socket); keyboard_socket=-1;
    }
    return ready;
}
static int ordered_event(SDL_Event *event) {
    ordered.suppress_text=!SDL_IsTextInputActive();
    Uint32 ticks=SDL_GetTicks(), window=trace_window ? SDL_GetWindowID(trace_window) : 0;
    int ready=keyboard_socket<0 ? pn_ordered_pop(&ordered,event)
        : pn_ordered_read(&ordered,keyboard_socket,event,window_width,window_height,ticks,window,SDL_GetKeyFromScancode);
    if (ordered.error && keyboard_socket>=0) {
        keyboard_failed=1; printf("PICONEST_KEYBOARD_ERROR code=%d\n",ordered.error);
        close(keyboard_socket); keyboard_socket=-1;
    }
    if (ready && event && event->type==SDL_QUIT) { quit_sent=1; puts("PICONEST_TRACE_QUIT ordered=1"); }
    return ready;
}

const Uint8 *SDL_GetKeyboardState(int *count) {
    const Uint8 *(*real)(int *)=symbol("SDL_GetKeyboardState");
    if (!keyboard_enabled) return real(count);
    if (count) *count=512;
    return ordered_enabled ? ordered.key.delivered : key_channel.bridge.delivered;
}
SDL_Keymod SDL_GetModState(void) {
    SDL_Keymod (*real)(void)=symbol("SDL_GetModState");
    return keyboard_enabled ? pn_sdl_key_mods(ordered_enabled ? ordered.key.delivered : key_channel.bridge.delivered) : real();
}

int SDL_PollEvent(SDL_Event *event) {
    int (*real)(SDL_Event *) = symbol("SDL_PollEvent");
    if (event && !quit_sent && duration_ms && now_ms() - started >= duration_ms) {
        SDL_zero(*event);
        event->type = SDL_QUIT;
        quit_sent = 1;
        puts("PICONEST_TRACE_QUIT");
        return 1;
    }
    if (!quit_sent) {
        if (ordered_enabled) {
            if (ordered_event(event)) return 1;
            if (ordered.yield_poll) return 0;
            return real(event);
        }
        // Preserve order within each channel without starving the other under continuous input.
        if (prefer_keyboard && keyboard_event(event)) { if (event) prefer_keyboard=0; return 1; }
        if (event && pointer_event(event)) { prefer_keyboard=1; return 1; }
        if (!prefer_keyboard && keyboard_event(event)) { if (event) prefer_keyboard=0; return 1; }
    }
    return real(event);
}
