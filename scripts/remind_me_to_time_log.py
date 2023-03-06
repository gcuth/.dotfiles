#!/usr/bin/env python3
# 
# A small script to use the inbuild macos osascript (etc) systems to
# remind me to log time in Timery if there isn't anything running.

import os
import sys
import csv
import datetime
import subprocess

def check_for_current_time_entry():
    """Call 'toggl now' to see if there's a current time entry"""
    try:
        now = subprocess.check_output("/opt/homebrew/bin/toggl now",shell=True,stderr=subprocess.STDOUT)
        return now
    except subprocess.CalledProcessError as e:
        if "There is no time entry running" in str(e.output.decode()):
            return False

def get_tasklist():
    """Call 'todoist list' to get the complete available tasklist."""
    raw_tasklist = subprocess.getoutput("/opt/homebrew/bin/todoist --csv --header list")
    # read the raw_tasklist string (which is a csv) into a list of tasks
    tasklist = []
    for row in csv.reader(raw_tasklist.splitlines(), delimiter=','):
        tasklist.append(row)
    # convert list of lists in tasklist to list of dicts
    header = tasklist.pop(0)
    tasklist = [dict(zip(header,row)) for row in tasklist]
    # interpret raw dates as datetime objects
    for task in tasklist:
        if task['DueDate'] != '':
            try:
                task['DueDate'] = datetime.datetime.strptime(task['DueDate'], '%y/%m/%d(%a) %H:%M')
            except Exception as e: # ignore any exceptions
                continue
    # sort items in the list by its priority field
    tasklist.sort(key=lambda x: x['Priority'])
    return tasklist

def get_best_task(tasklist=get_tasklist()):
    """Get the best next task from the tasklist"""
    # filter for tasks with a due date in the next 7 days and get the earliest one (if there is one)
    due_soon = [task for task in tasklist if task['DueDate'] != '' and task['DueDate'] < datetime.datetime.now() + datetime.timedelta(days=7)]
    due_soon = sorted(due_soon, key=lambda x: x['DueDate'])
    if len(due_soon) > 0:
        # return the first task in the list
        return due_soon[0]
    else: # looks like there are no due tasks, so instead search slightly cleverly
        # exercise tasks
        exercise_tasks = [task for task in tasklist if 'Exercise' in task['Project']]
        if len(exercise_tasks) > 0:
            return exercise_tasks[0]
        # cleaning tasks
        cleaning_tasks = [task for task in tasklist if 'Cleaning' in task['Project']]
        if len(cleaning_tasks) > 0:
            return cleaning_tasks[0]
        # tekke tasks
        tekke_tasks = [task for task in tasklist if 'Tekke' in task['Project']]
        if len(tekke_tasks) > 0:
            return tekke_tasks[0]
        # reading tasks
        reading_tasks = [task for task in tasklist if task['Project'] == '#Read' or 'Read and note' in task['Content']]
        if len(reading_tasks) > 0:
            return reading_tasks[0]
        # anything else
        tasklist = sorted(tasklist, key=lambda x: x['Priority'])
        return tasklist[0]

def prompt_to_regain_attention(current_task:str):
    """
    Take the current task string and use it to call an 'open -a blah' subprocess
    to regain attention on the (probably correct) application for some key tasks."""
    raw_entry = current_task.decode('utf-8').split('\n')
    try:
        entry = {'Description': raw_entry[0]}
        for line in raw_entry[1:]:
            field = line.split(':')[0].strip()
            value = ':'.join(line.split(':')[1:]).strip()
            entry[field] = value
    except Exception as e:
        entry = {}
    if 'Anki' in entry.get('Project', '') and 'Complete all anki decks' in entry.get('Description', ''):
        subprocess.call("open -a Anki", shell=True)
    elif 'Blog' in entry.get('Project', '') and 'morning pages' in entry.get('Description', ''):
        subprocess.call("open -a Terminal", shell=True)
    elif 'Thesis' in entry.get('Project', '') and 'Write' in entry.get('Description', ''):
        subprocess.call("open -a Terminal", shell=True)
    elif 'and note' in entry.get('Description', ''):
        subprocess.call("open -a Terminal", shell=True)
    elif 'D+D' in entry.get('Project', ''):
        if 'Write' in entry.get('Description', '') or 'campaign timeline' in entry.get('Description', ''):
            subprocess.call("open -a Terminal", shell=True)
    elif 'email' in entry.get('Description', '').lower():
        subprocess.call("open -a Mail", shell=True)


def main():
    """Main function"""
    task_now = check_for_current_time_entry()
    if task_now is False:
        print("[{}] No toggl time entry is running!".format(datetime.datetime.now().strftime("%Y-%-m-%d %H:%M:%S")))
        best_task = get_best_task()
        print("[{}] Best task to work on: {}".format(datetime.datetime.now().strftime("%Y-%-m-%d %H:%M:%S"), best_task['Content']))
        # copy best task content to system clipboard
        subprocess.call("echo '{}' | pbcopy".format(best_task['Content']),shell=True)
        # open the timery app
        subprocess.call("open -a Timery", shell=True)
    else:
        prompt_to_regain_attention(task_now)

if __name__ == '__main__':
    main()