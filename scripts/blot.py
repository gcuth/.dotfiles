#!/usr/bin/env python3
#
# Write a blot post.
#
# Usage:
#   blot.py

import os
from wsgiref import validate
import yaml
import sys
import subprocess
import yaml
from datetime import datetime, timedelta

BLOG_DIR = "~/Documents/blog/_posts/essays/"

def prompt_for_title():
    title = input("Title: ")
    return title

def prompt_to():
    to = input("To: ")
    return to

def prompt_from():
    from_ = input("From: ")
    return from_

def get_location():
    """Get the location of the computer right now."""
    return subprocess.run(["/usr/bin/env", "curl", "-s", "ipinfo.io/loc"], stdout=subprocess.PIPE).stdout.decode("utf-8").strip()

def build_metadata_dict() -> dict:
    """Build a metadata dict, prompting where necessary."""
    metadata = {"title": prompt_for_title(),
                "date": (datetime.now() + timedelta(days=14)).strftime("%Y-%m-%d %H:%M:%S %z"),
                "layout": "post",
                "published": False,
                "blottable": True,
                "to": prompt_to(),
                "from": prompt_from(),
                "location": get_location()}
    if len(metadata["title"]) < 1 or len(metadata["to"]) < 1 or len(metadata["from"]) < 1:
        sys.exit("Error: title, to, and from must be non-empty.")
    if "+" not in metadata["date"]:
        metadata["date"] = metadata["date"].strip() + " +0000"
    metadata["permalink"] = "/" + metadata["title"].lower().replace(" ", "-")
    return metadata

def build_header_string(metadata: dict) -> str:
    metadata_yaml = yaml.dump(metadata, default_flow_style=False, explicit_start=False)
    metadata_yaml = [l.strip() for l in metadata_yaml.splitlines()] # split into lines
    metadata_yaml.sort(reverse=True) # sort the lines
    metadata_yaml = "\n".join(metadata_yaml) # join lines
    if "categories: " not in metadata_yaml:
        metadata_yaml += "\ncategories: ['essay']"
    if "epistemicstatus: " not in metadata_yaml:
        metadata_yaml += "\nepistemicstatus: 'TK'\n"
    return "---\n" + metadata_yaml + "---\n"

def build_fp(dir: str, metadata: dict) -> str:
    """Build the file path for the post."""
    title = metadata["permalink"].lower().replace("/", "")
    date = metadata["date"].split(" ")[0]
    return os.path.join(dir, date + "-" + title + ".md")

def main():
    # convert BLOG_DIR to an absolute path
    dir = os.path.expanduser(BLOG_DIR)
    metadata = build_metadata_dict()
    header = build_header_string(metadata)
    fp = build_fp(dir, metadata)
    if not os.path.exists(fp):
        with open(fp, "w+") as f:
            f.write(header)
            f.write("\n")
    subprocess.run(["code", fp])

if __name__ == "__main__":
    main()