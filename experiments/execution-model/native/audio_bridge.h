/* Original callback-audio adapter. SDL keeps callback timing; Android owns physical output. */
#ifndef PN_AUDIO_BRIDGE_H
#define PN_AUDIO_BRIDGE_H
#define PN_AUDIO_BLOCK 16384
#define PN_AUDIO_SLOTS 8
typedef struct {
    SDL_AudioDeviceID device;
    SDL_AudioCallback callback;
    void *userdata;
    SDL_AudioStream *convert;
    SDL_mutex *mutex;
    SDL_cond *wake;
    SDL_Thread *thread;
    int fd,closing,failed;
    unsigned read,write,count,dropped;
    unsigned sizes[PN_AUDIO_SLOTS];
    unsigned char blocks[PN_AUDIO_SLOTS][PN_AUDIO_BLOCK];
} PNAudio;
static PNAudio *pn_audio;
static void pn_audio_u32(unsigned char *b,unsigned n) { for (int i=0;i<4;++i) b[i]=(unsigned char)(n>>(i*8)); }
static int pn_audio_send(PNAudio *a,const void *bytes,size_t count) {
    const unsigned char *p=bytes; Uint64 deadline=now_ms()+500;
    while (count && now_ms()<deadline) {
        ssize_t n=send(a->fd,p,count,MSG_NOSIGNAL);
        if (n<0 && errno==EINTR) continue;
        if (n<=0) return -1;
        p+=n; count-=(size_t)n;
    }
    return count ? -1 : 0;
}
static int pn_audio_worker(void *opaque) {
    PNAudio *a=opaque; unsigned char block[PN_AUDIO_BLOCK],size[4];
    for (;;) {
        SDL_LockMutex(a->mutex);
        while (!a->closing && !a->failed && !a->count) SDL_CondWait(a->wake,a->mutex);
        if (a->closing || a->failed) { SDL_UnlockMutex(a->mutex); break; }
        unsigned n=a->sizes[a->read]; memcpy(block,a->blocks[a->read],n);
        a->read=(a->read+1)%PN_AUDIO_SLOTS; --a->count;
        SDL_UnlockMutex(a->mutex);
        pn_audio_u32(size,n);
        if (pn_audio_send(a,size,4) || pn_audio_send(a,block,n)) {
            SDL_LockMutex(a->mutex); a->failed=1; SDL_UnlockMutex(a->mutex); break;
        }
    }
    return 0;
}
static void pn_audio_failed(PNAudio *a) {
    SDL_LockMutex(a->mutex); a->failed=1; SDL_CondSignal(a->wake); SDL_UnlockMutex(a->mutex);
}
static void pn_audio_callback(void *opaque,Uint8 *bytes,int count) {
    PNAudio *a=opaque;
    a->callback(a->userdata,bytes,count);
    if (SDL_AudioStreamPut(a->convert,bytes,count)<0) { pn_audio_failed(a); return; }
    unsigned char block[PN_AUDIO_BLOCK]; int n;
    while ((n=SDL_AudioStreamGet(a->convert,block,sizeof(block)))>0) {
        SDL_LockMutex(a->mutex);
        if (!a->failed && !a->closing) {
            // Drop the oldest block under backpressure; never stall Picotron's audio callback.
            if (a->count==PN_AUDIO_SLOTS) { a->read=(a->read+1)%PN_AUDIO_SLOTS; --a->count; ++a->dropped; }
            memcpy(a->blocks[a->write],block,(size_t)n); a->sizes[a->write]=(unsigned)n;
            a->write=(a->write+1)%PN_AUDIO_SLOTS; ++a->count; SDL_CondSignal(a->wake);
        }
        SDL_UnlockMutex(a->mutex);
    }
    if (n<0) pn_audio_failed(a);
}
static void pn_audio_free(PNAudio *a) {
    if (!a) return;
    if (a->thread) {
        SDL_LockMutex(a->mutex); a->closing=1; SDL_CondSignal(a->wake); SDL_UnlockMutex(a->mutex);
        SDL_WaitThread(a->thread,NULL);
    }
    if (a->fd>=0) close(a->fd);
    if (a->convert) SDL_FreeAudioStream(a->convert);
    if (a->wake) SDL_DestroyCond(a->wake);
    if (a->mutex) SDL_DestroyMutex(a->mutex);
    free(a);
}
SDL_AudioDeviceID SDL_OpenAudioDevice(const char *name,int capture,const SDL_AudioSpec *desired,SDL_AudioSpec *obtained,int changes) {
    SDL_AudioDeviceID (*real)(const char *,int,const SDL_AudioSpec *,SDL_AudioSpec *,int)=symbol("SDL_OpenAudioDevice");
    const char *endpoint=getenv("PICONEST_AUDIO_SOCKET");
    if (!endpoint || capture) return real(name,capture,desired,obtained,changes);
    if (pn_audio || !desired || !desired->callback) { SDL_SetError("PicotronNest supports one callback playback device"); return 0; }
    PNAudio *a=calloc(1,sizeof(*a));
    if (!a) { SDL_OutOfMemory(); return 0; }
    a->fd=-1; a->callback=desired->callback; a->userdata=desired->userdata;
    a->mutex=SDL_CreateMutex(); a->wake=SDL_CreateCond();
    if (!a->mutex || !a->wake) { pn_audio_free(a); return 0; }
    SDL_AudioSpec requested=*desired,actual;
    requested.callback=pn_audio_callback; requested.userdata=a;
    a->device=real(name,0,&requested,&actual,obtained ? changes : 0);
    if (!a->device) { pn_audio_free(a); return 0; }
    // SDL opens paused. Conversion/transport are ready before the caller can unpause.
    a->convert=SDL_NewAudioStream(actual.format,actual.channels,actual.freq,AUDIO_S16LSB,2,48000);
    struct sockaddr_un address={0}; size_t length=strlen(endpoint);
    int good=a->convert && length>0 && length<=sizeof(address.sun_path)-2;
    if (good) {
        address.sun_family=AF_UNIX; memcpy(address.sun_path+1,endpoint,length);
        a->fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);
        struct timeval timeout={.tv_sec=0,.tv_usec=250000}; struct ucred peer; socklen_t size=sizeof(peer);
        good=a->fd>=0 && !setsockopt(a->fd,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout))
            && !connect(a->fd,(struct sockaddr *)&address,(socklen_t)(offsetof(struct sockaddr_un,sun_path)+1+length))
            && !getsockopt(a->fd,SOL_SOCKET,SO_PEERCRED,&peer,&size) && size==sizeof(peer) && peer.uid==getuid();
    }
    if (good) {
        unsigned char header[24];
        const unsigned fields[]={0x504e4131,1,48000,2,16,PN_AUDIO_BLOCK};
        for (unsigned i=0;i<6;++i) pn_audio_u32(header+4*i,fields[i]);
        good=!pn_audio_send(a,header,sizeof(header));
    }
    if (good) { a->thread=SDL_CreateThread(pn_audio_worker,"picotron-audio",a); good=a->thread!=NULL; }
    if (!good) {
        void (*close_device)(SDL_AudioDeviceID)=symbol("SDL_CloseAudioDevice"); close_device(a->device);
        pn_audio_free(a); SDL_SetError("PicotronNest audio transport unavailable"); return 0;
    }
    if (obtained) { *obtained=actual; obtained->callback=desired->callback; obtained->userdata=desired->userdata; }
    pn_audio=a;
    printf("PICONEST_AUDIO_OPEN rate=%d channels=%u format=%u output=48000-S16LE-stereo\n",actual.freq,actual.channels,actual.format);
    return a->device;
}
void SDL_CloseAudioDevice(SDL_AudioDeviceID device) {
    void (*real)(SDL_AudioDeviceID)=symbol("SDL_CloseAudioDevice");
    PNAudio *a=pn_audio;
    real(device); // Joins the SDL callback before releasing its userdata.
    if (a && a->device==device) {
        pn_audio=NULL;
        SDL_LockMutex(a->mutex); unsigned dropped=a->dropped; int failed=a->failed; SDL_UnlockMutex(a->mutex);
        printf("PICONEST_AUDIO_CLOSE dropped=%u failed=%d\n",dropped,failed); pn_audio_free(a);
    }
}
void SDL_QuitSubSystem(Uint32 flags) {
    void (*real)(Uint32)=symbol("SDL_QuitSubSystem");
    if ((flags&SDL_INIT_AUDIO) && pn_audio) SDL_CloseAudioDevice(pn_audio->device);
    real(flags);
}
void SDL_Quit(void) {
    void (*real)(void)=symbol("SDL_Quit");
    if (pn_audio) SDL_CloseAudioDevice(pn_audio->device);
    real();
}
#endif
