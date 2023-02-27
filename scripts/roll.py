#!/usr/bin/env python3
#
# A script for translating a roll command into a dice roll.
# Rolls take the form {dice}d{sides}+{modifier}
# Example: 2d6+3
#
# Usage: roll.py <roll>
#
# Note: Also supports simulation via roll.py <roll> <n>
#
# Author: Galen Cuthbertson

import sys
import random

def translate_roll_command(roll_command:str) -> dict:
    """
    Take the string roll command and translate it into a dictionary of the
    form {dice:int, sides:int, modifier:int}
    """
    roll_command = roll_command.lower()
    roll_command = roll_command.replace(" ", "")
    roll_command = roll_command.replace("d", "d")
    roll_command = roll_command.replace("+-", "-")
    roll_command = roll_command.replace("-", "+-")
    if "+" not in roll_command:
        roll_command = roll_command + "+0"
    dice = int(roll_command.split("d")[0])
    sides = int(roll_command.split("d")[1].split("+")[0])
    modifier = int(roll_command.split("d")[1].split("+")[1])
    return {'dice':dice, 'sides':sides, 'modifier':modifier}

def calculate_roll(roll_dict:dict) -> int:
    """
    Take the dictionary of the form {dice:int, sides:int, modifier:int} and
    calculate the total of the roll.
    """
    total = 0
    for i in range(roll_dict['dice']):
        total += random.randint(1, roll_dict['sides'])
    total += roll_dict['modifier']
    return total

def simulate(roll_dict:dict, n:int) -> list:
    """
    Take the dictionary of the form {dice:int, sides:int, modifier:int} and
    simulate n rolls of the roll.
    """
    rolls = []
    for i in range(n):
        rolls.append(calculate_roll(roll_dict))
    return rolls

def summarise_simulation(simulations:list) -> str:
    """
    Take a list of simulations and return a string summarising the results.
    """
    n = len(simulations)
    mean = sum(simulations) / n
    median = sorted(simulations)[n//2]
    mode = max(set(simulations), key=simulations.count)
    maximum = max(simulations)
    minimum = min(simulations)
    return "Rolls: {}\nMean: {}\nMedian: {}\nMode: {}\nMax: {}\nMin: {}".format(n, mean, median, mode, maximum, minimum)

def main():
    if len(sys.argv) == 3:
        print("Interpreting additional argument '{}' as a simulation count.".format(sys.argv[2]))
        roll_command = sys.argv[1]
        roll_dict = translate_roll_command(roll_command)
        print("Simulating {} rolls of {}d{}+{}".format(sys.argv[2], roll_dict['dice'], roll_dict['sides'], roll_dict['modifier']))
        rolls = simulate(roll_dict, int(sys.argv[2]))
        print(summarise_simulation(rolls))
        sys.exit(0)
    elif len(sys.argv) != 2:
        print("Usage: roll.py <roll>")
        sys.exit(1)
    else:
        roll_command = sys.argv[1]
        roll_dict = translate_roll_command(roll_command)
        roll_sum = calculate_roll(roll_dict)
        print(roll_sum)
        sys.exit(0)

if __name__ == "__main__":
    main()
