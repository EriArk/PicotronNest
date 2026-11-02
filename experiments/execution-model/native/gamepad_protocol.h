/* Complete controller snapshots, carried in the authenticated ordered stream. */
#ifndef PN_GAMEPAD_PROTOCOL_H
#define PN_GAMEPAD_PROTOCOL_H
typedef struct { unsigned connected,buttons; int axes[6]; } PNGamepad;
static int pn_gamepad_decode(PNGamepad *next,const unsigned char b[64]) {
    if (pn_u32(b+12)>=4 || pn_u32(b+16)>1 || pn_u32(b+20)>0x7fff) return -1;
    for (unsigned i=48;i<64;++i) if (b[i]) return -1;
    PNGamepad value={0}; value.connected=pn_u32(b+16); value.buttons=pn_u32(b+20);
    for (unsigned i=0;i<6;++i) {
        value.axes[i]=(int32_t)pn_u32(b+24+i*4);
        if (value.axes[i]< (i<4 ? -32768 : 0) || value.axes[i]>32767) return -1;
        if (!value.connected && value.axes[i]) return -1;
    }
    if (!value.connected && value.buttons) return -1;
    *next=value; return 0;
}
#endif
