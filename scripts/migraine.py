#!/usr/bin/env python3
#
# If you get a migraine, type this.

import os

def set_darkmode():
    """Try two ways to set dark mode on."""
    os.system("dark-mode on")
    os.system("""
                osascript -e 'tell application "System Events"
                                tell appearance preferences
                                    set dark mode to true
                                end tell
                              end tell'
              """)

def switch_focus_mode_to_migraine():
    """Use a os.system call to osascript to turn on the 'Migraine' focus mode."""
    os.system("shortcuts run 'migraine'")

def start_a_migraine_timer():
    """Call toggl and try to kill the current timer and replace it with a migraine timer."""
    os.system("/opt/homebrew/bin/toggl start -o 'Migraine' 'Migraine'")



def main():
    set_darkmode()
    switch_focus_mode_to_migraine()
    start_a_migraine_timer()

if __name__ == "__main__":
    main()
