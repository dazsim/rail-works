# Rail Works

A more complete rail experience. Build your Train factory, Assemble each carriage to suit your needs and then design and build your rail system one tie at a time.

Rail Works adds life-scale railways to Minecraft, built block by block in the world rather than in a menu. Track is laid as smooth curves between nodes you place, so lines can sweep across the landscape instead of following the block grid. Early industry, starting with making steel, supplies the rails and parts a railway needs.

## Coke oven

Build a 3x3x3 of Coke Oven Bricks, leave out the 4 top corner blocks (23 bricks in all), then hit the brick in the middle of one side with a hammer. The bricks form a beehive oven with an iron door on that side. Put coal in to make coal coke and creosote. Breaking any part of a formed oven gives back all 23 bricks and its contents. Pistons cannot move a formed oven.

## Steel

Craft clay with sand to make fire clay, smelt it into fire bricks and craft 4 fire bricks into a Fire Bricks block. Build a 3x3 shaft 5 blocks tall with a hollow middle (40 blocks), then hit the bottom middle block of one side with a hammer. It forms a Brick Furnace with an iron door on that side and a tap on the side to the right of the door.

Put iron ingots or raw iron and coal coke in the furnace. Molten steel is measured in ingots, and a bucket of it is 9 ingots, the same as a block. 8 iron and 4 coke make 8 ingots of molten steel in about a minute. The furnace holds 36 ingots (4 buckets). Hit the tap with a hammer, or give the furnace a redstone signal, to open it. Molten steel runs out at 1 ingot a second.

Moulds sit under the tap. Put a pattern in a mould, then a block of sand; the pattern comes back and the sand is used up. A rail bar mould takes 1 ingot of steel and makes one rail bar. An ingot mould takes 4 ingots and makes 4 steel ingots. A full mould cools in 20 seconds; hit it with a hammer to knock out the cast. Moulds on roller tables can be pushed along by hand. Clicking the tap side of the furnace or the roller table under a mould works on the mould.

The tap only pours when there is a mould under the spout to catch it, so nothing spills between moulds. Molten steel poured from a bucket glows white hot, burns like lava, spreads 2 blocks and sets into Set Steel after 30 seconds, or straight away if water touches it. Mine Set Steel with an iron pickaxe and craft it into 9 steel ingots. You can also fill a bucket (9 ingots) or a hand ladle (up to 9 ingots) from the furnace door and pour the ladle into a mould.

Breaking any part of a formed furnace gives back all 40 Fire Bricks and the iron and coke inside. Any molten steel in it is lost. Pistons cannot move a formed furnace.

Steel ingots and blocks use the common `c:ingots/steel` and `c:storage_blocks/steel` tags, so other mods that use those tags can share them.

## Track

Track is surveyed with stakes, checked, then laid one tie and one rail at a time. Track does not need or use gravel or any other ballast. Ties sit on whatever ground is there, and ballast is only decoration you can place yourself if you like the look.

**Survey stakes.** Craft 4 sticks and white dye into 8 stakes. Place a stake on the ground and it faces the way you are looking. Hit one stake with the hammer, then hit another up to 96 blocks away to link them. The section is planned as an arc and a straight, or an S curve when both ends face set ways. A plain hammer hit anywhere on a stake selects or links it. Sneak and hit to adjust it: the upper half of its side raises it 1/8 block, the top face lowers it 1/8, and the lower half of its side turns it 2 degrees. The turn goes by the half you hit as you see it: the right half turns the stake to the right (clockwise from above), the left half to the left, and the readout says which way it went and the new bearing. Break a stake to pull it up; that unlinks its sections and drops the stake. A stake with ties or rails laid on its sections will not break until the track is taken up (in creative it breaks and drops everything laid, like a hidden node). A selection is cleared after a minute or by hitting the same stake again.

**Checks.** Linking shows the length, the grade as 1 in N and the radius. Radius under 30 or a grade steeper than 1 in 20 shows in red, and steeper than 1 in 40 is marked steep. The Theodolite shows the same reading for any stake you sight on. A linked section shows a chalk centre line, ghost ties and the clearance edges (6 blocks wide on straights, 7 on curves, 6.5 high). Blocks in the way are counted in the reading, but they do not stop you laying. Every tie needs solid ground under it. A tie over air will not go down, its ghost turns red and the reading counts the ties over air in red. The warning stays on screen while you hold ties near the spot, until you fix the ground or walk away. Any solid block will do.

**Laying.** Hold ties and walk along the chalk line holding use; ties go down ahead of you. Then do the same with rail bars. When every tie and rail is down the section goes live, the stakes come back to you and a small hidden node stays at each end. Hammer a section to see its progress. Sneak and hit near laid track with the hammer to take it up, rails first, then ties, and you get each piece back. Breaking a hidden node drops everything laid on its sections.

**Continuing track.** Live track ends are hidden, so while you hold a stake or the hammer each open end shows a yellow post with arrows pointing the way new track would go. Use a stake on that post to turn the end back into a stake, or just place a stake right next to it and it goes on the end for you (sneak to place a separate stake instead). Then link it to a new stake with the hammer as usual.

**Points.** Link a second section from a stake that already has one and that end becomes a set of points. Throw them by hitting near the toe with the hammer, by pulling a Ground Frame placed within 12 blocks of the toe, or by powering the Ground Frame with redstone (powered sets the second route). While the frame is powered it cannot be pulled by hand, and a frame placed on a live signal sets the second route straight away. The rail at the crossing (frog) on each route takes 1 extra rail bar.

**Buffer stop.** Use a Buffer Stop near the open end of a live section to fit a GWR style buffer. Sneak and hammer it to take it off.

**Recipes.** Stakes: 4 sticks and white dye make 8. Theodolite: iron, spyglass and iron over stick, compass and stick, over a stick. Ground Frame: a lever over iron, treated beam and iron. Buffer Stop: treated beam, lantern, treated beam over rail bar, red dye, rail bar over steel ingot, treated beam, steel ingot.

**Known issues.** Past 40 blocks track is drawn without chairs and rail webs, and past 192 blocks it is not drawn at all. A very long line in view still costs frame rate on slow machines.

## Development

The formed coke oven models, textures, blockstates and shapes are generated by `python3 tools/beehive-model/gen.py` (needs Pillow). Rerun it after changing the oven geometry or the brick texture. The brick furnace model is generated the same way by `python3 tools/brick-furnace-model/gen.py`, and the steel textures, models, recipes, tags and loot tables by `python3 tools/steel-art/gen.py` and `python3 tools/steel-art/data.py`.
