#!/usr/bin/env python3
#
# A script for logging TAPs (Trigger Action Plans) in a json file.
#
# Usage:
#   tap.py add (adds a new TAP)
#   tap.py list (lists all TAPs)

import os
import sys
import json
import uuid
import datetime

TAPS_PATH = "~/Documents/blog/data/logs/taps.json"

def get_taps_path(default_path=TAPS_PATH):
    return os.path.expanduser(default_path)

def create_taps_file():
    """Create the taps.json file if it doesn't exist."""
    if not os.path.exists(get_taps_path()):
        with open(get_taps_path(), "w+") as f:
            json.dump([], f)

def get_taps():
    """Return a list of all logged TAPs."""
    try:
        with open(get_taps_path(), "r") as f:
            text = f.read()
            return json.loads(text)
    except Exception as e:
        print(e)
        sys.exit(1)

def add_tap(tap):
    """Add a TAP to the list of logged TAPs."""
    taps = get_taps()
    taps.append(tap)
    formatted = json.dumps(taps, indent=4)
    with open(get_taps_path(), "w") as f:
        f.write(formatted)

def summarise_taps(path=get_taps_path()):
    """Summarise all TAPs."""
    with open(path, "r") as f:
        text = f.read()
        if text == "":
            taps =  []
        else:
            taps = json.loads(text)
    active = [t for t in taps if t["active"]]
    inactive = [t for t in taps if not t["active"]]
    return "Total Taps: {}\nActive Taps: {}\nInactive Taps: {}".format(len(taps), len(active), len(inactive))

def prompt_to_add_tap():
    """Prompt the user to add a TAP."""
    print("Remember:")
    print("1. Triggers should be noticeable, concrete, and relevant.")
    print("2. Actions should be simple, atomic *affordances*.")
    goal = input("[Goal] Desired Outcome/Behaviour: ")
    trigger = input("[Trigger] When I notice ")
    action = input("[Action]  I will ")
    tap = {
        "uuid": str(uuid.uuid4()),
        "goal": goal,
        "trigger": trigger,
        "action": action,
        "added": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "active": True
    }
    add_tap(tap)
    print("TAP added.")

def list_taps():
    """List all TAPs."""
    taps = get_taps()
    if len(taps) == 0:
        print("No TAPs logged.")
    else:
        print("TAPs:")
        for tap in [t for t in taps if t["active"]]:
            print("TAP: {}".format(tap["uuid"]))
            print("Goal: {}".format(tap["goal"]))
            print("Trigger: {}".format(tap["trigger"]))
            print("Action: {}".format(tap["action"]))
            print("\n")

def main():
    """Main function."""
    # create_taps_file()
    if len(sys.argv) == 1:
        prompt_to_add_tap()
        print(summarise_taps())
    elif sys.argv[1] == "list":
        list_taps()
    elif sys.argv[1] == "add":
        prompt_to_add_tap()
        print(summarise_taps())
    else:
        print("Unknown command: {}".format(sys.argv[1]))


if __name__ == "__main__":
    main()
    sys.exit(0)
