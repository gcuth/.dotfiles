#!/usr/bin/env python3
# 
# Dump raw annotations from a KOBOeReader if one is mounted.

import os
import sys
import shutil

USERNAME = os.environ.get("USERNAME")
DEFAULT_MEDIA_PATH = os.path.join('/media/', USERNAME)
DEFAULT_KOBO_DRIVE_PATH = os.path.join('/media/', USERNAME, 'KOBOeReader')
RAW_ANNOTATION_OUT_PATH = os.path.join(os.environ.get("HOME"),
                                       'Documents',
                                       'blog',
                                       'data',
                                       'annotations')

def has_kobo_mounted(media_path=DEFAULT_MEDIA_PATH):
    mounted = os.listdir(media_path)
    if 'KOBOeReader' in mounted:
        return True
    else:
        return False

def list_annotation_paths(kobo_drive_path=DEFAULT_KOBO_DRIVE_PATH):
    all_paths = []
    for root, dirs, files in os.walk(kobo_drive_path):
        for name in files:
            all_paths.append(os.path.join(root, name))
    annotation_paths = [f for f in all_paths if 'Annotations' in f]
    return annotation_paths

if has_kobo_mounted():
    annotations = list_annotation_paths()
    for annotation in annotations:
        shutil.copy(annotation, RAW_ANNOTATION_OUT_PATH)
