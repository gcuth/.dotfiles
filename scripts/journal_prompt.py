#!/usr/bin/env python3
#
# A script for finding 'morning pages'-ish prompts and printing a random one.
# Searches for prompts in common system locations.
#
# Usage: journal_prompt.py
#
# Author: Galen Cuthbertson

import os
import json
import random
import datetime

TOLOOKFOR = ["~/Dropbox/notes/prompts.txt",
             "~/Dropbox/notes/prompts.md",
             "~/Documents/blog/data/prompts.txt",
             "~/Documents/blog/data/prompts.md",
             "~/Documents/blog/data/prompts",
             "~/Documents/blog/data/journal_prompts.txt",
             "~/Documents/blog/data/journal_prompts.md",
             "~/Documents/blog/data/journal_prompts",
             "~/.morning_pages_prompts.txt"]

LOGPATH = os.path.expanduser("~/Documents/blog/data/logs/journal_prompts.json")


def find_prompts(locations=TOLOOKFOR):
    """Find prompt files in common system locations."""
    valid_files = []
    for location in locations:
        location = os.path.expanduser(location)
        if os.path.exists(location):
            valid_files.append(location)
    return valid_files


def collect_prompts(locations=TOLOOKFOR):
    """Find prompts in common system locations and collect them all."""
    prompts = []
    for prompt_file in find_prompts(locations=locations):
        with open(prompt_file, 'r') as f:
            for line in f:
                prompts.append(line.strip())
    return list(set(prompts))


def filter_prompts(prompts: list) -> list:
    """Exclude prompts for a list that are unlikely to be useful."""
    actual = [p for p in prompts if not p.startswith("#") and not len(p) < 5]
    return [p for p in actual if p not in read_prompt_log().get(datetime.datetime.now().strftime("%Y-%m-%d"), [])]


def read_prompt_log(logpath=LOGPATH):
    """Read the log of prompts I've used."""
    if os.path.exists(logpath):
        with open(logpath, 'r') as f:
            return json.load(f)
    else:
        return {}


def log_prompt(prompt: str, logpath=LOGPATH) -> None:
    """Log that I've used a prompt, to prevent immediate reuse."""
    current_log = read_prompt_log(logpath=logpath)
    today = datetime.datetime.now().strftime("%Y-%m-%d")
    if today not in current_log:
        current_log[today] = []
    current_log[today].append(prompt)
    with open(logpath, 'w') as f:
        json.dump(current_log, f, indent=4)


def main():
    """Find prompts in common system locations, collect all, and print one."""
    prompts = filter_prompts(collect_prompts())
    if len(prompts) > 0:
        best_prompt = random.choice(prompts)
        log_prompt(best_prompt)
        print(best_prompt)
    else:
        print("No suitable prompts found!")


if __name__ == "__main__":
    main()
