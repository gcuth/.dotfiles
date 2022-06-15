#!/usr/bin/env python3
#
# A script that scrapes a web page for a blog post etc, then uses AWS Polly to
# convert the content text to an audio file suitable for a private podcast.
#
# Usage:
#   as_audio.py <url> 

import os
import sys
import re
import requests
import argparse
import boto3
import html2text
from markdown import markdown
from bs4 import BeautifulSoup
from readability import Document
import pydub

def extract_doi_from_string(s):
    """
    Extract the DOI from a string.
    """
    doi = re.search(r'(10\.\d{4,9}/[-._;()/:A-Za-z0-9]+|10.1002/[^\s]+|10.\d{4}/\d+-\d+X?(\d+)\d+<[\d\w]+:[\d\w]*>\d+.\d+.\w+;\d|10.1021/\w\w\d+|10.1207/[\w\d]+\&\d+_\d+)', s)
    if doi:
        return doi.group()
    else:
        return None

def extract_isbn_from_string(s):
    """
    Extract the ISBN from a string.
    """
    isbn = re.search(r'(?=(?:\D*\d){10}(?:(?:\D*\d){3})?$)[\d-]+', s)
    if isbn:
        return str(isbn.group()).replace('-',"")
    else:
        return None

def get_article_doc(url: str) -> Document:
    """
    Get the Document version of the article from the given URL.
    """
    response = requests.get(url)
    if response.status_code != 200:
        print("Error: {}".format(response.status_code))
        raise Exception("Failed to get article text from URL: {}".format(url))
    html = response.text
    doc = Document(html)
    return doc

def convert_doc_to_dict(doc: Document, strip_html=True) -> dict:
    """
    Convert the Document to a dictionary of the article's text and title.
    """
    if strip_html:
        h = html2text.HTML2Text()
        # ignore links
        h.ignore_links = True
        text = h.handle(doc.summary())
    else:
        text = doc.summary()
    return {
        "title": doc.title(),
        "text": text
    }

def split_article_text(text: str, max_length=3000) -> list:
    """
    Split the article text into paragraphs as best as possible.
    """
    if '\n\n' in text:
        split_text = text.split('\n\n')
        split_text = [p.replace('\n', ' ').strip() for p in split_text if p.strip()]
    else:
        split_text = text.split('\n')
    clean_split_text = []
    split_text = [p.strip() for p in split_text if len(p.strip()) > 0]
    for p in split_text:
        if p.startswith('>'):
            p = p[1:].strip()
        if len(p) >= max_length:
            # find a space to split on
            sentences = p.split('.')
            parts = [""]
            for s in sentences:
                latest_part = parts[-1] if len(parts) > 0 else ""
                if len(latest_part) + len(s) + 1 >= max_length:
                    parts.append(s)
                else:
                    parts[-1] = "{}.{}".format(latest_part, s)
            clean_split_text.extend(parts)
        else:
            clean_split_text.append(p)
    return clean_split_text


def strip_markdown(text: str) -> str:
    """
    Strip out markdown from the given text snippet.
    """
    html = markdown(text)
    soup = BeautifulSoup(html, 'html.parser')
    return soup.get_text()

def build_polly_client():
    """
    Build a Polly client.
    """
    return boto3.client('polly')

def generate_temp_id():
    """
    Generate a temporary ID for a file.
    """
    return os.urandom(16).hex()

def get_and_prep_for_polly(url: str, remove_html=True, remove_markdown=True):
    """Take a url; prep a dict containing a list of polly-suitable texts."""
    try:
        raw_doc = get_article_doc(url)
        clean_doc = convert_doc_to_dict(raw_doc, strip_html=remove_html)
        title = clean_doc['title']
        text = clean_doc['text']
        if remove_markdown:
            paragraphs = [strip_markdown(p) for p in split_article_text(text)]
        else:
            paragraphs = split_article_text(text)
        paragraphs = [p.strip().replace('>','').replace('  ',' ') for p in paragraphs if len(p.strip()) > 0]
        # add the title to the first paragraph
        if len(title.strip()) > 0:
            paragraphs.insert(0, title.strip())
        return {
            "title": title,
            "text": paragraphs,
            "url": url,
            "id": generate_temp_id()
        }
    except Exception as e:
        print("Error: {}".format(e))
        return None

def send_and_save(text:str, polly_client, outdir, id, n, voice="Matthew"):
    """
    Send a short string to a polly client, then save the resulting file.
    """
    response = polly_client.synthesize_speech(
        OutputFormat='mp3',
        Text=text,
        VoiceId=voice,
        TextType='text',
        Engine='neural'
    )
    if 'AudioStream' in response:
        with open("{}/{}-{}.mp3".format(outdir, id, str(n).zfill(6)), 'wb') as f:
            f.write(response['AudioStream'].read())

def list_temp_audio_files(outdir, id):
    """
    Find all the temporary audio files for a given id; return a list in order.
    """
    files = [os.path.join(outdir, f) for f in os.listdir(outdir) if id in f]
    files.sort()
    return files

def generate_combined_audio_outpath(outdir, id):
    """
    Generate the output path for the combined audio file.
    """
    return os.path.join(outdir, "{}.mp3".format(id))

def join_audio_files(files, outdir, id, buffer_silence=1000):
    """
    Join all the audio files into a single file.
    """
    audio_files = [pydub.AudioSegment.from_mp3(f) for f in files]
    silence = pydub.AudioSegment.silent(duration=buffer_silence)
    joined_audio = pydub.AudioSegment.empty()
    for i, audio in enumerate(audio_files):
        joined_audio += audio
        joined_audio += silence
    combined_outpath = generate_combined_audio_outpath(outdir, id)
    joined_audio.export(combined_outpath, format="mp3")
    if not os.path.exists(combined_outpath):
        print("Attempt to saved combined audio to {} failed for an unknown reason!".format(combined_outpath))


def build_final_outpath(outdir, document_dict):
    """
    Build the final output path for the given document.
    """
    if 'title' in document_dict.keys() and len(document_dict['title']) > 0:
        final_name = "{}.mp3".format(document_dict['title'].strip())
        final_outpath = os.path.join(outdir, final_name)
        return final_outpath
    else:
        return None

def rename_combined_file(temp_outdir, id, final_outdir, document_dict):
    """
    Rename the combined file to the final name.
    """
    combined_outpath = generate_combined_audio_outpath(temp_outdir, id)
    final_outpath = build_final_outpath(final_outdir, document_dict)
    if final_outpath is not None:
        os.rename(src=combined_outpath, dst=final_outpath)
    else:
        final_name = "{}.mp3".format(id)
        final_outpath = os.path.join(final_outdir, final_name)
        os.rename(src=combined_outpath, dst=final_outpath)

def make_audible(url, temp_dir="/Users/g/Desktop", final_dir="/Users/g/Desktop", remove_html=True, remove_markdown=True, buffer_silence=1000, voice="Matthew"):
    """
    Take a url and generate an audio file from it.
    """
    document = get_and_prep_for_polly(url)
    if document is not None:
        final_outpath = build_final_outpath(final_dir, document) # use to check if it already exists!
        if final_outpath is not None and os.path.exists(final_outpath):
            print("{} already exists! Skipping.".format(final_outpath))
            return None
        else:
            client = build_polly_client()
            for n, text in enumerate(document['text']):
                send_and_save(text, client, temp_dir, document['id'], n, voice=voice)
            temp_files = list_temp_audio_files(temp_dir, document['id'])
            join_audio_files(temp_files, temp_dir, document['id'])
            rename_combined_file(temp_dir, document['id'], final_dir, document)
            # remove temp files
            for f in temp_files:
                os.remove(f)

def get_args(sysargs = sys.argv[1:]):
    """
    Get the command line arguments.
    """
    parser = argparse.ArgumentParser(description="Make an audio file from a given URL.")
    parser.add_argument("url", help="The URL to make an audio file from.")
    parser.add_argument("--temp_dir", help="The temporary directory to save the in-process audio files.", default="/Users/g/Desktop")
    parser.add_argument("--final_dir", help="The directory to save the final audio file.", default="/Users/g/Desktop")
    parser.add_argument("--remove_html", help="Remove HTML from the text.", action="store_true", default=True)
    parser.add_argument("--remove_markdown", help="Remove markdown from the text.", action="store_true", default=True)
    parser.add_argument("--buffer_silence", help="The amount of silence to add between each audio file.", default=1000, type=int)
    parser.add_argument("--voice", help="The Polly voice to use.", default="Matthew")
    return parser.parse_args(sysargs)

def main():
    """
    Get the arguments and make an audio file.
    """
    args = get_args(sys.argv[1:])
    if args.url is not None and args.url != "":
        # make sure the link doesn't have a doi or isbn
        doi = extract_doi_from_string(args.url)
        isbn = extract_isbn_from_string(args.url)
        if doi is None and isbn is None and not str(args.url).endswith('.pdf'):
            make_audible(args.url,
                         args.temp_dir,
                         args.final_dir,
                         args.remove_html,
                         args.remove_markdown,
                         args.buffer_silence,
                         args.voice)


if __name__ == "__main__":
    main()