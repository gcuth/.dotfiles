#!/usr/bin/env python3
#
# A script for 'bumping' the status of my desk Hue lights in the direction of
# a target brightness and/or colour. Takes arguments for the direction to bump
# the brightness and/or colour of the lights. The script uses the openhue cli
# tool to interact with the lights, and logs the target status to a json file.

import os
import sys
import json
import datetime
import argparse

OPENHUE = "/opt/homebrew/bin/openhue" # The path to the openhue cli tool
LIGHTS = ["Desk Left", "Desk Right"] # Lights to control
LOGPATH = os.path.expanduser("~/.bumplights.json") # Log file path


def get_light_status(name: str) -> dict:
    """Get the current status of a light."""
    cmd = f"{OPENHUE} get light \"{name}\" --json"
    # get the result of the command
    result = os.popen(cmd).read()
    # parse the result as json
    return json.loads(result)


def read_log(logpath=LOGPATH) -> list:
    """Read the log file and return the contents as a list of dicts."""
    if not os.path.exists(logpath):
        return []
    else:
        with open(logpath, "r") as f:
            return json.load(f)


def write_log(log: dict, logpath=LOGPATH) -> None:
    """Write to the logpath, appending the given log item."""
    if os.path.exists(logpath):
        logs = read_log(logpath)
    else:
        logs = []
    logs.append(log)
    with open(logpath, "w+") as f:
        json.dump(logs, f)


def set_light_status(name: str, brightness: int, rgb: str, logpath=LOGPATH) -> None:
    """Set the status of a light. Log the target given to a json file."""
    log = {
        "name": name,
        "brightness": brightness,
        "rgb": rgb,
        "timestamp": datetime.datetime.now().isoformat()
    }
    if brightness < 0 or brightness > 100:
        raise ValueError("Brightness must be between 0 and 100")
    if rgb is not None and len(rgb) != 6:
        cmd = f"{OPENHUE} set light \"{name}\" --brightness {brightness} --rgb {rgb}"
    else:
        cmd = f"{OPENHUE} set light \"{name}\" --brightness {brightness}"
    os.system(cmd)
    if os.system(cmd) != 0:
        print(f"Error setting light status for {name}")
        log['success'] = False
        write_log(log, logpath)
    else:
        log['success'] = True
        write_log(log, logpath)


def compute_new_brightness(current: int, direction: str, n=1) -> int:
    """Compute the new brightness value based on the given direction."""
    if direction == "up":
        return max(0, min(current + 1, 100))
    elif direction == "down":
        return max(0, min(current - 1, 100))
    else:
        raise ValueError("Direction must be 'up' or 'down'")


def compute_new_rgb(current: str, target: str, n=1) -> str:
    """
    Compute the new rgb value (as a hex string) in the direction of the target.
    """
    if current == target:
        return current
    if current is None:
        return target
    cr = int(current[:2], 16)
    cg = int(current[2:4], 16)
    cb = int(current[4:], 16)
    tr = int(target[:2], 16)
    tg = int(target[2:4], 16)
    tb = int(target[4:], 16)
    # bump the rgb values by 1 in the direction of the target
    if cr < tr:
        cr += 1
    elif cr > tr:
        cr -= 1
    if cg < tg:
        cg += 1
    elif cg > tg:
        cg -= 1
    if cb < tb:
        cb += 1
    elif cb > tb:
        cb -= 1
    # make sure the new values are bound by 0 and 255
    cr = max(0, min(255, cr))
    cg = max(0, min(255, cg))
    cb = max(0, min(255, cb))
    # return the new rgb value as a hex string
    return f"{cr:02x}{cg:02x}{cb:02x}"


def bump_light(name: str, direction: str, rgb: str, logpath=LOGPATH) -> None:
    """
    Get the current status of a light. If *and only if* on, bump the light up
    or down in brightness by 1%. If the light supports rgb colour, nudge the
    colour in the given direction as well (from wherever it is now).
    """
    status = get_light_status(name)
    if not status["HueData"]["on"]["on"]:
        return None
    current_log = read_log(logpath)
    current_log = [l for l in current_log if l["name"] == name]
    current_log = [l for l in current_log if l["success"]]
    if len(current_log) > 0:
        last = current_log[-1]
        current_brightness = last["brightness"]
        current_rgb = last["rgb"]
    else:
        current_brightness = 1
        current_rgb = None
    new_brightness = compute_new_brightness(current_brightness, direction)
    new_rgb = compute_new_rgb(current_rgb, rgb)
    if rgb is not None:
        set_light_status(name, new_brightness, new_rgb)
    else:
        set_light_status(name, new_brightness, None)


def main():
    """Run the script to get current light status and bump in a direction."""
    parser = argparse.ArgumentParser()
    parser.add_argument("direction", choices=["up", "down"], help="The direction to bump the light")
    parser.add_argument("--rgb", help="The target rgb value for the light")
    args = parser.parse_args()
    # check that the arguments are valid
    if args.rgb is not None and len(args.rgb) != 6:
        parser.error("RGB value must be a 6-character hex string")
    for light in LIGHTS:
        if light == "Desk Left" or light == "Desk Right": # these don't support rgb
            bump_light(light, args.direction, None)
        else:
            bump_light(light, args.direction, args.rgb)

if __name__ == "__main__":
    main()