/* Bounded keyboard wire decoder shared by the SDL observer and owned fixture. */
#ifndef PN_KEYBOARD_PROTOCOL_H
#define PN_KEYBOARD_PROTOCOL_H
#include <stdint.h>
#include <string.h>
#include "pointer_protocol.h"
typedef struct {
    uint32_t sequence, packets, downs, ups, repeats, text_bytes, resets;
    unsigned char held[512];
} PNKeyboard;
static unsigned pn_key_mods(const PNKeyboard *k) {
    return (k->held[225] || k->held[229] ? 1u : 0u) | (k->held[224] || k->held[228] ? 2u : 0u)
        | (k->held[226] || k->held[230] ? 4u : 0u) | (k->held[227] || k->held[231] ? 8u : 0u);
}
static int pn_utf8(const unsigned char *p, unsigned length) {
    for (unsigned i = 0; i < length;) {
        unsigned first = p[i++], cp, extra, min;
        if (first > 0 && first < 128) continue;
        if (first >= 0xc2 && first <= 0xdf) { cp = first & 31u; extra = 1; min = 128; }
        else if (first >= 0xe0 && first <= 0xef) { cp = first & 15u; extra = 2; min = 2048; }
        else if (first >= 0xf0 && first <= 0xf4) { cp = first & 7u; extra = 3; min = 65536; }
        else return -1;
        if (extra > length - i) return -1;
        while (extra--) { unsigned next = p[i++]; if ((next & 0xc0u) != 0x80u) return -1; cp = cp * 64 + (next & 63u); }
        if (cp < min || cp > 0x10ffff || (cp >= 0xd800 && cp <= 0xdfff)) return -1;
    }
    return 0;
}
static int pn_keyboard_apply(PNKeyboard *k, const unsigned char b[64]) {
    uint32_t seq = pn_u32(b+4), type = pn_u32(b+8), scan = pn_u32(b+12), flags = pn_u32(b+16);
    uint32_t mods = pn_u32(b+20), length = pn_u32(b+24);
    if (pn_u32(b) != 0x504e4b31 || seq <= k->sequence || type < 1 || type > 3 || length > 31) return -1;
    for (unsigned i = 28 + length; i < 64; ++i) if (b[i]) return -1;
    PNKeyboard next = *k;
    if (type == 1) {
        if (!scan || scan >= 512 || flags > 3 || flags == 2 || mods > 15 || length) return -1;
        if (flags & 1) {
            if (!!next.held[scan] != !!(flags & 2)) return -1;
            if (flags & 2) ++next.repeats; else { next.held[scan] = 1; ++next.downs; }
        } else { if (!next.held[scan]) return -1; next.held[scan] = 0; ++next.ups; }
        if (mods != pn_key_mods(&next)) return -1;
    } else {
        if (scan || flags || mods) return -1;
        if (type == 2) {
            if (!length || pn_utf8(b+28,length)) return -1;
            next.text_bytes += length;
        } else {
            if (length) return -1;
            for (unsigned i = 1; i < 512; ++i) { next.ups += next.held[i]; next.held[i] = 0; }
            ++next.resets;
        }
    }
    next.sequence = seq; ++next.packets; *k = next; return 0;
}
#endif
