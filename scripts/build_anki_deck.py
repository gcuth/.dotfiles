#!/usr/bin/env python3
#
# Usage: ./build_anki_deck.py --input <input_dir> --output <path/to/deck.apkg>
#
# This script takes a directory of json files, each containing question/answer
# pairs suitable for use in a simple Anki deck. It creates a new Anki deck at
# the target path, and adds the question/answer pairs to the deck. Hardcoded
# values are used for the deck name, note type, ids, models, etc.

import os
import sys
import json
import argparse
import genanki as anki
import markdown2 as md
import html
import time

###############################################################################
############################### DEFAULTS/MODELS ###############################
###############################################################################

DEFAULT_DECK_ID = 2050113323
DEFAULT_DECK_NAME = "Reading"

# CSS for the cards; we use a custom CSS to style, with the source (if any)
# appearing in a smaller font at the bottom right of the view.
DEFAULT_CARD_CSS = '''
html { overflow: scroll; overflow-x: hidden; }

#kard {
    padding: 0px 0px;
    wax-width: 700px;
    margin: 0 auto; /* Centre the card in the middle of the window */
    word-wrap: break-word;
}

.card {
  font-family: Helvetica, Arial, sans-serif;
  font-size: 18px;
  text-align: center;
  color: black;
  background-color: white;
  /* position: relative; Make the card a relative container */
  padding-bottom: 30px; /* Add space at the bottom for the source */
}

.source {
  position: absolute; /* Position it absolutely within .card */
  bottom: 0; /* Align to the bottom */
  right: 0; /* Align to the right */
  font-size: 14px; /* Smaller font size for the source text */
  padding: 5px; /* Some padding */
  color: #666666; /* Gray color */
}

/* styling for multiple choice options when answer is revealed */
.correct {
    color: #009000;
}
.incorrect {
    color: #666666;
}

/* styling for code snippets */
code {
    background-color: #f4f4f4;
    border-radius: 3px;
    font-size: 0.9em;
    padding: 0.2em 0.4em;
}

/* styling for lists; left align and add some padding */
ul, ol {
    text-align: left;
    padding-left: 20px;
}
'''

BASIC_MODEL_ID = 2074017087
BASIC_MODEL = anki.Model(
    BASIC_MODEL_ID,
    'Basic Question Answer Pair with Source',
    fields=[
        {'name': 'Unique ID'}, # Unique identifier for the question
        {'name': 'Question'}, # The question text (html)
        {'name': 'Answer'}, # The answer text (html)
        {'name': 'Source'}, # The source of the question (if any)
    ],
    templates=[
        {
            'name': 'Card 1',
            'qfmt': '''{{Question}}
            <hr id="answer">
            <br>
            <span class="source">{{Source}}</span>
            ''',
            'afmt': '''{{Question}}
            <hr id="answer">
            {{Answer}}
            <br>
            <span class="source">{{Source}}</span>''',
        }
    ],
    css = DEFAULT_CARD_CSS
)


MULTIPLE_CHOICE_MODEL_ID = 1638490282
MULTIPLE_CHOICE_MODEL = anki.Model(
    MULTIPLE_CHOICE_MODEL_ID,
    'Multiple Choice Question with Source',
    fields=[
        {'name': 'Unique ID'}, # Unique identifier for the question
        {'name': 'Question'}, # The question text (html)
        {'name': 'OptionA'}, # The first option (html)
        {'name': 'OptionB'}, # The second option (html)
        {'name': 'OptionC'}, # The third option (html)
        {'name': 'OptionD'}, # The fourth option (html)
        {'name': 'AnswerA'}, # The first option (with span class for correct)
        {'name': 'AnswerB'}, # The second option (with span class for correct)
        {'name': 'AnswerC'}, # The third option (with span class for correct)
        {'name': 'AnswerD'}, # The fourth option (with span class for correct)
        {'name': 'Answer'}, # The correct answer (html)
        {'name': 'Source'}, # The source of the question (if any)
    ],
    templates=[
        {
            'name': 'Card 1',
            'qfmt': '''{{Question}}
            <br>
            <ol>
            <li>{{OptionA}}</li>
            <li>{{OptionB}}</li>
            <li>{{OptionC}}</li>
            <li>{{OptionD}}</li>
            </ol>
            <br>
            <span class="source">{{Source}}</span>
            ''',
            'afmt': '''{{Question}}
            <br>
            <ol>
            <li>{{AnswerA}}</li>
            <li>{{AnswerB}}</li>
            <li>{{AnswerC}}</li>
            <li>{{AnswerD}}</li>
            </ol>
            <br>
            <span class="source">{{Source}}</span>
            '''
        }
    ],
    css = DEFAULT_CARD_CSS
)


###############################################################################
############################### BASIC FUNCTIONS ###############################
###############################################################################

def list_question_files(dir: str) -> list:
    """Return a list of all the json files in the input directory."""
    return [os.path.join(dir, f) for f in os.listdir(dir) if f.endswith(".json")]


def load_question_file(file_path: str) -> dict:
    """Return the contents of a json file as a dictionary."""
    with open(file_path, "r") as f:
        return json.load(f)


def filter_question_files(files: list, keys=[], kv_pairs={}) -> list:
    """Return a list of the json file paths that contain the required keys."""
    filtered_files = []
    for file in files:
        with open(file, "r") as f:
            data = json.load(f)
            if all(k in data for k in keys):
                if all(data[k] == kv_pairs[k] for k in kv_pairs):
                    filtered_files.append(file)
    return filtered_files


def get_question_list(files: list, note_type='basic') -> list:
    """
    Given a list of file paths, return a list of dictionaries, each containing
    data suitable for building a note of a given type (eg, 'basic' q/a pairs).
    """
    # Check note type; only 'basic' and 'choice' are supported
    if note_type not in ['basic', 'choice']:
        raise ValueError("Invalid note type: must be 'basic' or 'choice'.")
    
    questions = [] # array to store list of question dictionaries

    # filter files based on note type
    if note_type == 'basic':
        files = filter_question_files(files,
                                      keys=["question", "answer"],
                                      kv_pairs={'draft': False,
                                                'checked': True})
    elif note_type == 'choice':
        files = filter_question_files(files,
                                      keys=["question", "options", "answer"],
                                      kv_pairs={'draft': False,
                                                'checked': True})
    else:
        raise ValueError("Invalid note type: must be 'basic' or 'choice'.")
    
    # load question data from each file
    for file in files:
        fname = os.path.basename(file)
        # get digits from filename to use as unique id
        unique_id = ''.join(filter(str.isdigit, fname))
        # if unique id is empty, generate a random one
        if not unique_id:
            unique_id = str(hash(fname))
        raw = load_question_file(file)
        # check note type and extract data accordingly
        if note_type == 'basic':
            if 'question' in raw and 'answer' in raw:
                data = {'unique_id': unique_id,
                        'question': raw['question'],
                        'answer': raw['answer'],
                        'source': raw.get('source', ''),
                        'tags': raw.get('tags', '')}
                data['tags'] = data['tags'].split(",") if data['tags'] else []
                data['tags'] = [t.strip() for t in data['tags']]
                data['tags'] = [t.replace(" ", "_") for t in data['tags']]
                questions.append(data)
        elif note_type == 'choice':
            if 'question' in raw and 'options' in raw and 'answer' in raw:
                data = {'unique_id': unique_id,
                        'question': raw['question'],
                        'options': raw['options'],
                        'answer': raw['answer'],
                        'source': raw.get('source', ''),
                        'tags': raw.get('tags', '')}
                data['tags'] = data['tags'].split(",") if data['tags'] else []
                data['tags'] = [t.strip() for t in data['tags']]
                data['tags'] = [t.replace(" ", "_") for t in data['tags']]
                if len(data['options']) == 4: # only add if there are 4 options
                    if data['answer'] in data['options']: # and answer is there
                        questions.append(data)
    # return list of question dictionaries
    return questions


def clean_question_data(data: dict) -> dict:
    """Take a dictionary of question data and clean it up."""
    # if a source is provided, confirm that it's not just a raw URL!
    if data['source']:
        if data['source'].startswith("http"):
            data['source'] = ""
    # if tags are provided, split them into a list if they're not already
    if data['tags']:
        if isinstance(data['tags'], str):
            data['tags'] = data['tags'].split(",")
            data['tags'] = [t.strip() for t in data['tags']]
            data['tags'] = [t.replace(" ", "_") for t in data['tags']]
        elif isinstance(data['tags'], list):
            data['tags'] = [t.strip() for t in data['tags']]
            data['tags'] = [t.replace(" ", "_") for t in data['tags']]
        else:
            data['tags'] = []
    # return the cleaned data
    return data


###############################################################################
############################### ANKI FUNCTIONS ################################
###############################################################################

def build_basic_note(data: dict) -> anki.Note:
    """Build a basic note from a dictionary of question data."""
    return anki.Note(
        model=BASIC_MODEL,
        fields=[str(data['unique_id']),
                str(md.markdown(data['question'])),
                str(md.markdown(data['answer'])),
                str(data['source'])],
        tags=data['tags'])


def build_choice_note(data: dict) -> anki.Note:
    """Build a multiple choice note from a dictionary of question data."""
    # identify the correct answer and wrap it in a span with the correct class
    options = [str(md.markdown(o)) for o in data['options']]
    answers = []
    for option in data['options']:
        if option == data['answer']:
            answers.append(f'<span class="correct">{str(md.markdown(option))}</span>')
        else:
            answers.append(f'<span class="incorrect">{str(md.markdown(option))}</span>')
    return anki.Note(
        model=MULTIPLE_CHOICE_MODEL,
        fields=[str(data['unique_id']),
                str(md.markdown(data['question'])),
                options[0], options[1], options[2], options[3],
                answers[0], answers[1], answers[2], answers[3],
                str(md.markdown(data['answer'])),
                str(data['source'])],
        tags=data['tags'])


def build_deck(notes: list, id=DEFAULT_DECK_ID, name=DEFAULT_DECK_NAME) -> anki.Deck:
    """Build an Anki deck from a list of notes."""
    deck = anki.Deck(id, name)
    for note in notes:
        deck.add_note(note)
    return deck


###############################################################################
################################ MAIN FUNCTION ################################
###############################################################################

def get_args():
    """Parse all command line arguments."""
    parser = argparse.ArgumentParser(
        description="Build an Anki deck from a directory of json files.")
    parser.add_argument("--input", required=True,
                        help="Path to the input directory.")
    parser.add_argument("--output", required=True,
                        help="Path to the output file.")
    args = parser.parse_args()
    return args


def main():
    """Main function."""
    args = get_args()
    question_files = list_question_files(args.input)
    print(f"Found {len(question_files)} question files.")
    # get basic questions and build basic notes
    basic_questions = get_question_list(question_files, note_type='basic')
    basic_questions = [clean_question_data(q) for q in basic_questions]
    basic_notes = [build_basic_note(q) for q in basic_questions]
    # get multiple choice questions and build choice notes
    choice_questions = get_question_list(question_files, note_type='choice')
    choice_questions = [clean_question_data(q) for q in choice_questions]
    choice_notes = [build_choice_note(q) for q in choice_questions]
    # combine notes and build deck
    all_notes = basic_notes + choice_notes
    deck = build_deck(all_notes)
    anki.Package(deck).write_to_file(args.output)


if __name__=="__main__":
    main()