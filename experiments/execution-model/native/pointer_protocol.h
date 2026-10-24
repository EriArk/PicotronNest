/* Original bounded pointer decoder shared by the SDL adapter and owned tests. */
#ifndef PN_POINTER_PROTOCOL_H
#define PN_POINTER_PROTOCOL_H
#include <stdint.h>
#define PN_POINTER_BYTES 28
#define PN_WHEEL_UNIT 1024
#define PN_WHEEL_LIMIT (32 * PN_WHEEL_UNIT)
typedef struct {
    uint32_t sequence, packets, downs, ups, buttons;
    int x, y, wheel_x, wheel_y;
    int wheel_remainder_x, wheel_remainder_y, wheel_steps_x, wheel_steps_y;
} PNPointer;
static uint32_t pn_u32(const unsigned char *p) {
    return (uint32_t)p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24;
}
static int64_t pn_signed(const unsigned char *p) {
    uint32_t value = pn_u32(p);
    return value <= INT32_MAX ? (int64_t)value : (int64_t)value - INT64_C(4294967296);
}
static int pn_pointer_apply(PNPointer *state, const unsigned char bytes[PN_POINTER_BYTES], int width, int height) {
    uint32_t sequence = pn_u32(bytes + 4), x = pn_u32(bytes + 8), y = pn_u32(bytes + 12);
    uint32_t buttons = pn_u32(bytes + 16);
    int64_t wheel_x = pn_signed(bytes + 20), wheel_y = pn_signed(bytes + 24);
    if (pn_u32(bytes) != 0x504e4932 || sequence <= state->sequence || sequence > INT32_MAX || width <= 0 || height <= 0 ||
        width > 2048 || height > 2048 || x >= 2048 || y >= 2048 || buttons > 3 ||
        wheel_x < -PN_WHEEL_LIMIT || wheel_x > PN_WHEEL_LIMIT || wheel_y < -PN_WHEEL_LIMIT || wheel_y > PN_WHEEL_LIMIT ||
        ((buttons || wheel_x || wheel_y) && (x >= (uint32_t)width || y >= (uint32_t)height)))
        return -1;
    // A release queued from the previous frame size must still clear a held button.
    if (x >= (uint32_t)width) x = (uint32_t)width - 1;
    if (y >= (uint32_t)height) y = (uint32_t)height - 1;
    for (uint32_t bit = 1; bit <= 2; bit <<= 1) {
        if ((buttons ^ state->buttons) & bit) { if (buttons & bit) ++state->downs; else ++state->ups; }
    }
    state->wheel_x = (int)wheel_x; state->wheel_y = (int)wheel_y;
    int total_x = state->wheel_remainder_x + (int)wheel_x, total_y = state->wheel_remainder_y + (int)wheel_y;
    state->wheel_steps_x = total_x / PN_WHEEL_UNIT; state->wheel_steps_y = total_y / PN_WHEEL_UNIT;
    state->wheel_remainder_x = total_x % PN_WHEEL_UNIT; state->wheel_remainder_y = total_y % PN_WHEEL_UNIT;
    state->sequence = sequence; state->x = (int)x; state->y = (int)y; state->buttons = buttons;
    ++state->packets;
    return 0;
}
#endif
