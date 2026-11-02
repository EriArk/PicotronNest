-- Original PicotronNest showcase. Run in the official Picotron runtime.
-- Move the small spacecraft with the D-pad. No external assets.
local x, y = 240, 148

function _update()
    if btn(0) then x -= 2 end
    if btn(1) then x += 2 end
    if btn(2) then y -= 2 end
    if btn(3) then y += 2 end
    x = mid(12, x, 468)
    y = mid(80, y, 224)
end

function _draw()
    cls(1)
    for i = 1, 80 do
        pset((i * 137) % 480, (i * 73) % 230, i % 3 == 0 and 7 or 13)
    end
    circfill(356, 112, 52, 12)
    circfill(342, 101, 44, 1)
    circfill(353, 118, 25, 13)
    rectfill(0, 232, 479, 269, 0)
    print("P O C K E T   O R B I T", 24, 24, 7)
    print("A SMALL PROJECT. A WHOLE WORKSTATION.", 24, 40, 6)
    print("MOVE WITH THE D-PAD", 24, 246, 6)
    print("MADE IN PICOTRON", 352, 246, 12)
    line(x - 9, y + 7, x, y - 12, 7)
    line(x, y - 12, x + 9, y + 7, 7)
    line(x - 9, y + 7, x + 9, y + 7, 12)
    rectfill(x - 3, y - 1, x + 3, y + 6, 12)
    line(x - 2, y + 10, x, y + 16, 9)
    line(x + 2, y + 10, x, y + 16, 10)
end
