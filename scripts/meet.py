#!/usr/bin/env python3
#
# Get input about a remote meeting that's about to start (or has just ended).
# Using this info, along with whatever system information is available,
# build and open suitable meeting notes, along with zoom, audio hijack, etc.
# Create &/or update a metadata file for the audio recording, if one exists.
#
# Usage:
#   meet.py [start|end]

import sys
import os
import subprocess
from datetime import datetime
import argparse

RECORDING_DIRS = ["~/Music/Audio Hijack/", "~/Music/Just Press Record/"]

#    toggl stop
#    toggl start -o 'Social' -a 'meeting' "'$*'"
#    code ~/Documents/blog/_posts/meetings/$(date +%Y-%m-%d)-$(echo "'$*'" | sed "s/[A-Z]/\L&/g" | sed "s/ /-/g" | sed "s/'//g").md

def build_meeting_recording_metadata(metadata: dict):
    pass

def update_meeting_recording_metadata(metadata: dict):
    pass

def build_notes_header(metadata: dict):
    pass

def build_meeting_notes(notes_dir = "~/Documents/blog/_posts/meetings"):
    pass

def backup_recordings(default_dirs=RECORDING_DIRS, backup_dir="~/Recordings/"):
    """
    Copy any available audio recordings found in default_dirs.
    Save these files to a backup directory, along with any metadata files.
    """
    pass

def process_args(sysargs = sys.argv[1:]):
    """Take a list of command line arguments and return a dict of settings."""
    parser = argparse.ArgumentParser(description='Get input about a remote meeting that\'s about to start (or has just ended). Using this info, along with whatever system information is available, build and open suitable meeting notes, along with zoom, audio hijack, etc. Create &/or update a metadata file for the audio recording, if one exists.')
    parser.add_argument('action', choices=['start', 'end'], help='start or end')
    parser.add_argument('--notes-dir', default="~/Documents/blog/_posts/meetings", help='directory to store meeting notes')
    parser.add_argument('--backup-dir', default="~/Recordings/", help='directory to store meeting recordings')
    parser.add_argument('--recording-dirs', default=RECORDING_DIRS, nargs='+', help='directories to search for recordings')
    parser.add_argument('--recording-metadata', default="~/Recordings/metadata.json", help='metadata file for recordings')
    settings = parser.parse_args(sysargs)
    return settings

def main(args=sys.argv[1:]):
    settings = process_args(args)
    if settings.action == 'start':
        pass
    elif settings.action == 'end':
        pass
    else:
        print("Unknown action: {}".format(settings.action))
        sys.exit(1)

if __name__ == "__main__":
    main()