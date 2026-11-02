#include <assert.h>
#include <stdio.h>
#include "../native/audio_clock.h"
int main(void) {
    const unsigned rates[]={44100,48000,22050},samples[]={128,512,1024};
    for (unsigned r=0;r<3;r++) for (unsigned s=0;s<3;s++) {
        PNAudioClock clock; pn_clock_init(&clock,0,1000000,samples[s],rates[r]);
        uint64_t now=0,deadline=0;
        for (unsigned i=1;i<=100000;i++) {
            /* Variable callback work and sub-millisecond wake jitter must not accumulate. */
            now=deadline+100+(i%7)*37;
            deadline=pn_clock_next(&clock,now);
            assert(deadline==(uint64_t)i*samples[s]*1000000/rates[r]);
        }
        now=deadline+10000000;
        uint64_t resumed=pn_clock_next(&clock,now);
        assert(resumed>now && resumed-now==(uint64_t)samples[s]*1000000/rates[r]);
        assert(pn_clock_next(&clock,resumed)>resumed);
    }
    puts("PASS fractional audio clock, variable callback cost, jitter, long-suspend reset");
    return 0;
}
