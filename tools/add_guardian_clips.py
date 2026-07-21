#!/usr/bin/env python3
"""Adds the `cast` and `jump` clips to the humanoid guardian rig.

    python3 tools/add_guardian_clips.py

Idempotent: re-running rewrites the two clips and leaves every other clip untouched, so an artist
who replaces `idle`/`walk`/`attack`/`shoot` by hand does not lose that work here.

The rig gained these because behaviours did. `VOLLEY` needs a wind-up the party can read across a
room (`cast`), and `LEAP` needs a take-off (`jump`) — BestiaryAudit holds every variant to the
clips its behaviours can request, so declaring either on a guardian without these fails the build.
Placeholders in the DUNGEONS_PISOS.md §19 sense: correct in structure, crude in execution.
"""
import json
import os

PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                    'src/main/resources/assets/teras/animations/dungeon_guardian.animation.json')


def keys(pairs):
    return {str(t): list(v) for t, v in pairs}


def cast_clip():
    """Both arms raised and held, then thrown down — the wind-up VOLLEY's telegraph sits under."""
    return {
        'loop': False,
        'animation_length': 1.5,
        'bones': {
            'arm_right': {'rotation': keys([
                (0.0, [0, 0, 0]), (0.4, [-160, 0, -20]), (1.1, [-150, 0, -25]),
                (1.3, [20, 0, 0]), (1.5, [0, 0, 0])])},
            'arm_left': {'rotation': keys([
                (0.0, [0, 0, 0]), (0.4, [-160, 0, 20]), (1.1, [-150, 0, 25]),
                (1.3, [20, 0, 0]), (1.5, [0, 0, 0])])},
            'head': {'rotation': keys([
                (0.0, [0, 0, 0]), (0.4, [-20, 0, 0]), (1.1, [-20, 0, 0]),
                (1.3, [15, 0, 0]), (1.5, [0, 0, 0])])},
            'body': {'rotation': keys([
                (0.0, [0, 0, 0]), (0.4, [-8, 0, 0]), (1.3, [10, 0, 0]), (1.5, [0, 0, 0])])},
        },
    }


def jump_clip():
    """Crouch, extend, tuck the legs — a take-off for LEAP on a humanoid."""
    return {
        'loop': False,
        'animation_length': 0.7,
        'bones': {
            'body': {
                'rotation': keys([(0.0, [0, 0, 0]), (0.15, [18, 0, 0]),
                                  (0.35, [-12, 0, 0]), (0.7, [0, 0, 0])]),
                'position': keys([(0.0, [0, 0, 0]), (0.15, [0, -2, 0]),
                                  (0.35, [0, 1, 0]), (0.7, [0, 0, 0])]),
            },
            'leg_right': {'rotation': keys([
                (0.0, [0, 0, 0]), (0.15, [-25, 0, 0]), (0.35, [-55, 0, 0]), (0.7, [0, 0, 0])])},
            'leg_left': {'rotation': keys([
                (0.0, [0, 0, 0]), (0.15, [-20, 0, 0]), (0.35, [-45, 0, 0]), (0.7, [0, 0, 0])])},
            'arm_right': {'rotation': keys([
                (0.0, [0, 0, 0]), (0.35, [-120, 0, 0]), (0.7, [0, 0, 0])])},
            'arm_left': {'rotation': keys([
                (0.0, [0, 0, 0]), (0.35, [-120, 0, 0]), (0.7, [0, 0, 0])])},
        },
    }


def main():
    with open(PATH) as f:
        data = json.load(f)
    data['animations']['cast'] = cast_clip()
    data['animations']['jump'] = jump_clip()
    with open(PATH, 'w') as f:
        json.dump(data, f, indent=1)
        f.write('\n')
    print('clips now in dungeon_guardian:', ', '.join(data['animations']))


if __name__ == '__main__':
    main()
