/* Owned event translation, shared with native tests using pinned SDL2 headers. */
#ifndef PN_POINTER_SDL_H
#define PN_POINTER_SDL_H
#include "SDL_events.h"
#include "pointer_protocol.h"
#include <string.h>

static Uint32 pn_mouse_mask(uint32_t buttons) {
    return (buttons & 1 ? SDL_BUTTON_LMASK : 0) | (buttons & 2 ? SDL_BUTTON_RMASK : 0);
}

static unsigned pn_pointer_events(SDL_Event events[4], const PNPointer *before, const PNPointer *after,
                                  Uint32 timestamp, Uint32 window) {
    memset(events, 0, 4 * sizeof(*events));
    unsigned count = 1;
    events[0].type = SDL_MOUSEMOTION;
    events[0].motion.timestamp = timestamp; events[0].motion.windowID = window;
    events[0].motion.state = pn_mouse_mask(after->buttons);
    events[0].motion.x = after->x; events[0].motion.y = after->y;
    events[0].motion.xrel = after->x - before->x; events[0].motion.yrel = after->y - before->y;
    for (uint32_t bit = 1; bit <= 2; bit <<= 1) {
        if (!((before->buttons ^ after->buttons) & bit)) continue;
        SDL_Event *button = &events[count++];
        button->type = after->buttons & bit ? SDL_MOUSEBUTTONDOWN : SDL_MOUSEBUTTONUP;
        button->button.timestamp = timestamp; button->button.windowID = window;
        button->button.button = bit == 1 ? SDL_BUTTON_LEFT : SDL_BUTTON_RIGHT;
        button->button.state = after->buttons & bit ? SDL_PRESSED : SDL_RELEASED;
        button->button.clicks = 1;
        button->button.x = after->x; button->button.y = after->y;
    }
    if (after->wheel_x || after->wheel_y) {
        SDL_Event *wheel = &events[count++];
        wheel->type = SDL_MOUSEWHEEL;
        wheel->wheel.timestamp = timestamp; wheel->wheel.windowID = window;
        wheel->wheel.direction = SDL_MOUSEWHEEL_NORMAL;
        wheel->wheel.x = after->wheel_steps_x; wheel->wheel.y = after->wheel_steps_y;
        wheel->wheel.preciseX = (float)after->wheel_x / PN_WHEEL_UNIT;
        wheel->wheel.preciseY = (float)after->wheel_y / PN_WHEEL_UNIT;
        wheel->wheel.mouseX = after->x; wheel->wheel.mouseY = after->y;
    }
    return count;
}
#endif
