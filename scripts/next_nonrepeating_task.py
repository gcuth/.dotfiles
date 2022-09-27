#!/usr/bin/env python3
# 
# A small script to grab my list of todoist tasks, both completed and active,
# and list the next n non-repeating (ie, never done it multiple times before)
# tasks in my todolist.

import os
import sys
import csv
import datetime
import subprocess

LOGFILE = os.path.expanduser("~/Documents/blog/data/logs/completed_tasks.csv")


def process_raw_tasklist(raw_tasklist: list) -> list:
    """
    Take a raw list of tasklist lines and return a list of dicts with with processed dates and task names.
    """
    # identify the header row (first row with 'Content' or 'ID' in it)
    header_index = [i for i, row in enumerate(raw_tasklist) if 'Content' in row or 'ID' in row][0]
    # get the header row (and remove it from the raw tasklist)
    header = raw_tasklist.pop(header_index)
    # use the header row to create a dict of column names and indices
    tasklist = [dict(zip(header, row)) for row in raw_tasklist]
    # interpret raw dates as datetime objects
    for task in tasklist:
        if task.get('DueDate', '') != '':
            try:
                task['DueDate'] = datetime.datetime.strptime(task.get('DueDate',''), '%y/%m/%d(%a) %H:%M')
            except Exception as e: # ignore any exceptions
                continue
        if task.get('CompletedDate', '') != '':
            try:
                task['CompletedDate'] = datetime.datetime.strptime(task.get('CompletedDate',''), '%y/%m/%d(%a) %H:%M')
            except Exception as e: # ignore any exceptions
                continue
    return tasklist


def get_tasklist_from_log(logfile=LOGFILE):
    if not os.path.exists(logfile):
        return []
    tasklist = []
    with open(logfile, 'r') as f:
        text = f.read()
        for row in csv.reader(text.splitlines(), delimiter=','):
            tasklist.append(row)
    tasklist = process_raw_tasklist(tasklist)
    return tasklist


def get_tasklist(list_type='list'):
    """Call 'todoist list' or 'todoist completed-list' to get tasklist."""
    raw_tasklist = subprocess.getoutput("/opt/homebrew/bin/todoist --csv --header {}".format(list_type))
    # read the raw_tasklist string (which is a csv) into a list of tasks
    tasklist = []
    for row in csv.reader(raw_tasklist.splitlines(), delimiter=','):
        tasklist.append(row)
    # process the tasklist
    tasklist = process_raw_tasklist(tasklist)
    return tasklist



def main(sysargs=sys.argv[1:]):
    """Main function"""
    # get the tasklists
    active = get_tasklist('list')
    raw_completed = get_tasklist('completed-list')
    raw_completed.extend(get_tasklist_from_log())
    # identify all IDs in the completed list
    completed_ids = list(set([task['ID'] for task in raw_completed]))
    # use the completed_ids list to deduplicate the completed list
    completed = []
    for task_id in completed_ids:
        task = [task for task in raw_completed if task['ID'] == task_id][0]
        completed.append(task)
    # get the number of tasks to list
    try:
        num_tasks = int(sysargs[0])
    except Exception as e:
        num_tasks = 5
    # get the list of tasks that have been completed (ie, actual task text)
    completed_tasks = [t['Content'] for t in completed]
    # get the list of tasks that have been completed more than once
    repeat_complete = list(set([x for x in completed_tasks if completed_tasks.count(x) > 1]))
    # remove all tasks from active where the content of the task is in repeat_complete
    active_new = [t for t in active if t.get('Content', '').strip() not in repeat_complete]
    active_new = [t for t in active_new if t.get('DueDate') is not None and isinstance(t.get('DueDate'), datetime.datetime)]
    active_new.sort(key=lambda x: x['DueDate'])
    if len(active_new) > 0:
        if num_tasks == 1:
            print(active_new[0]['Content'])
        else:
            for i, task in enumerate(active_new[:num_tasks]):
                print("{}. {}".format(i+1, task['Content']))


if __name__ == '__main__':
    main(sys.argv[1:])
