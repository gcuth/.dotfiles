#!/usr/bin/env python3
#
# Ping the manifold.markets API and recommend bets.
# 
# Usage:
#   recommend_bets.py

import os
import sys
import toml
import time
import requests

SETTINGS_PATHS = ["~/.spae.toml", "~/.config/spae.toml", "~/.config/spae/settings.toml"]

def find_settings(paths=SETTINGS_PATHS):
    """Search for settings toml files, read, and return the best.

    :paths: TODO
    :returns: TODO

    """
    paths = [os.path.expanduser(path) for path in paths]
    available = [path for path in paths if os.path.exists(path)]
    if not available:
        raise Exception("No settings found")
    with open(available[0], "r") as f:
        settings = toml.load(f)
    return settings

def get_manifold_users(apikey):
    """Take a manifold api key and return a list of all manifold users.

    :apikey: TODO
    :returns: TODO
    """
    url = "https://manifold.markets/api/v0/users"
    headers = {"Authorization": "Key {}".format(apikey)}
    r = requests.get(url, headers=headers)
    return r.json()

def sort_manifold_users(users:list) -> list:
    """Take a list of manifold users and return a list of user ids, sorted in descending order by profit over time.

    :users: TODO
    :returns: TODO
    """
    users = [u for u in users if u['totalDeposits'] > 0 and u['profitCached']['allTime'] != 0]
    users = [u for u in users if u['totalDeposits'] and u['profitCached'] and u['profitCached']['allTime']]
    percentage_earnings = sorted(users, key=lambda user: (user['profitCached']['allTime']/user['totalDeposits']), reverse=True)
    total_earnings = sorted(users, key=lambda user: user['profitCached']['allTime'], reverse=True)
    percentage_rankings = [(i, u['id']) for i, u in enumerate(percentage_earnings)]
    total_rankings = [(i, u['id']) for i, u in enumerate(total_earnings)]
    ranked_users = [{'id': u['id'], 'url': u['url']} for u in users]
    for ranked_user in ranked_users:
        ranked_user['percentage_ranking'] = [i for i, u in percentage_rankings if u == ranked_user['id']][0]
        ranked_user['total_ranking'] = [i for i, u in total_rankings if u == ranked_user['id']][0]
        ranked_user['overall_ranking'] = ranked_user['percentage_ranking'] + ranked_user['total_ranking']
    best = sorted(ranked_users, key=lambda user: user['overall_ranking'])
    best_users = [[user for user in users if user['id'] == bestuser['id']][0] for bestuser in best]
    return best_users

def score_users(users):
    """Take a list of users and return a 'score' for how seriously to take
       their bets (from -1 to 1), where 1 is the best user and -1 is the
       worst user. Do this by sorting users by profit over time and then
       assigning 1 to the first user in the list, -1 to the last user in
       the list, and scaling every user between."""
    sorted_users = sort_manifold_users(users)
    total_users = len(sorted_users)
    for i, user in enumerate(sorted_users):
        percentage_position = (i+1)/total_users # 1.0 is the worst here; 0.0 is the best
        user['score'] = ((1-percentage_position)-0.5)*2
    return sorted_users

def get_bets(apikey):
    """Take a manifold api key and return a list of bets.

    :apikey: TODO
    :returns: TODO
    """
    url = "https://manifold.markets/api/v0/bets"
    headers = {"Authorization": "Key {}".format(apikey)}
    r = requests.get(url, headers=headers)
    return r.json()

def filter_bets(bets):
    """Take a list of bets and only keep the ones that are
        1. binary ('YES' or 'NO' in outcome field), &
        2. not cancelled (isCancelled = false)
    
    :bets: TODO
    :returns: TODO
    """
    valid_bets = [bet for bet in bets if 'outcome' in bet.keys() and 'isCancelled' in bet.keys()]
    return [bet for bet in valid_bets if bet['outcome'] in ['YES', 'NO'] and not bet['isCancelled']]

def get_user_info(apikey):
    """Take an api key and return information about the current user.
    
    :apikey: TODO
    :returns: TODO
    """
    url = "https://manifold.markets/api/v0/me"
    headers = {"Authorization": "Key {}".format(apikey)}
    r = requests.get(url, headers=headers)
    return r.json()

def get_market(market_id:str, apikey:str) -> dict:
    """Take an api key and market ID and return information about a given market."""
    url = "https://manifold.markets/api/v0/market/{}".format(market_id.strip())
    headers = {"Authorization": "Key {}".format(apikey)}
    r = requests.get(url, headers=headers)
    return r.json()

def calculate_market_position(user_id, market):
    """Take a user id and market object and assess the user's current position in that market."""
    user_bets = [bet for bet in market['bets'] if bet['userId'] == user_id]
    if not user_bets:
        return None
    sum_no = sum([b['amount'] for b in user_bets if b['outcome'] == 'NO'])
    sum_yes = sum([b['amount'] for b in user_bets if b['outcome'] == 'YES'])
    if sum_yes > 0:
        return {'outcome': 'YES', 'amount': sum_yes}
    elif sum_no > 0:
        return {'outcome': 'NO', 'amount': sum_no}
    else:
        return None


def suggest_bets(bets, scored_users, me, apikey):
    """Take a list of bets, scored users, and info about you,
    and use that to return a list of suggestions.
    
    :bets: TODO
    :scored_users: TODO
    :me: TODO
    :apikey: TODO
    :returns: TODO
    """
    suggested_bets = []
    # add detail to the bets list to include the user who placed the bet, the % bankroll they bet, etc.
    print("Mutating all {} bets to add information about the relevant user...".format(len(bets)))
    for bet in bets:
        try:
            relevant_user = [u for u in scored_users if bet['userId'] == u['id']][0]
        except Exception as e:
            relevant_user = None
        try:
            relevant_user_score = relevant_user['score']
        except Exception as e:
            relevant_user_score = None
        try:
            relevant_user_percentage_bankroll = abs(bet['amount'])/relevant_user['balance']
        except Exception as e:
            relevant_user_percentage_bankroll = 0
        if bet['orderAmount'] > 0:
            order_type = 'buy'
        elif bet['orderAmount'] < 0:
            order_type = 'sell'
        else:
            order_type = None
        bet['user_score'] = relevant_user_score
        bet['user_percentage_bankroll'] = min([1, relevant_user_percentage_bankroll])
        bet['order_type'] = order_type
    print("Filtering out bets that have an irrelevant order type...")
    bets = [b for b in bets if b['order_type'] is not None]
    bets = [b for b in bets if b['user_score'] is not None]
    print("Now working with {} bets".format(len(bets)))
    bets = sorted(bets, key=lambda bet: bet['user_score'])
    print("Creating raw 'suggested bets' list, and reversing extremely bad bettors ...")
    for i, bet in enumerate(bets):
        print("Creating raw 'suggested bets': {}/{}".format(i+1, len(bets)))
        if bet['user_score'] is not None and bet['user_score'] > [u for u in scored_users if u['id'] == me['id']][0]['score']: # they're better than me by the score
            suggested_bet = {'contractId': bet['contractId'], # the id for the market this bet interacts with
                             'bankrollPercentage': bet['user_percentage_bankroll'], # the percentage of the bankroll to use
                             'user_score': bet['user_score'], # the score of the user who placed the bet
                             'timestamp': bet['createdTime']} # the time of the bet being used to generate the suggestion
            if bet['order_type'] == 'sell': # switch a sell order to a buy of the reverse
                suggested_bet['action'] = 'buy'
                if bet['outcome'] == 'YES':
                    suggested_bet['outcome'] = 'NO'
                elif bet['outcome'] == 'NO':
                    suggested_bet['outcome'] = 'YES'
            elif bet['order_type'] == 'buy':
                suggested_bet['action'] = 'buy'
                suggested_bet['outcome'] = bet['outcome']
            # if the bet has a limit, add it to the suggested bet (otherwise, add None)
            suggested_bet['limitProb'] = bet.get('limitProb', None)
            suggested_bets.append(suggested_bet) # append the suggested bet to the list
        elif bet['user_score'] is not None and bet['user_score'] < [u for u in scored_users if u['id'] == me['id']][0]['score']: # they're worse than me by the score
            if bet['user_score'] < -0.25: # they're also losing money & doing badly on average, by quite a bit
                suggested_bet = {'contractId': bet['contractId'], # the id for the market this bet interacts with
                                 'bankrollPercentage': bet['user_percentage_bankroll'], # the percentage of the bankroll to use
                                 'user_score': bet['user_score'], # the score of the user who placed the bet
                                 'timestamp': bet['createdTime']} # the time of the bet being used to generate the suggestion
                # they're bad enough, that you might want to be on the opposite side of the bet they're making:
                if bet['order_type'] == 'sell':
                    suggested_bet['action'] = 'buy'
                    suggested_bet['outcome'] = bet['outcome']
                    if bet['outcome'] == 'YES':
                        suggested_bet['outcome'] = 'NO'
                    elif bet['outcome'] == 'NO':
                        suggested_bet['outcome'] = 'YES'
                elif bet['order_type'] == 'buy':
                    suggested_bet['action'] = 'buy'
                    if bet['outcome'] == 'YES':
                        suggested_bet['outcome'] = 'NO'
                    elif bet['outcome'] == 'NO':
                        suggested_bet['outcome'] = 'YES'
                suggested_bets.append(suggested_bet) # append the suggested bet to the list
            else:
                pass
        else:
            pass
    print("Total of {} raw suggested bets created".format(len(suggested_bets)))
    print("Dropping all suggested bets with an undefined/unknown user bankroll percentage")
    suggested_bets = [b for b in suggested_bets if b['bankrollPercentage'] is not None and b['bankrollPercentage'] > 0]
    print("Now working with {} raw suggested bets".format(len(suggested_bets)))
    # get information about each relevant market (including all bets in it)
    print("Getting information about each relevant market (total of {} to get)...".format(len(list(set([suggested_bet['contractId'] for suggested_bet in suggested_bets])))))
    markets = []
    for market in list(set([suggested_bet['contractId'] for suggested_bet in suggested_bets])):
        markets.append(get_market(market, apikey))
        print("Collected {}/{} markets".format(len(markets)+1, len(list(set([suggested_bet['contractId'] for suggested_bet in suggested_bets])))))
    # filter the suggested bets to remove any where the bet occurred before my own most recent bet
    print("Filtering bet suggestions to remove any where the bet occurred before my own most recent bet...")
    filtered_bet_suggestions = []
    for suggested_bet in suggested_bets:
        relevant_market = [m for m in markets if m['id']==suggested_bet['contractId']][0]
        my_bets = [bet for bet in relevant_market['bets'] if bet['userId']==me['id']]
        if len(my_bets) > 0:
            my_most_recent = max([b['createdTime'] for b in my_bets])
        else:
            my_most_recent = None
        if my_most_recent is None or suggested_bet['timestamp'] > my_most_recent:
            filtered_bet_suggestions.append(suggested_bet)
    # aggregate the per-market bet suggestions, pitting suggested bets against each other
    print("Now assessing {} suggested bets...".format(len(filtered_bet_suggestions)))
    print("Aggregating bet suggestions, pitting suggested bets against each other in each market...")
    aggregated_suggestions = []
    for market_id in list(set([b['contractId'] for b in filtered_bet_suggestions])):
        try:
            market_bets = [b for b in filtered_bet_suggestions if b['contractId']==market_id and b['action']=='buy']
            yes_percentages = []
            yes_limits = []
            no_percentages = []
            no_limits = []
            for bet in market_bets:
                if bet['outcome'] == 'YES':
                    yes_percentages.append(bet['bankrollPercentage']*abs(bet['user_score']))
                    if 'limitProb' in bet.keys():
                        yes_limits.append(bet['limitProb'])
                elif bet['outcome'] == 'NO':
                    no_percentages.append(bet['bankrollPercentage']*abs(bet['user_score']))
                    if 'limitProb' in bet.keys():
                        no_limits.append(bet['limitProb'])
            if len(yes_percentages) > 0:
                yes_percentage = sum(yes_percentages)/len(yes_percentages)
            else:
                yes_percentage = None
            if len(yes_limits) > 0:
                yes_limit = sum(yes_limits)/len(yes_limits)
            else:
                yes_limit = None
            if len(no_percentages) > 0:
                no_percentage = sum(no_percentages)/len(no_percentages)
            else:
                no_percentage = None
            if len(no_limits) > 0:
                no_limit = sum(no_limits)/len(no_limits)
            else:
                no_limit = None
            if yes_percentage is not None and no_percentage is not None:
                aggregate_outcome = 'YES' if yes_percentage > no_percentage else 'NO'
                aggregate_bankroll_percentage = yes_percentage - no_percentage
            elif yes_percentage is not None:
                aggregate_outcome = 'YES'
                aggregate_bankroll_percentage = yes_percentage
            elif no_percentage is not None:
                aggregate_outcome = 'NO'
                aggregate_bankroll_percentage = no_percentage
            else:
                aggregate_outcome = None
                aggregate_bankroll_percentage = None
            if yes_limit is not None and no_limit is not None:
                aggregate_limit = sum([yes_limit, no_limit])/2
            elif yes_limit is not None:
                aggregate_limit = yes_limit
            elif no_limit is not None:
                aggregate_limit = no_limit
            else:
                aggregate_limit = None
            aggregated_suggestions.append({'contractId': market_id,
                                        'action': 'buy',
                                        'outcome': aggregate_outcome,
                                        'bankrollPercentage': aggregate_bankroll_percentage,
                                        'limitProb': aggregate_limit,
                                        'url': [m['url'] for m in markets if m['id']==market_id][0]})
        except Exception as e:
            print("Exception occurred when trying to process bets associated with {}: {}".format(market_id, e))
            continue
    # # take the aggregated suggestions and compare to my current market position to calculate clean suggestions
    print("{} aggregated suggestions created".format(len(aggregated_suggestions)))
    print("Comparing aggregated suggestions to my current market position to calculate clean suggestions...")
    clean_suggestions = []
    for i, suggestion in enumerate(aggregated_suggestions):
        print("Processing aggregated suggestion {}/{}: {}...".format(i+1, len(aggregated_suggestions), suggestion['contractId']))
        try:
            relevant_market = [m for m in markets if m['id']==suggestion['contractId']][0]
            my_market_position = calculate_market_position(me['id'], relevant_market)
            if my_market_position is not None:
                my_market_position['bankrollPercentage'] = my_market_position['amount']/me['balance']
                if suggestion['outcome'] == my_market_position['outcome']:
                    # the suggested bet agrees with the existing position, so just append it (sized down appropriately)
                    clean_suggestion = {'contractId': suggestion['contractId'],
                                        'url': suggestion['url'],
                                        'action': suggestion['action'],
                                        'outcome': suggestion['outcome'],
                                        'bankrollPercentage': suggestion['bankrollPercentage'] - my_market_position['bankrollPercentage'],
                                        'limitProb': suggestion['limitProb']}
                    clean_suggestions.append(clean_suggestion)
                else:
                    if suggestion['bankrollPercentage'] > my_market_position['bankrollPercentage']:
                        # it looks like the suggestion overrides your existing position, so sell & then buy sized down
                        sell_suggestion = {'contractId': suggestion['contractId'],
                                        'url': suggestion['url'],
                                        'action': 'sell',
                                        'outcome': my_market_position['outcome'],
                                        'bankrollPercentage': my_market_position['bankrollPercentage'],
                                        'limitProb': my_market_position['limitProb']}
                        buy_suggestion = {'contractId': suggestion['contractId'],
                                        'url': suggestion['url'],
                                        'action': 'buy',
                                        'outcome': suggestion['outcome'],
                                        'bankrollPercentage': suggestion['bankrollPercentage'] - my_market_position['bankrollPercentage'],
                                        'limitProb': suggestion['limitProb']}
                        clean_suggestions.append(sell_suggestion)
                        clean_suggestions.append(buy_suggestion)
                    elif suggestion['bankrollPercentage'] < my_market_position['bankrollPercentage']:
                        # it looks like your market position is just too big, so sell a little off
                        sell_suggestion = {'contractId': suggestion['contractId'],
                                        'url': suggestion['url'],
                                        'action': 'sell',
                                        'outcome': my_market_position['outcome'],
                                        'bankrollPercentage': my_market_position['bankrollPercentage'] - suggestion['bankrollPercentage'],
                                        'limitProb': my_market_position['limitProb']}
                        clean_suggestions.append(sell_suggestion)
            else:
                clean_suggestion = suggestion
                clean_suggestions.append(clean_suggestion)
        except Exception as e:
            print("Exception occurred when trying to process aggregated suggestion {}: {}".format(suggestion['contractId'], e))
            continue
    return clean_suggestions



def main():
    """the main thing"""
    print("Collecting settings ...")
    settings = find_settings()
    key = settings["manifold"]["key"]
    print("Collecting a list of all active manifold users (and sorting/scoring by performance) ...")
    users = score_users(sort_manifold_users(get_manifold_users(key)))
    print("{} users found and scored".format(len(users)))
    print("Collecting list of last 1000 bets (and filtering out bets that aren't on binary questions etc) ...")
    bets = filter_bets(get_bets(key))
    print("{} bets found".format(len(bets)))
    print("Collecting info about me, the user ...")
    me = get_user_info(key)
    print("Info collected. Calculating suggested bets based on available information ...")
    suggested = suggest_bets(bets, users, me, key)
    starting_bankroll = me['balance']
    if len([s for s in suggested if s['action']=='sell']) == 0: # if you're not selling anything, then just sort by bankroll percentage
        suggested.sort(key=lambda x: x['bankrollPercentage'], reverse=True)
    for s in suggested:
        if round(s['bankrollPercentage']*starting_bankroll) < 5:
            continue
        else:
            # print the suggestion
            if s['limitProb'] is not None:
                print("Action: {} M${} of '{}' (with {} limit) in {}".format(s['action'], round(s['bankrollPercentage']*starting_bankroll), s['outcome'], s['limitProb'], s['url']))
                print("    That's at most {} of your bankroll.".format(s['bankrollPercentage']))
            else:
                print("Action: {} M${} of '{}' in {}".format(s['action'], round(s['bankrollPercentage']*starting_bankroll), s['outcome'], s['url']))
                print("    That's at most {} of your bankroll.".format(s['bankrollPercentage']))
            if s['action'] == 'buy':
                starting_bankroll -= (s['bankrollPercentage']*starting_bankroll)
            elif s['action'] == 'sell':
                starting_bankroll += (s['bankrollPercentage']*starting_bankroll)

if __name__ == "__main__":
    main()
