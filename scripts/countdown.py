#!/usr/bin/env python3
# A script for counting down n seconds.

import sys
import time

if len(sys.argv) != 2:
    print("Usage: countdown.py <seconds>")
    sys.exit(1)

n = int(sys.argv[1])

for i in range(n, 0, -1):
    print(i)
    time.sleep(1)
