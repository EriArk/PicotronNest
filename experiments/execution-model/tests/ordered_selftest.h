/* Owned regression of the production ordered reader, event construction and acknowledgement. */
static void ordered_selftest(void) {
    unsigned char b[64],ack[16]; SDL_Event e;
    // Controller state is atomic, source-owned, reset on EOF, and precedes quit.
    {
        PNOrderedInput pads={0},before;
        memset(b,0,64); put(b,0x504e4f31); put(b+4,1); put(b+8,6);
        put(b+12,3); put(b+16,1); put(b+20,0x4001); put(b+24,(unsigned)-32768);
        if (pn_ordered_stage(&pads,b,48,27,1,17,fixture_keycode) || pads.gamepads[3].buttons!=0x4001) _exit(89);
        put(b+4,2); put(b+12,0); put(b+20,2);
        if (pn_ordered_stage(&pads,b,48,27,1,17,fixture_keycode) || pads.gamepads[3].buttons!=0x4001) _exit(89);
        put(b+4,3); put(b+24,32768); before=pads;
        if (!pn_ordered_stage(&pads,b,48,27,1,17,fixture_keycode) || memcmp(&pads,&before,sizeof(pads))) _exit(89);
        memset(b+12,0,52); put(b+8,5);
        if (!pn_ordered_stage(&pads,b,48,27,1,17,fixture_keycode)) _exit(89);
        int pair[2]; if (socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair)) _exit(89);
        close(pair[0]); pn_ordered_read(&pads,pair[1],&e,48,27,2,17,fixture_keycode);
        if (!pads.error || pads.gamepads[0].connected || pads.gamepads[3].connected) _exit(89);
        close(pair[1]);
    }
    for (unsigned split=1;split<64;++split) {
        int pair[2]; PNOrderedInput s={0};
        if (socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair)) _exit(80);
        key_record(b,1,224,1,2); put(b,0x504e4f31);
        if (write(pair[0],b,split)!=(ssize_t)split || pn_ordered_read(&s,pair[1],&e,48,27,1,17,fixture_keycode)
            || s.used!=split || s.key.delivered[224]) _exit(81);
        if (write(pair[0],b+split,64-split)!=(ssize_t)(64-split)
            || !pn_ordered_read(&s,pair[1],NULL,48,27,1,17,fixture_keycode) || s.key.delivered[224]
            || !pn_ordered_read(&s,pair[1],&e,48,27,1,17,fixture_keycode) || e.type!=SDL_KEYDOWN
            || e.key.keysym.mod!=KMOD_LCTRL || recv(pair[0],ack,16,MSG_DONTWAIT)!=16
            || pn_u32(ack)!=0x504e4f41 || pn_u32(ack+4)!=1 || pn_u32(ack+8)!=1) _exit(82);
        memset(b,0,64); put(b,0x504e4f31); put(b+4,2); put(b+8,4); put(b+12,5); put(b+16,6); put(b+20,3);
        if (write(pair[0],b,64)!=64 || !pn_ordered_read(&s,pair[1],&e,48,27,2,17,fixture_keycode)
            || e.type!=SDL_MOUSEMOTION || pn_sdl_key_mods(s.key.delivered)!=KMOD_LCTRL
            || recv(pair[0],ack,16,MSG_DONTWAIT)>=0) _exit(83); // No early ack before both button events.
        if (!pn_ordered_read(&s,pair[1],&e,48,27,2,17,fixture_keycode) || e.type!=SDL_MOUSEBUTTONDOWN
            || !pn_ordered_read(&s,pair[1],&e,48,27,2,17,fixture_keycode) || e.type!=SDL_MOUSEBUTTONDOWN
            || recv(pair[0],ack,16,MSG_DONTWAIT)!=16 || pn_u32(ack+4)!=2 || pn_u32(ack+8)!=4) _exit(83);
        put(b+4,3);
        if (write(pair[0],b,split)!=(ssize_t)split) _exit(84);
        close(pair[0]); unsigned releases=0;
        while (pn_ordered_read(&s,pair[1],&e,48,27,3,17,fixture_keycode)) {
            if (++releases>4) _exit(84);
            if (e.type==SDL_MOUSEBUTTONUP && pn_sdl_key_mods(s.key.delivered)!=KMOD_LCTRL) _exit(84);
        }
        if (releases!=4 || s.error!=PN_KEY_PARTIAL || s.buttons || s.key.delivered[224] || s.acknowledged!=2) _exit(84);
        close(pair[1]);
    }
    PNOrderedInput s={0},saved;
    memset(b,0,64); put(b,0x504e4f31); put(b+4,1); put(b+8,4); put(b+12,5); put(b+16,6); put(b+20,1);
    if (pn_ordered_stage(&s,b,48,27,1,17,fixture_keycode) || !pn_ordered_pop(&s,NULL) || s.buttons) _exit(85);
    saved=s; put(b+4,2);
    if (!pn_ordered_stage(&s,b,48,27,1,17,fixture_keycode) || memcmp(&s,&saved,sizeof(s))) _exit(85);
    while (pn_ordered_pop(&s,&e)) { }
    memset(b+12,0,52); put(b+8,5); saved=s;
    if (!pn_ordered_stage(&s,b,48,27,2,17,fixture_keycode) || memcmp(&s,&saved,sizeof(s))) _exit(86); // Held exit refused.
    for (unsigned i=1;i<512;++i) s.key.protocol.held[i]=s.key.delivered[i]=1;
    s.key.protocol.downs=511; put(b+8,3);
    if (pn_ordered_stage(&s,b,48,27,3,17,fixture_keycode)) _exit(86);
    unsigned count=0; while (pn_ordered_pop(&s,&e)) ++count;
    if (count!=513 || s.buttons || pn_sdl_key_mods(s.key.delivered)) _exit(86);
    put(b+4,3); put(b+8,5);
    if (pn_ordered_stage(&s,b,48,27,4,17,fixture_keycode) || !pn_ordered_pop(&s,&e) || e.type!=SDL_QUIT || s.quits!=1) _exit(87);
    if (!pn_ordered_stage(&s,b,48,27,4,17,fixture_keycode)) _exit(87);
    // The peer can stop receiving acknowledgements while its send side remains open.
    // Delivery of the down event must still be followed by an owned release.
    int pair[2]; PNOrderedInput failed={0};
    if (socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair)) _exit(88);
    key_record(b,1,224,1,2); put(b,0x504e4f31);
    if (shutdown(pair[0],SHUT_RD) || write(pair[0],b,64)!=64
        || !pn_ordered_read(&failed,pair[1],&e,48,27,1,17,fixture_keycode) || e.type!=SDL_KEYDOWN
        || failed.error!=5 || !pn_ordered_read(&failed,pair[1],&e,48,27,2,17,fixture_keycode)
        || e.type!=SDL_KEYUP || failed.key.delivered[224] || failed.acknowledged
        || pn_ordered_read(&failed,pair[1],&e,48,27,3,17,fixture_keycode)) _exit(88);
    close(pair[0]); close(pair[1]);
}
