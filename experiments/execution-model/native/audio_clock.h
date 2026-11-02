/* PicotronNest: sample-count clock, independent of callback cost and millisecond rounding. */
#ifndef PN_AUDIO_CLOCK_H
#define PN_AUDIO_CLOCK_H
#include <stdint.h>
typedef struct {
    uint64_t deadline, remainder, frequency;
    unsigned samples, rate;
} PNAudioClock;
static void pn_clock_init(PNAudioClock *clock,uint64_t now,uint64_t frequency,unsigned samples,unsigned rate) {
    clock->deadline=now; clock->remainder=0; clock->frequency=frequency;
    clock->samples=samples; clock->rate=rate;
}
static uint64_t pn_clock_next(PNAudioClock *clock,uint64_t now) {
    uint64_t numerator=(uint64_t)clock->samples*clock->frequency;
    uint64_t period=numerator/clock->rate;
    /* Do not burst through a long suspend or device stall to catch an obsolete clock. */
    if (now>clock->deadline && now-clock->deadline>period*2) {
        clock->deadline=now; clock->remainder=0;
    }
    numerator+=clock->remainder;
    clock->deadline+=numerator/clock->rate;
    clock->remainder=numerator%clock->rate;
    return clock->deadline;
}
#endif
