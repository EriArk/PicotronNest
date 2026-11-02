/* Original PicotronNest additions to SDL's dummy output backend.
 * Physical playback remains in Android AudioTrack; only callback pacing is replaced.
 * Included inside SDL_dummyaudio.c, after its existing type definitions. */
static int PN_DUMMY_OpenDevice(_THIS, const char *devname)
{
    (void)devname;
    _this->hidden=SDL_calloc(1,sizeof(*_this->hidden));
    if (!_this->hidden) return SDL_OutOfMemory();
    if (!_this->iscapture) {
        _this->hidden->mixbuf=SDL_malloc(_this->spec.size);
        if (!_this->hidden->mixbuf) return SDL_OutOfMemory();
        SDL_memset(_this->hidden->mixbuf,_this->spec.silence,_this->spec.size);
    }
    pn_clock_init(&_this->hidden->clock,SDL_GetPerformanceCounter(),SDL_GetPerformanceFrequency(),
                  _this->spec.samples,_this->spec.freq);
    return 0;
}
static void PN_DUMMY_CloseDevice(_THIS)
{
    SDL_free(_this->hidden->mixbuf);
    SDL_free(_this->hidden);
}
static Uint8 *PN_DUMMY_GetDeviceBuf(_THIS) { return _this->hidden->mixbuf; }
static void PN_DUMMY_WaitDevice(_THIS)
{
    PNAudioClock *clock=&_this->hidden->clock;
    Uint64 deadline=pn_clock_next(clock,SDL_GetPerformanceCounter()),now;
    while ((now=SDL_GetPerformanceCounter())<deadline) {
        Uint64 ms=(deadline-now)*1000/clock->frequency;
        SDL_Delay((Uint32)(ms ? ms : 1));
    }
}
