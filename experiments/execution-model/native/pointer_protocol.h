/* Original bounded pointer decoder shared by the SDL adapter and owned tests. */
#ifndef PN_POINTER_PROTOCOL_H
#define PN_POINTER_PROTOCOL_H
#include <stdint.h>
typedef struct {
    uint32_t sequence, packets, downs, ups, buttons;
    int x, y;
} PNPointer;
static uint32_t pn_u32(const unsigned char *p) {
    return (uint32_t)p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24;
}
static int pn_pointer_apply(PNPointer *state, const unsigned char bytes[20], int width, int height) {
    uint32_t sequence = pn_u32(bytes + 4), x = pn_u32(bytes + 8), y = pn_u32(bytes + 12);
    uint32_t buttons = pn_u32(bytes + 16);
    if (pn_u32(bytes) != 0x504e4931 || sequence <= state->sequence || width <= 0 || height <= 0 ||
        width > 2048 || height > 2048 || x >= 2048 || y >= 2048 || buttons > 1 ||
        (buttons && (x >= (uint32_t)width || y >= (uint32_t)height)))
        return -1;
    // A release queued from the previous frame size must still clear a held button.
    if (x >= (uint32_t)width) x = (uint32_t)width - 1;
    if (y >= (uint32_t)height) y = (uint32_t)height - 1;
    if (buttons != state->buttons) { if (buttons) ++state->downs; else ++state->ups; }
    state->sequence = sequence; state->x = (int)x; state->y = (int)y; state->buttons = buttons;
    ++state->packets;
    return 0;
}
#endif
