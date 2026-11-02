/* SDL owns enumeration, mapping and events for our Android-backed controllers. */
static SDL_Joystick *pn_joysticks[4];
static int pn_gamepad_apply(unsigned slot,const PNGamepad *state) {
    PNGamepad neutral={0};
    if (!state->connected) state=&neutral;
    SDL_Joystick *joystick=pn_joysticks[slot];
    if (!joystick) {
        SDL_VirtualJoystickDesc desc; SDL_zero(desc);
        desc.version=SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
        desc.type=SDL_JOYSTICK_TYPE_GAMECONTROLLER; desc.naxes=6; desc.nbuttons=15;
        desc.axis_mask=0x3f; desc.button_mask=0x7fff; desc.name="PicotronNest Controller";
        int index=SDL_JoystickAttachVirtualEx(&desc);
        if (index<0) return -1;
        joystick=SDL_JoystickOpen(index);
        if (!joystick) { SDL_JoystickDetachVirtual(index); return -1; }
        pn_joysticks[slot]=joystick; SDL_JoystickSetPlayerIndex(joystick,(int)slot);
        printf("PICONEST_GAMEPAD_ATTACH slot=%u instance=%d controller=%d\n",slot,SDL_JoystickInstanceID(joystick),SDL_IsGameController(index));
    }
    for (unsigned i=0;i<6;++i) {
        int value=state->axes[i];
        if (i>=4) value=value==32767 ? 32767 : value*2-32768; // Preserve both signed endpoints.
        if (SDL_JoystickSetVirtualAxis(joystick,(int)i,(Sint16)value)) return -1;
    }
    for (unsigned i=0;i<15;++i)
        if (SDL_JoystickSetVirtualButton(joystick,(int)i,(Uint8)((state->buttons>>i)&1))) return -1;
    SDL_JoystickUpdate(); return 0;
}
static int pn_gamepad_initialize(void) {
    PNGamepad neutral={0};
    // Enumerate stable logical slots before Picotron opens controllers. Creating
    // a controller with its first button already held can lose that transition.
    for (unsigned slot=0;slot<4;++slot) if (pn_gamepad_apply(slot,&neutral)) return -1;
    return 0;
}
