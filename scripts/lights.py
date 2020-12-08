#!/usr/bin/env python3
#
# A script for switching my philips hue study lights in response to file activity.

import os
import sys
import time
from phue import Bridge

b = Bridge(ip='10.1.1.235')

def rgb_to_hsv(r, g, b):
    r, g, b = r/255.0, g/255.0, b/255.0
    mx = max(r, g, b)
    mn = min(r, g, b)
    df = mx-mn
    if mx == mn:
        h = 0
    elif mx == r:
        h = (60 * ((g-b)/df) + 360) % 360
    elif mx == g:
        h = (60 * ((b-r)/df) + 120) % 360
    elif mx == b:
        h = (60 * ((r-g)/df) + 240) % 360
    if mx == 0:
        s = 0
    else:
        s = (df/mx)*100
    v = mx*100
    return h, s, v

def rerange_hsv(hsv):
    h,s,v = hsv
    return int((h/100)*254), int((s/100)*254), int((v/100)*254)

def get_study_lights():
    study = b.get_group(b.get_group_id_by_name('Study'), 'lights')
    return [int(l) for l in study]

def get_study_lights_status(target_state):
    if target_state is 'any':
        status = b.get_group('Study')['state']['any_on']
    elif target_state is 'all':
        status = b.get_group('Study')['state']['all_on']
    else:
        status = None
    return status

def list_changed(last_n=5, directories=['/home/g/Documents/','/home/g/Code/']):
    recent = time.time() - last_n * 60
    changed = []
    for directory in directories:
        files = []
        for (dirpath, dirnames, filenames) in os.walk(directory):
            files += [os.path.join(dirpath, file) for file in filenames]
        changed += [f for f in files if os.path.getmtime(f) > recent]
    return [f for f in changed if '/.git/' not in f]

def main():
    study_lights = get_study_lights()
    study_status = get_study_lights_status('any') # check if any are on
    changed = list_changed(last_n=5)
    print(changed)
    if len(changed) > 0:
        h,s,v = rerange_hsv(rgb_to_hsv(255, 255, 255))
        command = {'on': True, 'hue': h, 'sat': s, 'bri': v, 'transitiontime': 600}
        b.set_light(study_lights, command)
    else:
        if study_status:
            h,s,v = rerange_hsv(rgb_to_hsv(255, 0, 0))
            command = {'on': True, 'hue': h, 'sat': s, 'bri': v, 'transitiontime': 1800}
            b.set_light(study_lights, command)

if __name__ == "__main__":
    main()
