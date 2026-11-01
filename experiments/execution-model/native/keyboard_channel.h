/* Owned nonblocking record reader. Descriptor lifetime belongs to the caller. */
#ifndef PN_KEYBOARD_CHANNEL_H
#define PN_KEYBOARD_CHANNEL_H
#include "keyboard_sdl.h"
#include <sys/socket.h>
#include <errno.h>

enum { PN_KEY_EOF=1, PN_KEY_PARTIAL=2, PN_KEY_IO=3, PN_KEY_INVALID=4 };
typedef struct {
    PNKeyboardSDL bridge;
    unsigned char bytes[64];
    size_t used;
    int error;
    int suppress_text;
    unsigned suppressed;
} PNKeyboardChannel;

static int pn_keyboard_available(PNKeyboardChannel *channel, SDL_Event *event) {
    PNKeyboardSDL *bridge=&channel->bridge;
    if (channel->suppress_text && bridge->index<bridge->count && bridge->pending[bridge->index].type==SDL_TEXTINPUT) {
        ++bridge->index; ++channel->suppressed;
    }
    return pn_keyboard_pop(bridge,event);
}

static int pn_keyboard_read(PNKeyboardChannel *channel, int fd, SDL_Event *event, Uint32 ticks,
                            Uint32 window, SDL_Keycode (*keycode)(SDL_Scancode)) {
    if (pn_keyboard_available(channel,event)) return 1;
    if (channel->error) return 0;
    // Also bound interrupted/one-byte reads; the next poll continues the same record.
    for (unsigned reads=0;channel->used<sizeof(channel->bytes) && reads<64;++reads) {
        ssize_t n=recv(fd,channel->bytes+channel->used,sizeof(channel->bytes)-channel->used,MSG_DONTWAIT);
        if (n<0 && errno==EINTR) continue;
        if (n<0 && (errno==EAGAIN || errno==EWOULDBLOCK)) return 0;
        if (n<=0) {
            channel->error=n<0 ? PN_KEY_IO : channel->used ? PN_KEY_PARTIAL : PN_KEY_EOF;
            break;
        }
        channel->used+=(size_t)n;
    }
    if (!channel->error && channel->used==sizeof(channel->bytes)) {
        if (pn_keyboard_stage(&channel->bridge,channel->bytes,ticks,window,keycode)) channel->error=PN_KEY_INVALID;
        else channel->used=0;
    }
    if (channel->error) pn_keyboard_release(&channel->bridge,ticks,window,keycode);
    return pn_keyboard_available(channel,event);
}
#endif
