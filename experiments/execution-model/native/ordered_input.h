/* Owned PNO1 input stream: key/text, pointer, reset and quit in one acknowledged order. */
#ifndef PN_ORDERED_INPUT_H
#define PN_ORDERED_INPUT_H
#include "keyboard_channel.h"
#include "pointer_sdl.h"
#include "gamepad_protocol.h"

typedef struct {
    PNKeyboardSDL key;
    PNPointer pointer;
    SDL_Event pending[4];
    unsigned count,index,sequence,packets,acknowledged,events,quits,suppressed;
    int x,y; unsigned buttons;
    unsigned char bytes[64]; size_t used;
    int error,suppress_text;
    PNGamepad gamepads[4];
    int (*gamepad_apply)(unsigned slot,const PNGamepad *state);
    unsigned gamepad_packets;
} PNOrderedInput;

static void pn_put32(unsigned char *p,unsigned n) { for (int i=0;i<4;++i) p[i]=(unsigned char)(n>>(8*i)); }
static int pn_ordered_pending(const PNOrderedInput *s) { return s->index<s->count || s->key.index<s->key.count; }
static void pn_ordered_pointer_release(PNOrderedInput *s,Uint32 ticks,Uint32 window) {
    s->count=s->index=0;
    if (!s->buttons) return;
    PNPointer before=s->pointer; before.x=s->x; before.y=s->y; before.buttons=s->buttons;
    s->pointer.x=s->x; s->pointer.y=s->y;
    for (unsigned bit=1;bit<=2;bit<<=1) if (s->pointer.buttons&bit) ++s->pointer.ups;
    s->pointer.buttons=0; s->pointer.wheel_x=s->pointer.wheel_y=0;
    s->pointer.wheel_steps_x=s->pointer.wheel_steps_y=0;
    s->count=pn_pointer_events(s->pending,&before,&s->pointer,ticks,window);
}
static void pn_ordered_release(PNOrderedInput *s,Uint32 ticks,Uint32 window,SDL_Keycode (*keycode)(SDL_Scancode)) {
    pn_ordered_pointer_release(s,ticks,window);
    pn_keyboard_release(&s->key,ticks,window,keycode);
    for (unsigned i=0;i<4;++i) {
        memset(&s->gamepads[i],0,sizeof(s->gamepads[i]));
        if (s->gamepad_apply) s->gamepad_apply(i,&s->gamepads[i]);
    }
}
static int pn_ordered_stage(PNOrderedInput *s,const unsigned char b[64],int width,int height,
                            Uint32 ticks,Uint32 window,SDL_Keycode (*keycode)(SDL_Scancode)) {
    unsigned seq=pn_u32(b+4),type=pn_u32(b+8);
    if (pn_ordered_pending(s) || s->quits || pn_u32(b)!=0x504e4f31 || seq!=s->sequence+1 || seq>INT32_MAX) return -1;
    if (type>=1 && type<=3) {
        unsigned char key[64]; memcpy(key,b,64); pn_put32(key,0x504e4b31);
        if (pn_keyboard_stage(&s->key,key,ticks,window,keycode)) return -1;
        if (type==3) {
            pn_ordered_pointer_release(s,ticks,window);
            for (unsigned i=0;i<4;++i) {
                memset(&s->gamepads[i],0,sizeof(s->gamepads[i]));
                if (s->gamepad_apply && s->gamepad_apply(i,&s->gamepads[i])) return -1;
            }
        }
    } else if (type==4) {
        for (unsigned i=32;i<64;++i) if (b[i]) return -1;
        unsigned char point[28]; pn_put32(point,0x504e4932); pn_put32(point+4,seq);
        memcpy(point+8,b+12,20);
        PNPointer next=s->pointer;
        if (pn_pointer_apply(&next,point,width,height)) return -1;
        s->index=0; s->count=pn_pointer_events(s->pending,&s->pointer,&next,ticks,window);
        s->pointer=next;
    } else if (type==5) {
        for (unsigned i=12;i<64;++i) if (b[i]) return -1;
        for (unsigned i=1;i<512;++i) if (s->key.delivered[i]) return -1;
        if (s->buttons) return -1; // Exit must follow the ordered releases, never skip them.
        for (unsigned i=0;i<4;++i) if (s->gamepads[i].connected) return -1;
        s->index=0; s->count=1; memset(&s->pending[0],0,sizeof(s->pending[0]));
        s->pending[0].type=SDL_QUIT; s->pending[0].quit.timestamp=ticks;
    } else if (type==6) {
        PNGamepad next;
        if (pn_gamepad_decode(&next,b)) return -1;
        unsigned slot=pn_u32(b+12);
        if (s->gamepad_apply && s->gamepad_apply(slot,&next)) return -1;
        s->gamepads[slot]=next; ++s->gamepad_packets;
    } else return -1;
    s->sequence=seq; ++s->packets; return 0;
}
static int pn_ordered_pop(PNOrderedInput *s,SDL_Event *event) {
    if (s->suppress_text && s->key.index<s->key.count && s->key.pending[s->key.index].type==SDL_TEXTINPUT) {
        ++s->key.index; ++s->suppressed;
    }
    if (!pn_ordered_pending(s)) return 0;
    if (!event) return 1;
    if (s->index<s->count) {
        *event=s->pending[s->index++];
        if (event->type==SDL_MOUSEMOTION) { s->x=event->motion.x; s->y=event->motion.y; s->buttons=s->pointer.buttons; }
        if (event->type==SDL_QUIT) ++s->quits;
    } else if (!pn_keyboard_pop(&s->key,event)) return 0;
    ++s->events; return 1;
}
static int pn_ordered_ack(PNOrderedInput *s,int fd) {
    if (pn_ordered_pending(s) || s->sequence==s->acknowledged || s->error) return 0;
    unsigned char ack[16]; pn_put32(ack,0x504e4f41); pn_put32(ack+4,s->sequence);
    pn_put32(ack+8,s->events); pn_put32(ack+12,s->suppressed ? 1 : 0);
    size_t used=0;
    for (unsigned tries=0;used<sizeof(ack) && tries<64;++tries) {
        ssize_t n=send(fd,ack+used,sizeof(ack)-used,MSG_NOSIGNAL);
        if (n<0 && errno==EINTR) continue;
        if (n<=0) return -1;
        used+=(size_t)n;
    }
    if (used!=sizeof(ack)) return -1;
    s->acknowledged=s->sequence; return 0;
}
static int pn_ordered_read(PNOrderedInput *s,int fd,SDL_Event *event,int width,int height,
                           Uint32 ticks,Uint32 window,SDL_Keycode (*keycode)(SDL_Scancode)) {
    int ready=pn_ordered_pop(s,event);
    if (ready || s->error || s->quits) {
        if (pn_ordered_ack(s,fd)) { s->error=5; pn_ordered_release(s,ticks,window,keycode); }
        return ready;
    }
    if (pn_ordered_ack(s,fd)) s->error=5;
    for (unsigned tries=0;!s->error && s->used<64 && tries<64;++tries) {
        ssize_t n=recv(fd,s->bytes+s->used,64-s->used,MSG_DONTWAIT);
        if (n<0 && errno==EINTR) continue;
        if (n<0 && (errno==EAGAIN || errno==EWOULDBLOCK)) return 0;
        if (n<=0) { s->error=n<0 ? PN_KEY_IO : s->used ? PN_KEY_PARTIAL : PN_KEY_EOF; break; }
        s->used+=(size_t)n;
    }
    if (!s->error && s->used==64) {
        if (pn_ordered_stage(s,s->bytes,width,height,ticks,window,keycode)) s->error=PN_KEY_INVALID;
        else s->used=0;
    }
    if (s->error) pn_ordered_release(s,ticks,window,keycode);
    ready=pn_ordered_pop(s,event);
    if (pn_ordered_ack(s,fd)) { s->error=5; pn_ordered_release(s,ticks,window,keycode); }
    return ready;
}
#endif
