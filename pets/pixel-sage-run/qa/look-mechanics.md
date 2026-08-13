# Pixel Sage look mechanics

Pixel Sage is a soft, rounded plush fox. The lower torso, feet, and satchel attachment point stay anchored to the same baseline while the head and upper body make a restrained attention turn. The physical amber eye globes rotate as complete eye surfaces inside their sockets: sclera, iris, pupil, rim, eyelids, and highlights move together. Eyes lead the gaze; the muzzle and forehead follow with a small natural turn; ear tips follow one step behind with a slight soft-body lag. The tail stays attached and mostly stable, with a small follow-through on horizontal turns. The teal satchel is rigidly attached at the side, follows the torso rather than leading the gaze, and becomes slightly more side-on or occluded as the fox turns.

Cardinal pose families use viewer/screen coordinates:

- `000` up: feet and lower torso stay planted; chin lifts, both eye globes roll upward, eyelids open slightly, and the ears tilt back a little.
- `090` screen-right: the muzzle, nose, and head turn toward screen-right; the near eye is more prominent, the far eye is partly occluded, and the satchel remains attached on the torso with a small lag.
- `180` down: chin tucks, eyes roll downward, eyelids lower slightly, ears relax forward, and the lower face compresses subtly without changing identity.
- `270` screen-left: the complete head/eye-globe construction turns toward screen-left; the opposite eye becomes partly occluded, the satchel shifts only with the anchored torso, and the tail remains attached.

Intermediate directions interpolate evenly around the clockwise arc. Each 22.5-degree step uses a small, consistent change in eye-globe rotation, head turn, muzzle aim, and ear follow-through. No whole-sprite rotation, skew, or affine tilt is used. Keep the lower-body anchor stable and preserve the original physical eye construction; never add replacement eyes or slide a pupil across a fixed eye white.

Motion budget: eye direction changes lead each step; head/muzzle turn is smaller than eye motion; ear and tail follow-through is smaller again; satchel movement is the least and remains attached. The `157.5 -> 180` and `337.5 -> 000` transitions must be as smooth as all other adjacent pairs.
