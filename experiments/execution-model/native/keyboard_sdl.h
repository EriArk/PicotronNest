/* Owned bounded keyboard event translation shared by the SDL observer and fixture. */
#ifndef PN_KEYBOARD_SDL_H
#define PN_KEYBOARD_SDL_H
#include "SDL_events.h"
#include "keyboard_protocol.h"
_Static_assert(SDL_NUM_SCANCODES==512,"keyboard wire scancode bound must match pinned SDL");
_Static_assert(sizeof(((SDL_TextInputEvent *)0)->text)==32,"keyboard wire text bound must match pinned SDL");

typedef struct {
    PNKeyboard protocol;
    SDL_Event pending[512];
    unsigned count, index, events, text_events;
    unsigned char delivered[512];
} PNKeyboardSDL;

static SDL_Keymod pn_sdl_key_mods(const unsigned char held[512]) {
    return (SDL_Keymod)((held[225] ? KMOD_LSHIFT : 0) | (held[229] ? KMOD_RSHIFT : 0)
        | (held[224] ? KMOD_LCTRL : 0) | (held[228] ? KMOD_RCTRL : 0)
        | (held[226] ? KMOD_LALT : 0) | (held[230] ? KMOD_RALT : 0)
        | (held[227] ? KMOD_LGUI : 0) | (held[231] ? KMOD_RGUI : 0));
}

static void pn_sdl_key(SDL_Event *event, unsigned scan, unsigned down, unsigned repeat,
                       const unsigned char held[512], Uint32 ticks, Uint32 window,
                       SDL_Keycode (*keycode)(SDL_Scancode)) {
    memset(event,0,sizeof(*event));
    event->type=down ? SDL_KEYDOWN : SDL_KEYUP;
    event->key.timestamp=ticks; event->key.windowID=window;
    event->key.state=down ? SDL_PRESSED : SDL_RELEASED; event->key.repeat=(Uint8)repeat;
    event->key.keysym.scancode=(SDL_Scancode)scan;
    event->key.keysym.sym=keycode((SDL_Scancode)scan);
    event->key.keysym.mod=(Uint16)pn_sdl_key_mods(held);
}

/* Caller must drain the previous record before accepting another one. Invalid input is atomic. */
static int pn_keyboard_stage(PNKeyboardSDL *bridge, const unsigned char bytes[64], Uint32 ticks,
                             Uint32 window, SDL_Keycode (*keycode)(SDL_Scancode)) {
    if (bridge->index<bridge->count) return -1;
    PNKeyboard next=bridge->protocol;
    if (pn_keyboard_apply(&next,bytes)) return -1;
    bridge->index=bridge->count=0;
    unsigned type=pn_u32(bytes+8);
    if (type==1) {
        unsigned flags=pn_u32(bytes+16);
        pn_sdl_key(&bridge->pending[bridge->count++],pn_u32(bytes+12),flags&1,!!(flags&2),
                   next.held,ticks,window,keycode);
    } else if (type==2) {
        SDL_Event *event=&bridge->pending[bridge->count++];
        memset(event,0,sizeof(*event));
        event->type=SDL_TEXTINPUT; event->text.timestamp=ticks; event->text.windowID=window;
        memcpy(event->text.text,bytes+28,pn_u32(bytes+24)); // Validated <=31 UTF-8 bytes, plus zero terminator.
    } else {
        unsigned char held[512]; memcpy(held,bridge->protocol.held,sizeof(held));
        for (unsigned scan=1;scan<512;++scan) if (held[scan]) {
            held[scan]=0;
            pn_sdl_key(&bridge->pending[bridge->count++],scan,0,0,held,ticks,window,keycode);
        }
    }
    bridge->protocol=next;
    return 0;
}

/* Disconnect releases delivered state; it is not a valid wire packet or an extra sequence number. */
static void pn_keyboard_release(PNKeyboardSDL *bridge, Uint32 ticks, Uint32 window,
                                SDL_Keycode (*keycode)(SDL_Scancode)) {
    unsigned char held[512]; memcpy(held,bridge->delivered,sizeof(held));
    bridge->index=bridge->count=0;
    for (unsigned scan=1;scan<512;++scan) if (held[scan]) {
        held[scan]=0;
        pn_sdl_key(&bridge->pending[bridge->count++],scan,0,0,held,ticks,window,keycode);
    }
    for (unsigned scan=1;scan<512;++scan) {
        bridge->protocol.ups+=bridge->protocol.held[scan]; bridge->protocol.held[scan]=0;
    }
}

static int pn_keyboard_pop(PNKeyboardSDL *bridge, SDL_Event *event) {
    if (bridge->index==bridge->count) return 0;
    if (!event) return 1; // PollEvent(NULL) must not consume or change sampled key state.
    *event=bridge->pending[bridge->index++];
    if (event->type==SDL_KEYDOWN || event->type==SDL_KEYUP)
        bridge->delivered[event->key.keysym.scancode]=event->type==SDL_KEYDOWN;
    if (event->type==SDL_TEXTINPUT) ++bridge->text_events;
    ++bridge->events; return 1;
}
#endif
