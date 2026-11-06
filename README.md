# Tfa Mistake Tracker

Tracks mistakes made by players throughout The Fractured Archives.


---
This plugin will track mistakes for you and your teammates in The Fractured Archives.

By default, when detecting a mistake, all players with this plugin will receive a public message of the mistake, a chat
overhead above the player who made the mistake, and the mistake will be added to the Tfa Mistake Tracker side-panel.

Current mistakes being tracked:

* ![death](src/main/resources/com/tfamistaketracker/death.png) **Deaths** throughout the raid
    * ![death-ag](src/main/resources/com/tfamistaketracker/death-ag.png) **Death in the Archive Guardian room**
    * ![death-bork](src/main/resources/com/tfamistaketracker/death-bork.png) **Death in the Bork room**
    * ![death-dreadnoth](src/main/resources/com/tfamistaketracker/death-dreadnoth.png) **Death in the Dreadnoth room**
    * ![death-xarth](src/main/resources/com/tfamistaketracker/death-xarth.png) **Death in the Xarth room**
    * ![death-ocellus](src/main/resources/com/tfamistaketracker/death-ocellus.png) **Death at Ocellus**
    * ![death](src/main/resources/com/tfamistaketracker/death.png) **Death in the Ezekiel, Moia or Dagon'hai rooms**
* ![archive-guardian](src/main/resources/com/tfamistaketracker/archive-guardian.png) **Archive Guardian**
    * ![ag-fall](src/main/resources/com/tfamistaketracker/ag-fall.png) **Fall** damage
    * ![ag-melee](src/main/resources/com/tfamistaketracker/ag-melee.png) **Melee** damage while under him
    * ![ag-range-attack](src/main/resources/com/tfamistaketracker/ag-range-attack.png) **Rock** prayer miss
    * ![ag-mushroom](src/main/resources/com/tfamistaketracker/ag-mushroom.png) **Mushroom explosion** damage
    * ![ag-vine](src/main/resources/com/tfamistaketracker/ag-vine.png) **Vine** damage
* ![bork](src/main/resources/com/tfamistaketracker/bork.png) **Bork**
    * ![bork-stomp](src/main/resources/com/tfamistaketracker/bork-stomp.png) **Stomp wave** damage
    * ![bork-melee](src/main/resources/com/tfamistaketracker/bork-melee.png) **Melee** prayer miss
    * ![bork-smorks-aa](src/main/resources/com/tfamistaketracker/bork-smorks-aa.png) **The Smorks ranged and magic attacks** prayer miss
    * ![bork-smorks-fireball](src/main/resources/com/tfamistaketracker/bork-smorks-fireball.png) **The Smorks fireball** damage
    * ![bork-smorks-fire-tile](src/main/resources/com/tfamistaketracker/bork-smorks-fire-tile.png) **Room fire** damage
* ![dreadnoth](src/main/resources/com/tfamistaketracker/dreadnoth.png) **Dreadnoth**
    * ![dreadnoth-slam](src/main/resources/com/tfamistaketracker/dreadnoth-slam.png) **Jump slam** prayer miss
    * ![dreadnoth-range](src/main/resources/com/tfamistaketracker/dreadnoth-range.png) **Ranged attack** prayer miss
    * ![dreadnoth-blood](src/main/resources/com/tfamistaketracker/dreadnoth-blood.png) **Blood pool** damage
    * ![dreadnoth-blood-ball](src/main/resources/com/tfamistaketracker/dreadnoth-blood-ball.png) **Blood ball** damage
    * ![dreadnoth-landed](src/main/resources/com/tfamistaketracker/dreadnoth-landed.png) **Landing** on you
    * ![dreadnoth-jelly](src/main/resources/com/tfamistaketracker/dreadnoth-jelly.png) **Jelly** prayer miss
    * ![dreadnoth-inside](src/main/resources/com/tfamistaketracker/dreadnoth-inside.png) **Standing inside** Dreadnoth
* ![xarth](src/main/resources/com/tfamistaketracker/xarth.png) **Xarth**
    * ![xarth-tether](src/main/resources/com/tfamistaketracker/xarth-tether.png) **Tether fireball** damage
    * ![xarth-explosion](src/main/resources/com/tfamistaketracker/xarth-explosion.png) **Flame blast explosion** damage
    * ![xarth-fire](src/main/resources/com/tfamistaketracker/xarth-fire.png) **Room fire** damage
    * ![xarth-melee](src/main/resources/com/tfamistaketracker/xarth-melee.png) **Melee** prayer miss
    * ![xarth-heal](src/main/resources/com/tfamistaketracker/xarth-heal.png) **Corpse heal** team mistake
* ![ocellus-p1](src/main/resources/com/tfamistaketracker/ocellus-p1.png) **Ocellus P1**
    * ![ocellus-p1-aa](src/main/resources/com/tfamistaketracker/ocellus-p1-aa.png) **Magic and ranged attacks** prayer miss
    * ![ocellus-p1-slam](src/main/resources/com/tfamistaketracker/ocellus-p1-slam.png) **Slam** damage
* ![ocellus-p2](src/main/resources/com/tfamistaketracker/ocellus-p2.png) **Ocellus P2**
    * ![ocellus-p2-portals](src/main/resources/com/tfamistaketracker/ocellus-p2-portals.png) **Portal projectile** damage
    * ![ocellus-p2-purple](src/main/resources/com/tfamistaketracker/ocellus-p2-purple.png) **Purple tile** damage
* ![ocellus-p3](src/main/resources/com/tfamistaketracker/ocellus-p3.png) **Ocellus P3 / P4**
    * ![ocellus-p3-void-grief](src/main/resources/com/tfamistaketracker/ocellus-p3-void-grief.png) **Void bomb and void floor** damage
    * ![ocellus-p3-aoe](src/main/resources/com/tfamistaketracker/ocellus-p3-aoe.png) **Shadow slash** damage
    * ![ocellus-p3-insolence](src/main/resources/com/tfamistaketracker/ocellus-p3-insolence.png) **Superior slash (Insolence)** damage
    * ![ocellus-p3-melee-parry](src/main/resources/com/tfamistaketracker/ocellus-p3-melee-parry.png) **Melee parry** prayer miss
    * ![ocellus-p3-tank-mage](src/main/resources/com/tfamistaketracker/ocellus-p3-tank-mage.png) **Tank not in melee range**, causing his magic attack on everyone
    * ![ocellus-p3-void-flare-detonation](src/main/resources/com/tfamistaketracker/ocellus-p3-void-flare-detonation.png) **Void rift, siphon or augmenter detonation** team mistake
    * ![ocellus-p3-untether-explosion](src/main/resources/com/tfamistaketracker/ocellus-p3-untether-explosion.png) **Untether explosion** damage
    * ![ocellus-p4-untether-mage](src/main/resources/com/tfamistaketracker/ocellus-p4-untether-mage.png) **Untether special** magic prayer miss
    * ![ocellus-p3-fall](src/main/resources/com/tfamistaketracker/ocellus-p3-fall.png) **Falling into the abyss**
    * ![ocellus-p4-void-space](src/main/resources/com/tfamistaketracker/ocellus-p4-void-space.png) **Space tiles** damage
    * ![ocellus-p4-void-explosion](src/main/resources/com/tfamistaketracker/ocellus-p4-void-explosion.png) **Void explosion** damage
    * ![ocellus-p4-prayer-attack](src/main/resources/com/tfamistaketracker/ocellus-p4-prayer-attack.png) **Prayer attack** prayer miss

Coming soon, in a later release (only deaths are tracked in these rooms for now):

* **Ezekiel**
* **Moia**
* **Dagon'hai trio**

---

## Screenshots

![panel-example](screenshots/panel-example.png)

![ag-example](screenshots/ag-example.png)

![ag-example-2](screenshots/ag-example-2.png)

![bork-example](screenshots/bork-example.png)

![xarth-example](screenshots/xarth-example.png)

![ocellus-example](screenshots/ocellus-example.png)

![ocellus-death-example](screenshots/ocellus-death-example.png)

---

## Changes

#### 1.0

* Initial release
