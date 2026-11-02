-- Owned manual check. Run inside official Picotron; no keyboard fallback.
local previous = ""
local seen = ""

function _update()
    local held = ""
    for button = 0, 15 do
        if btn(button, 0, 1) then held ..= button .. "," end
    end
    if held != previous then
        printh("PN_GAME=" .. held)
        if held != "" then seen ..= " [" .. held .. "]" end
        previous = held
    end
end

function _draw()
    cls(1)
    print("CONTROLLER INPUT", 20, 20, 7)
    print("Held: " .. previous, 20, 40, 11)
    print("Seen: " .. seen, 20, 60, 7)
end
