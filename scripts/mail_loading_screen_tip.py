#!/usr/bin/env python3
#
# A script to generate 'mailto' links for sending a custom 'Loading Screen Tip'
# to people in my D&D campaign(s).
#
# Usage:
#   loadingscreentip
#   loadingscreentip

import os
import sys
import json
import random
import datetime
from urllib.parse import quote

SENT_PATH = "~/Documents/blog/data/logs/loadingscreentips.json"
TIPS_PATH = "~/Documents/dnd/loading-screen-tips.md"

def get_sent_path(default_path=SENT_PATH):
    return os.path.expanduser(default_path)

def get_tips_path(default_path=TIPS_PATH):
    return os.path.expanduser(default_path)

def create_sent_file():
    """Create the loadingscreentips.json file if it doesn't exist."""
    if not os.path.exists(get_sent_path()):
        with open(get_sent_path(), "w+") as f:
            json.dump([], f)

def get_sent(path=SENT_PATH):
    """Return a list of all sent tips."""
    try:
        with open(get_sent_path(path), "r") as f:
            text = f.read()
            return json.loads(text)
    except Exception as e:
        print(e)
        sys.exit(1)

def get_tips(path=TIPS_PATH):
    try:
        with open(get_tips_path(path), "r") as f:
            lines = f.readlines()
            tips = [l.strip() for l in lines if len(l.strip()) > 1]
            return tips
    except Exception as e:
        print(e)
        sys.exit(1)

def add_sent(email: str, tip: str, path=SENT_PATH):
    """Add record to log indicating that the tip has been sent to the email."""
    sent = get_sent(path=path)
    sent.append({'email': email, 'tip': tip, 'sent': datetime.datetime.now().strftime('%Y-%m-%d-%H-%M-%S')})
    formatted = json.dumps(sent, indent=4)
    with open(get_sent_path(path), "w") as f:
        f.write(formatted)

def main():
    """Main function."""
    emails = [a.strip() for a in sys.argv[1:] if '@' in a]
    for email in emails:
        # collect a list of all tips sent to that email in the past
        all_sent = get_sent()
        sent = [s for s in all_sent if s['email'] != email.strip()]
        # get all tips that we *could* sent
        all_tips = get_tips()
        tips = [t for t in all_tips if t not in [s['tip'] for s in sent]]
        random_tip = random.choice(tips)
        add_sent(email, random_tip)
        urlsafe_tip = quote(random_tip)
        urlsafe_subject = quote('Wizards & Lizards: Loading Screen Tip')
        mailto = ''.join(['mailto:', email, '?', 'Subject=', urlsafe_subject, '&body=', urlsafe_tip])
        print(mailto)

if __name__ == "__main__":
    main()
    sys.exit(0)
