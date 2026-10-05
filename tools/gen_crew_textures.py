"""Ship crew skins (2026-10-05): the Armada marine = the pirate crew's texture with the olive coat re-dyed Armada blue
(skin, hair, boots and the hat keep their colours). python tools/gen_crew_textures.py"""
import colorsys, pathlib
from PIL import Image
T = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/pixelpirates/textures/entity"
src = Image.open(T / "pirate.png").convert("RGBA")
out = src.copy()
px = out.load()
for y in range(out.height):
    for x in range(out.width):
        r, g, b, a = px[x, y]
        if a == 0: continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if 60 / 360 <= h <= 110 / 360 and s > 0.3:                        # the olive coat -> navy blue, a touch brighter
            nr, ng, nb = colorsys.hsv_to_rgb(222 / 360, min(1, s * 1.15), min(1, v * 1.35))
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
        elif (h < 20 / 360 or h > 340 / 360) and s > 0.45:                # the red bandana -> an Armada black
            px[x, y] = (int(28 + v * 30), int(28 + v * 30), int(36 + v * 36), a)
out.save(T / "navy_marine.png")
print("navy_marine.png written")
