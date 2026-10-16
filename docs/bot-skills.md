# Bot Skills & Capabilities

`BotBrain` decides what a bot casts. How well it decides depends on the bot's
`BotSkill`. The skill is the tier's noise amplitude plus a set of
`BotCapability` values. Each capability switches on one refinement of the
decision. A weaker tier is not just a noisier copy of a strong one: it knows
fewer things.

## Tier table

`BotSkill.forTier` is the only place a tier's strength is defined. Each tier
knows everything the tier below it knows.

| Tier | Noise | Adds |
|---|---|---|
| HOSPITALITY | 0 | nothing. It plays to lose through `HospitalityDirector`. |
| INTRO | 0.50 | `SEED_SPIRIT_COMBO`, `MOB_CLUSTER_COMBO` (the play every tier had before capabilities) |
| BEGINNER | 0.30 | `STRONGEST_LANE` |
| INTERMEDIATE | 0.18 | `LETHAL`, `SUMMON_STRENGTH`, `THREAT_STRENGTH` |
| ADVANCED | 0.08 | `ALLY_COVER`, `MANA_DISCIPLINE`, `ROLE_PLACEMENT`, `SUMMON_RELEVANCE`, `GAME_PLAN` |
| ELITE | 0 | `LEAD_TARGETING` |

## What each capability changes

- `STRONGEST_LANE`: `ThreatAssessment.strongestLaneZ` groups pressing threats
  into lanes and defends the heaviest one. Without it, two lanes average out
  to the empty middle.
- `SUMMON_STRENGTH`: a summon is worth the average summon's value, scaled by
  its cost and by the combat power (`sqrt(hp * dps)`) it buys per mana
  (`UnitCatalogue`). Without it every summon is worth the same, so the
  cheapest one always wins.
- `THREAT_STRENGTH`: pressure weighs each enemy by its combat power at its
  remaining hp, instead of counting bodies.
- `ALLY_COVER`: allied mobs standing in a pressing lane take pressure off.
- `LETHAL`: `LethalRule` casts the cheapest spell whose stated `damage`
  finishes the enemy player.
- `MANA_DISCIPLINE`: while calm and not overdue, the bot waits for a card
  that scores clearly better per mana. It only waits for cards it can afford
  at max mana.
- `ROLE_PLACEMENT`: summons whose prefab `attack_range` is at least
  `UnitCatalogue.RANGED_ATTACK_RANGE` are placed behind the blocker line.
- `SUMMON_RELEVANCE`: `SummonRelevance` skips a summon that has nothing to
  do right now, by its `CardRole`: an anti-air unit with no enemy flyer, a
  defence or push building with no enemy on our half, a support building
  with fewer than two allies to support. Mana within `GamePlanner.CAP_MARGIN`
  of the cap may still go to one, so a dead card does not block a hand slot
  forever.
- `GAME_PLAN`: `GamePlanner` saves for a wave of fighters while calm and not
  overdue. With a fighter of at least `WAVE_CARD_COST` mana in hand it saves
  until every fighter in hand is affordable together, then releases them on
  one spot one tick after another; our fighter still near our base is a wave
  being released, and the next one joins it. It only does this when the whole
  deck is built for it (`DeckProfile.wavesPayOff`: three or more such fighter
  cards). A deck of cheap fighters loses by any saving, so it casts on sight.
- `LEAD_TARGETING`: `MotionTracker` measures velocities between two
  `BotEye` snapshots, using game time. Blasts aim where enemies will be after
  the persona's thinking time plus `CAST_RESOLUTION_SECONDS`.

Every unit stat comes from the same prefab parameters the initializers read.
A unit whose parameters cannot judge it counts as average.

What a summon is for cannot be read from parameters: who a unit attacks is set
by `TargetMask` in its prefab initializer, and `wind_spirit` has a 300 damage
row while only diving at flyers. `CardRole` writes the role of each summon
down by magic name, read from the code. A summon with no row is played as if
roles did not exist, and the first bot to hold one logs a WARN naming it. Add
the row when a summon magic is added.

## Adding a capability

1. Add a constant to `BotCapability` and document what the bot does
   differently with it.
2. Read it where the decision is made with `skill.has(...)`. If it is a play
   that should pre-empt value scoring, implement `PriorityRule` instead and add
   it to `BotBrain.PRIORITY_RULES` in the order it should be tried.
3. Grant it to the right tiers in `BotSkill.forTier`. `BotSkillTest` checks
   that each tier still includes the one below it and that ELITE knows every
   capability.
4. Test the decision with and without the capability, at zero noise, the way
   `BotBrainCapabilityTest` does. Build the skill directly with
   `new BotSkill(0.0, Set.of(...))` so that only one capability is on.

Never give HOSPITALITY a capability that helps it win. Its contract is in
`HospitalityDirector`.
