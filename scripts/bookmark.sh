#!/usr/bin/env sh
#
# A dead-small bookmarking script for newsboat

url="$1"
title="$2"
description="$3"
feed_title="$4"

echo $url >> ~/Documents/blog/data/toread.txt

xdg-open $url || open $url
