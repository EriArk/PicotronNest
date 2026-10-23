/* Original SDL call observer. No copied PikoNest shim or proprietary code. */
#define _GNU_SOURCE
#include "SDL.h"
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>

static unsigned frames, updates, surfaces;
static Uint64 started;
static int quit_sent;

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
}

__attribute__((destructor)) static void finish(void) {
    printf("PICONEST_TRACE_DONE frames=%u updates=%u surfaces=%u quit=%d\n", frames, updates, surfaces, quit_sent);
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
    if ((frames == 60 || frames == 180) && w > 0 && h > 0 && w <= 2048 && h <= 2048) {
        size_t size = (size_t)w * (size_t)h * 4;
        unsigned char *pixels = malloc(size);
        if (pixels && SDL_RenderReadPixels(renderer, NULL, SDL_PIXELFORMAT_RGBA32, pixels, w * 4) == 0) {
            FILE *out = fopen("/probe/frame.tmp", "wb");
            if (out) {
                unsigned header[4] = {0x504e4631, (unsigned)w, (unsigned)h, frames};
                int ok = fwrite(header, sizeof(header), 1, out) == 1 && fwrite(pixels, size, 1, out) == 1;
                if (fclose(out)) ok = 0;
                if (ok && rename("/probe/frame.tmp", "/probe/frame.rgba") == 0)
                    printf("PICONEST_FRAME_CAPTURE sequence=%u width=%d height=%d pitch=%d format=RGBA32\n", frames, w, h, w * 4);
            }
        }
        free(pixels);
    }
    real(renderer);
}

int SDL_PollEvent(SDL_Event *event) {
    int (*real)(SDL_Event *) = symbol("SDL_PollEvent");
    if (event && !quit_sent && now_ms() - started >= 8000) {
        SDL_zero(*event);
        event->type = SDL_QUIT;
        quit_sent = 1;
        puts("PICONEST_TRACE_QUIT");
        return 1;
    }
    return real(event);
}
