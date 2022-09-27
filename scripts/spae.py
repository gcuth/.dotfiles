#!/usr/bin/env python3
#
# A simple python script for forecasting via the command line.
# Currently only works for the Metaculus API.
#
# Author: Galen Cuthbertson <g@galen.me>

import os
import typer
import logging
import requests
import json
import datetime
import random
import time
import toml

DEFAULT_CONFIG_PATH = '~/.spae.toml'

logger = logging.getLogger('spae.cli')
app = typer.Typer()

def probability_to_fractional(probability: float) -> tuple:
    """
    Convert a probability to an approximate fractional betting odds as a tuple.
    eg 0.25 -> (1,3)
       0.5 -> (1,1)
       0.1 -> (1,9)

    Args:
        probability (float): The probability of the outcome.
    
    Returns:
        fractional (tuple): The approx fractional betting odds in the form (x,y).
    """
    pass #TODO: Write this!

def probability_to_moneyline(probability: float) -> int:
    """
    Convert a probability to an approximate moneyline.
    """
    percent = probability * 100
    if percent >= 50:
        return f"{round((percent/(percent - 100)) * 100)}"
    else:
        return f"+{round(((100 - percent)/(percent)) * 100)}"

def decimal_odds_to_probability(decimal_odds: float):
    """
    Convert a decimal betting odds to a probability.
    """
    pass #TODO: Write this!

def kelly(p: float, b: float) -> float:
    """
    Calculate the kelly criterion fraction of a bankroll to bet.

    Args:
        p (float): The probability of the outcome.
        b (float): The proportion of the bet gained with a win.
    
    Returns:
        f (float): The fraction of the bankroll to bet.
    """
    q = 1 - p
    f = p - (q/b)
    return f



def construct_metaculus_session(username, password, api_url='https://www.metaculus.com/api2'):
    """TODO: Docstring for construct_metaculus_session."""
    logger.info('Constructing a Metaculus session')
    s = requests.Session()
    r = s.post(f'{api_url}/accounts/login/',
            headers={"Content-Type": "application/json"},
            data=json.dumps({"username": username, "password": password}))
    if not r.ok:
        logger.error('Failed to login to Metaculus. Status code: {}'.format(r.status_code))
    else:
        logger.info('Metaculus session constructed successfully.')
    return s

def get_metaculus_results(session, url, maximum=500):
    """TODO: Docstring for get_metaculus_results."""
    results = []
    logger.info('Collecting metaculus questions beginning with {}'.format(url))
    while url is not None and len(results) < maximum:
        data = json.loads(session.get(url).text)
        url = data["next"]
        results += data["results"]
        logger.info('Collected {} metaculus questions so far'.format(len(results)))
        for result in data["results"]:
            yield result

def construct_metaculus_questions_url(filters: dict, api_url='https://www.metaculus.com/api2'):
    """"
    Create a url to get questions from the Metaculus API.
    """
    logger.info('Constructing Metaculus questions url')
    url = f'{api_url}/questions/'
    url += '?'
    for key, value in filters.items():
        url += f'{key}={value}&'
    return url[:-1]

def echo_metaculus_question(question):
    """
    Echo details of a metaculus question to the console.
    """
    typer.echo(f'{question["id"]} {question["title"]}')

def summarise_metaculus_question(question: dict) -> dict:
    """Generate a dict of some easy/convenient summary numbers for printing."""
    summary = {'id':         question['id'],
               'title':      question['title'],
               'community': {'median': None,
                             'mean': None,
                             'variance': None,
                             'forecasts': None,
                             'forecasters': None,
                             'time_since': None},
               'user':      {'latest': None,
                             'forecasts': None,
                             'time_since': None}}
    predictions = question['prediction_timeseries']
    sorted_pred = sorted(predictions, key=lambda k: k['t'])
    if len(sorted_pred) > 0:
        summary['community']['median'] = sorted_pred[-1]['community_prediction']
        summary['community']['mean'] = sorted_pred[-1]['distribution']['avg']
        summary['community']['variance'] = sorted_pred[-1]['distribution']['var']
        summary['community']['forecasts'] = sorted_pred[-1]['distribution']['num']
        summary['community']['forecasters'] = sorted_pred[-1]['num_predictions']
        summary['community']['time_since'] = datetime.datetime.now().timestamp() - sorted_pred[-1]['t']
    if question['my_predictions']:
        summary['user']['latest'] = round(question['my_predictions']['predictions'][-1]['x'], 2)
        summary['user']['forecasts'] = len(question['my_predictions']['predictions'])
        summary['user']['time_since'] = datetime.datetime.now().timestamp() - question['my_predictions']['predictions'][-1]['t']
    if summary['community']['time_since'] is not None and summary['community']['time_since'] > datetime.timedelta(days=28).total_seconds():
        summary['community']['stale'] = True
    else:
        summary['community']['stale'] = False
    if summary['user']['time_since'] is not None and abs(summary['user']['time_since'] - summary['community']['time_since']) > datetime.timedelta(days=2).total_seconds():
        summary['user']['stale'] = True
    else:
        summary['user']['stale'] = False
    if summary['user']['latest'] is not None and summary['community']['median'] is not None:
        summary['difference'] = abs(summary['user']['latest'] - summary['community']['median'])
        if summary['difference'] > summary['community']['variance']*2:
            summary['disagreement'] = 'strong'
        elif summary['difference'] > summary['community']['variance']:
            summary['disagreement'] = 'moderate'
        else:
            summary['disagreement'] = None
    else:
        summary['difference'] = None
        summary['disagreement'] = None
    return summary

def pretty_echo_metaculus_question(question):
    """
    Echo details of a single metaculus question to the console in a pretty way.
    """
    s = summarise_metaculus_question(question)
    typer.secho(f"{s['title']}", bold=True)
    if s['community']['stale']:
        typer.echo(typer.style("Community Median: ") + typer.style(f"{s['community']['median']}", bold=True, fg=typer.colors.RED))
        typer.secho(f"Warning! Community prediction is stale!", fg=typer.colors.RED)
        typer.secho(f"Time since your last prediction: {s['user']['time_since']}")
        typer.secho(f"Time since community prediction: {s['community']['time_since']}")
    else:
        typer.echo(typer.style("Community Median: ") + typer.style(f"{s['community']['median']}", bold=True))
    if s['user']['latest'] is None:
        typer.secho("You have not made a prediction yet.", fg=typer.colors.RED)
    elif s['user']['stale']:
        typer.echo(typer.style("Your prediction: ") + typer.style(f"{s['user']['latest']}", bold=True, fg=typer.colors.RED))
        typer.secho(f"Warning! Your prediction is stale!", fg=typer.colors.RED)
        typer.secho(f"Time since your last prediction: {s['user']['time_since']}")
        typer.secho(f"Time since community prediction: {s['community']['time_since']}")
    elif s['disagreement'] == 'strong':
        typer.echo(typer.style("Your prediction: ") + typer.style(f"{s['user']['latest']}", bold=True, fg=typer.colors.RED))
    elif s['disagreement'] == 'moderate':
        typer.echo(typer.style("Your prediction: ") + typer.style(f"{s['user']['latest']}", bold=True, fg=typer.colors.YELLOW))
    else:
        typer.echo(typer.style("Your prediction: ") + typer.style(f"{s['user']['latest']}", bold=True))


def predict_metaculus(session, question_id: str, data: dict, api_url="https://www.metaculus.com/api2"):
    """Post a prediction to metaculus."""
    url = f"{api_url}/questions/{question_id}/predict/"
    r = session.post(url,
        headers={
            "Content-Type": "application/json",
            "Referer": api_url,
            "X-CSRFToken": session.cookies.get_dict()["csrftoken"],
        },
        data=json.dumps(data))
    return r

def predict_metaculus_binary(session, question_id: int, prediction: float, api_url="https://www.metaculus.com/api2"):
    """TODO: Docstring for predict_metaculus_binary."""
    prediction_data = {"prediction": prediction,
                       "void": False}
    return predict_metaculus(session = session, question_id = str(question_id), data = prediction_data, api_url = api_url)

def get_metaculus_question(session, question_id: str, api_url="https://www.metaculus.com/api2"):
    """Get a question from metaculus."""
    url = f"{api_url}/questions/{question_id}"
    r = session.get(url)
    data = json.loads(r.text)
    return data

def report_on_prediction(r):
    """"Echo a report on the outcome of the prediction."""
    latest = sorted(json.loads(r.text)['predictions'], key=lambda k: k['t'])[-1]['x']
    if r.ok:
        typer.secho(f"Prediction of {latest} submitted successfully! Returned {r.status_code}", fg=typer.colors.GREEN)
    else:
        typer.secho(f"Prediction failed! Returned {r.status_code}", fg=typer.colors.RED)


def read_config(path: str) -> dict:
    """Read the config from a .toml file found at the path."""
    config = toml.load(path)
    return config



@app.command()
def configure(
    platform: str = typer.Option(default="metaculus", prompt="Forecasting Platform"),
    username: str = typer.Option(default=None, prompt="Username"),
    password: str = typer.Option(default=None, prompt="Password", confirmation_prompt=True, hide_input=True)
    ):
    """Configure the spae.toml file."""
    if not os.path.exists(os.path.expanduser(DEFAULT_CONFIG_PATH)):
        logger.info('Config file does not exist at {}. Creating it.'.format(DEFAULT_CONFIG_PATH))
        typer.echo(f"No config file found at {DEFAULT_CONFIG_PATH}")
        typer.echo(f"Creating config file at {DEFAULT_CONFIG_PATH} now using provided platform settings...")
    else:
        typer.echo(f"Existing config file found at {DEFAULT_CONFIG_PATH}")
        typer.echo(f"Updating existing config file at {DEFAULT_CONFIG_PATH} with provided platform settings...")
    # write_config(platform, username, password, os.path.expanduser(DEFAULT_CONFIG_PATH))


@app.command()
def ls():
    """List available questions."""
    config = read_config(os.path.expanduser(DEFAULT_CONFIG_PATH))
    questions = []
    for platform in config:
        if platform == 'metaculus':
            session = construct_metaculus_session(config[platform]['username'], config[platform]['password'])
            url = construct_metaculus_questions_url(filters={'order_by': 'resolve_time', 'status': 'open', 'type': 'forecast'})
            typer.echo(f"Collecting questions from {platform} starting at {url}")
            with typer.progressbar(get_metaculus_results(session, url, 1000), length=1000, label='Metaculus Questions Collected', show_percent=True, show_eta=False) as bar:
                for question in bar:
                    questions.append(question)
        # TODO: Add support for non-metaculus platforms!
    typer.echo(f"Found {len(questions)} questions.")
    typer.echo(f"Filtering question list for only binary questions...")
    questions = [q for q in questions if q['possibilities']['type'] == 'binary']
    typer.echo(f"Found {len(questions)} binary questions.")
    typer.echo(f"Echoing questions to console...")
    for question in questions:
        echo_metaculus_question(question)

@app.command()
def elicit():
    """Find suitable questions and elicit predictions on them."""
    config = read_config(os.path.expanduser(DEFAULT_CONFIG_PATH))
    if 'metaculus' not in config:
        typer.echo(f"No Metaculus config found. Please run configure to add details to spaerc.")
        return None
    session = construct_metaculus_session(config['metaculus']['username'], config['metaculus']['password'])
    for platform in config:
        if platform == 'metaculus':
            order = random.choice(['resolve_time', '-publish_time', 'close_time'])
            url = construct_metaculus_questions_url(filters={'order_by': order, 'status': 'open', 'type': 'forecast'})
            typer.echo(f"Collecting questions from {platform} starting at {url}")
            for question in get_metaculus_results(session, url, 10000):
                if question['possibilities']['type'] == 'binary':
                    summary = summarise_metaculus_question(question)
                    if summary['user']['latest'] is None:
                        typer.secho(f"{summary['title']}", bold=True)
                        typer.secho(f"No existing user prediction!", fg=typer.colors.RED)
                        prediction = typer.prompt(summary['title'], type=float, default=summary['community']['median'] or 0.5)
                        r = predict_metaculus_binary(session, question_id = summary['id'], prediction = prediction, api_url="https://www.metaculus.com/api2")
                        report_on_prediction(r)
                        typer.echo("")
                    elif summary['community']['stale'] is True:
                        typer.secho(f"{summary['title']}", bold=True)
                        typer.echo(typer.style("Stale ", bold=True, fg=typer.colors.RED) +
                                   typer.style("community prediction: ") +
                                   typer.style(f"{summary['community']['median']}", bold=True, fg=typer.colors.RED) +
                                   typer.style(f" ({datetime.timedelta(seconds=summary['community']['time_since'])} since last update)"))
                        prediction = typer.prompt(summary['title'], type=float, default=summary['user']['latest'] or 0.5)
                        r = predict_metaculus_binary(session, question_id = summary['id'], prediction = prediction, api_url="https://www.metaculus.com/api2")
                        report_on_prediction(r)
                        typer.echo("")
                    elif summary['user']['stale'] is True and summary['disagreement'] is not None:
                        typer.secho(f"{summary['title']}", bold=True)
                        typer.echo(typer.style("Stale ", bold=True, fg=typer.colors.RED) +
                                   typer.style("user prediction: ") +
                                   typer.style(f"{summary['user']['latest']}", bold=True, fg=typer.colors.RED) +
                                   typer.style(f" ({datetime.timedelta(seconds=summary['user']['time_since'])} behind the community)"))
                        prediction = typer.prompt(summary['title'], type=float, default=summary['community']['median'] or 0.5)
                        if prediction != summary['user']['latest']:
                            r = predict_metaculus_binary(session, question_id = summary['id'], prediction = prediction, api_url="https://www.metaculus.com/api2")
                            report_on_prediction(r)
                        else:
                            typer.secho("Prediction unchanged.", fg=typer.colors.RED)
                        typer.echo("")
                    elif summary['disagreement'] == 'strong' or summary['disagreement'] == 'moderate':
                        if summary['user']['time_since'] > datetime.timedelta(days=1).total_seconds():
                            if summary['disagreement'] == 'strong':
                                discolour = typer.colors.RED
                            else:
                                discolour = typer.colors.YELLOW
                            typer.secho(f"{summary['title']}", bold=True)
                            typer.echo(typer.style("Your prediction is ") +
                                    typer.style(f"{summary['user']['latest']}", bold=True, fg=discolour) +
                                    typer.style(" against the community prediction of ") +
                                    typer.style(f"{summary['community']['median']}", bold=True, fg=discolour))
                            prediction = typer.prompt(summary['title'], type=float, default=summary['user']['latest'] or 0.5)
                            if prediction != summary['user']['latest']:
                                r = predict_metaculus_binary(session, question_id = summary['id'], prediction = prediction, api_url="https://www.metaculus.com/api2")
                                report_on_prediction(r)
                            else:
                                typer.secho("Prediction unchanged.", fg=discolour)
                            typer.echo("")
                    else:
                        continue




@app.command()
def predict(id: int = typer.Option(default=None, prompt="Question ID"),
            prediction: float = typer.Option(default=None)):
    """Post a forecast for a question."""
    config = read_config(os.path.expanduser(DEFAULT_CONFIG_PATH))
    if 'metaculus' not in config:
        typer.echo(f"No Metaculus config found. Please run configure to add details to spaerc.")
        return None
    session = construct_metaculus_session(config['metaculus']['username'], config['metaculus']['password'])
    if prediction is None:
        q = get_metaculus_question(session, question_id = str(id))
        s = summarise_metaculus_question(q)
        prediction = typer.prompt(s['title'], type=float, default=s['community']['median'] or 0.5)
    predict_metaculus_binary(session, question_id = id, prediction = prediction, api_url="https://www.metaculus.com/api2")

@app.command()
def now(id: int = typer.Option(default=None, prompt="Question ID", help="ID of question to predict"),
        pretty: bool = typer.Option(default=False, help="Pretty print key information about the question"),
        json: bool = typer.Option(default=False, help="Print JSON output"),
        ):
    """Report the current status of a question."""
    config = read_config(os.path.expanduser(DEFAULT_CONFIG_PATH))
    if 'metaculus' not in config:
        typer.echo(f"No Metaculus config found. Please run configure to add details to spaerc.")
        typer.exit(1)
    else:
        session = construct_metaculus_session(config['metaculus']['username'], config['metaculus']['password'])
        question = get_metaculus_question(session, question_id = id)
        if json:
            typer.echo(json.dumps(question, indent=2))
        elif pretty:
            pretty_echo_metaculus_question(question)
        else:
            summary = summarise_metaculus_question(question)
            typer.echo(round((summary['community']['median'] + summary['community']['mean'])/2,2))


@app.command()
def adopt(id: int = typer.Option(default=None, help="ID of question to adopt"),
          mode: str = typer.Option(default="median", help="Value to adopt prediction from ['median, 'mean', 'both']"),):
    """Adopt a prediction from the community for a question given by id."""
    config = read_config(os.path.expanduser(DEFAULT_CONFIG_PATH))
    if 'metaculus' not in config:
        typer.echo(f"No Metaculus config found. Please run configure to add details to spaerc.")
        typer.exit(1)
    else:
        session = construct_metaculus_session(config['metaculus']['username'], config['metaculus']['password'])
        if id is None:
            order = random.choice(['resolve_time', '-publish_time', 'close_time'])
            url = construct_metaculus_questions_url(filters={'order_by': order, 'status': 'open', 'type': 'forecast'})
            typer.echo(f"Collecting questions from metaculus starting at {url}")
            for question in get_metaculus_results(session, url, 20000):
                id = question['id']
                typer.echo(f"{id} {question['title']}")
                try:
                    if question['possibilities']['type'] == 'binary':
                            summary = summarise_metaculus_question(question)
                            if mode == "median":
                                prediction = summary['community']['median']
                            elif mode == "mean":
                                prediction = summary['community']['mean']
                            elif mode == "both":
                                prediction = round((summary['community']['median'] + summary['community']['mean'])/2,2)
                            else:
                                typer.echo(f"Unknown mode {mode}")
                                typer.exit(1)
                            typer.echo(f"Adopting {prediction} as new user prediction")
                            if summary['user']['latest'] != prediction:
                                r = predict_metaculus_binary(session, question_id = id, prediction = prediction, api_url="https://www.metaculus.com/api2")
                                if r.ok:
                                    typer.echo("{} successfully adopted: {} switched to {}".format(id, summary['user']['latest'], prediction))
                                else:
                                    typer.echo(f"{id} failed to adopt (returned {r.status_code})")
                                time.sleep(random.randint(1,13))
                except:
                    typer.echo(f"{id} failed to adopt")
        else:
            question = get_metaculus_question(session, question_id = id)
            summary = summarise_metaculus_question(question)
            if mode == "median":
                prediction = summary['community']['median']
            elif mode == "mean":
                prediction = summary['community']['mean']
            elif mode == "both":
                prediction = round((summary['community']['median'] + summary['community']['median'] + summary['community']['mean'])/3,2)
            else:
                typer.echo(f"Unknown mode {mode}")
                typer.exit(1)
            if summary['user']['latest'] != prediction:
                r = predict_metaculus_binary(session, question_id = id, prediction = prediction, api_url="https://www.metaculus.com/api2")
                if r.ok:
                    typer.echo("{} successfully adopted: {} switched to {}".format(id, summary['user']['latest'], prediction))
                else:
                    typer.echo(f"{id} failed to adopt (returned {r.status_code})")

if __name__ == "__main__":
    app()
