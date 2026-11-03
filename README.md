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

**Survey stakes.** Craft 4 sticks and white dye into 8 stakes. Place a stake on the ground and it faces the way you are looking. Hit one stake with the hammer, then hit another up to 96 blocks away to link them. The section is planned as an arc and a straight, or an S curve when both ends face set ways. A plain hammer hit anywhere on a stake selects or links it. Sneak and hit to adjust it: the upper half of its side raises it 1/8 block, the top face lowers it 1/8, and the lower half of its side turns it 2 degrees. The turn goes by the half you hit as you see it: the right half turns the stake to the right (clockwise from above), the left half to the left, and the readout says which way it went and the new bearing. Break a stake to pull it up; that unlinks its unlaid sections and drops the stake. Breaking never removes laid ties or rails: a stake or hidden node with track laid on its sections says to take the track up first, and in creative it comes straight back with everything still in place. Hitting a stake also lists its sections with their length, bearing and how much is laid, so stray links are easy to spot, and every unlaid section shows its ghost ties and chalk line. Hitting a stake that is already linked to the selected one selects it instead, so you can start a branch from it. A selection is cleared after a minute or by hitting the same stake again.

**Checks.** Linking shows the length, the grade as 1 in N and the radius. Radius under 30 or a grade steeper than 1 in 20 shows in red, and steeper than 1 in 40 is marked steep. The Theodolite shows the same reading for any stake you sight on. A linked section shows a chalk centre line, ghost ties and the clearance edges (6 blocks wide on straights, 7 on curves, 6.5 high). Blocks in the way are counted in the reading, but they do not stop you laying. The count updates by itself a moment after a block inside the clearance is placed or broken. Stakes, hidden nodes, ground frames and buffer stops never count as in the way. Every tie needs solid ground under it. A tie over air will not go down, its ghost turns red and the reading counts the ties over air in red. The warning stays on screen while you hold ties near the spot, until you fix the ground or walk away. Any solid block will do.

**Laying.** Hold ties and walk along the chalk line holding use; ties go down ahead of you. Then do the same with rail bars. When every tie and rail is down the section goes live, the stakes come back to you and a small hidden node stays at each end. Hammer a section to see its progress. On a partly laid section each missing tie has an orange post, and once the ties are down each missing rail has a yellow post on each side, and the progress reading gives the distance and compass direction to the nearest missing piece. When a section goes live, any other sections at its stakes with nothing laid on them are dropped and the reading says so, so the stakes can come back. Sneak and hit near laid track with the hammer to take it up, rails first, then ties, and you get each piece back.

**Continuing track.** Live track ends are hidden, so while you hold a stake or the hammer each open end shows a yellow post with arrows pointing the way new track would go. Use a stake on that post to turn the end back into a stake, or just place a stake right next to it and it goes on the end for you (sneak to place a separate stake instead). Then link it to a new stake with the hammer as usual.

**Points.** Link a second section from a stake that already has one and that end becomes a set of points. Throw them by hitting near the toe with the hammer, by pulling a Ground Frame placed within 12 blocks of the toe, or by powering the Ground Frame with redstone (powered sets the second route). While the frame is powered it cannot be pulled by hand, and a frame placed on a live signal sets the second route straight away. The rail at the crossing (frog) on each route takes 1 extra rail bar. Two sections only form points when they leave the stake the same way. If they leave opposite ways the stake is just a bend, and if two stakes sit side by side they are separate ends. The frame and the hammer say which, how far the nearest points are, and how far the frame is from the toe, so there is always a message. Hitting near the toe also shows the progress and clearance of the nearest unlaid section there.

**Straight lock.** For station platforms, sneak and hammer the chalk line of an unlaid section to lock it as a straight between its two stakes. Both stakes turn to face along the line and the sections either side re-plan to meet it smoothly. The chalk line turns blue and the reading says locked straight. Raising and lowering its stakes still works and the section stays straight, but turning a stake at either end is refused until you unlock it by sneaking and hammering the line again. Locking is refused if it would move a stake with track laid on its sections, or turn a stake held by another locked straight. The sneak hammer always takes up first: if any laid tie or rail is within 3 blocks of where you hit, it takes that up; only when nothing is laid nearby does it lock or unlock the nearest section.

**Buffer stop.** Use a Buffer Stop near the open end of a live section to fit a GWR style buffer. Sneak and hammer it to take it off.

**Recipes.** Stakes: 4 sticks and white dye make 8. Theodolite: iron, spyglass and iron over stick, compass and stick, over a stick. Ground Frame: a lever over iron, treated beam and iron. Buffer Stop: treated beam, lantern, treated beam over rail bar, red dye, rail bar over steel ingot, treated beam, steel ingot.

**Known issues.** Past 40 blocks track is drawn without chairs and rail webs, and past 192 blocks it is not drawn at all. A very long line in view still costs frame rate on slow machines.

## Hand trolley

A pump trolley for getting about on live track. Cast the wheels and axles, press them into wheelsets, then build the trolley on the line.

**Parts.** A wheel pattern in an ingot mould takes 4 ingots of steel and casts 4 wheels, enough for both wheelsets. A whole trolley takes 10 ingots. An axle pattern in a rail bar mould takes 1 ingot and casts 1 axle. Put an axle on a roller table, then a wheel on each side, and hit it with a hammer to press a wheelset. Frame: 6 treated planks over 3 treated beams. Pump: a rail bar between 2 steel ingots, over a treated beam. Brake: a steel ingot and a lever. Wheel pattern and axle pattern: treated planks.

**Building.** The frame can only go down on live track. Use 2 wheelsets, the pump and the brake on it in that order; each part shows as it goes on. Sneak and hammer the trolley to take it off as one item, keeping the tool box contents. A part built trolley comes back as the frame and its parts.

**Riding.** Use the trolley to get on. There is room for 2 at the pump handles and 2 on the seats. Forward and back pump it, jump brakes and sneak gets off. The brake stops it in about 6 blocks from its top speed of 10, or 3 to 4 from pumping speed, and it holds on a 1 in 20. One pumper reaches about 6 blocks a second on the level in about 10 seconds, and two about 7.5. It climbs a 1 in 20 at about 3, runs away downhill if not braked and coasts no faster than 10. The hand brake goes on when the last rider gets off. Sneak and use with an empty hand, or use the tool box, to open its 9 slots. The speed and grade show above the hotbar, and a run report (distance, steepest grade, tightest curve, points passed and clearance) shows in chat when you get off. Chunks 3x3 around it stay loaded while someone rides.

**On the line.** Facing points are taken the way they are set. Trailing points set against the trolley are thrown as it runs through, with a clatter. It bumps to a stop at a buffer stop, and at an open end of track it brakes to a stop at the last tie. A block in its way stops it and the message says where. Mobs on the line are nudged aside.

## Machine shop

Hand cranked machines for finishing castings. They are the first step to a bogie for a proper carriage.

**Castings.** New patterns go in the moulds the same way as the old ones. In an ingot mould (4 ingots): a carriage wheel pattern casts 2 rough carriage wheels and an axle box pattern casts 4 rough axle boxes. In a rail bar mould (1 ingot): a carriage axle pattern casts 1 rough carriage axle, a plate pattern 1 steel plate and a spring pattern 1 bogie spring. Patterns are treated planks: carriage wheel a plus sign, carriage axle a column of 3, plate an X, axle box 2 columns of 2, spring a chevron of 3. The pattern goes in an empty mould and comes back when the sand goes in, as before. A full mould shows the old cast shape whatever the pattern.

**Lathe.** 3 steel ingots over a rail bar, lever and rail bar, over 2 treated beams. It is 3 blocks wide across your view, with the crank on your left. Use a rough carriage wheel or rough carriage axle on it to put it on, then hold right click on the red crank wheel to turn it. A wheel takes 12 seconds of cranking and an axle 8. Letting go stops the machine and keeps the progress. When it is finished, let go and right click again to take the part off. Sneak with an empty hand to take an unfinished part back off.

**Pillar drill.** 2 steel ingots either side of a rail bar, over an iron ingot, over a treated beam. It is 2 blocks tall and is cranked from the top block. A steel plate becomes a drilled plate in 4 seconds and a rough axle box becomes an axle box in 3.

**Cranking.** Turning a crank costs food at about the rate of sprinting, and you cannot crank when you are too hungry to sprint. One person can crank a machine at a time. Progress, the part and its percentage show above the hotbar. Each machine has a wooden belt pulley on the side; it does nothing yet and is there for a line shaft later. Breaking a machine drops the part on it.

## Pannier tank engine

A small six coupled tank engine, built on the track in 11 stages like a carriage. Place the engine frames on the track, then hold the next part (its name shows above the hotbar) and use it on the engine. Riveted stages need rivets and a hammer; the others take a few hammer blows. The ghost shows where the next part goes.

**Parts.** New treated plank patterns cast in the ingot mould (4 ingots): engine wheel pattern (a ring of 8) a rough engine wheel, cylinder pattern (a square of 4) a rough cylinder, chimney pattern (a U) the chimney, smokebox door pattern (2 rows of 3) the door. Rough engine wheels and cylinders are finished on the lathe (18 and 15 seconds), coupling rods are drilled from a steel beam on the pillar drill (5 seconds). Boiler and smokebox plate is rolled from steel plate on the new plate rolls (6 seconds): 3 rail bars over a steel ingot, lever and steel ingot, over 2 treated beams. It is 3 blocks wide with the crank wheel on the right. The rest is crafted from steel plate, beams, copper, glass and treated planks; the recipe book has them. An engine takes about 150 steel ingots and 16 copper.

**Driving.** Fill the tanks with water buckets, put coal or coke in the bunker and light the fire with flint and steel or a fire charge at the firehole. Use the reverser to take the controls. Forward and back open and close the regulator, left and right move the reverser, jump holds the brake and sprint blows the whistle. Green or black dye paints it.

**Endless train.** For packs and servers that want trains running without a crew, set `endlessTrain=true` in `config/railworks.properties` (the file is written with `endlessTrain=false` on first start; the same file on both loaders) and restart. Every finished engine then runs at full line speed with nobody driving and needs no coal or water. It runs the way its reverser is set, or forwards in mid gear. Points, couplings and collisions work as normal.

## Building

The build uses [Stonecutter](https://stonecutter.kikugie.dev/) so both loaders come from the one source tree in `src`. Loader specific values are in `stonecutter.properties.toml`, the version is in `gradle.properties` and the nodes built are listed in `settings.gradle.kts`.

Build with JDK 25: `./gradlew build` builds both loaders, `./gradlew :26.1.2-fabric:build` builds one. Jars end up in `versions/26.1.2-<loader>/build/libs/`. Add `-Pversion=0.10.0-alpha.2` for a test jar. `./gradlew :26.1.2-neoforge:runClient` runs a dev client.

## Development

The formed coke oven models, textures, blockstates and shapes are generated by `python3 tools/beehive-model/gen.py` (needs Pillow). Rerun it after changing the oven geometry or the brick texture. The brick furnace model is generated the same way by `python3 tools/brick-furnace-model/gen.py`, and the steel textures, models, recipes, tags and loot tables by `python3 tools/steel-art/gen.py` and `python3 tools/steel-art/data.py`. The hand trolley item sprites and texture come from `python3 tools/trolley-art/gen.py`, and the machine shop item sprites from `python3 tools/machine-art/gen.py`. The machines are drawn with the hand trolley texture.
