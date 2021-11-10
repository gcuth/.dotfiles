#!/usr/bin/env python3

import os
import sys
import re
import json
import requests
from pyzotero import zotero

def get_library_id():
    """
    Get the library ID from the user.
    """
    library_id = input("Enter the library ID: ")
    return library_id

def get_api_key():
    """
    Get the API key from the user.
    """
    api_key = input("Enter the API key: ")
    return api_key

def load_configuration(fp='~/.zoterocladd.json'):
    """
    Load the configuration file.
    """
    fp = os.path.expanduser(fp)
    if os.path.exists(fp):
        with open(fp, 'r') as f:
            config = json.load(f)
        return config
    else:
        print("Configuration file not found. Creating now...")
        config = {}
        config['library_id'] = get_library_id()
        config['api_key'] = get_api_key()
        with open(fp, 'w+') as f:
            json.dump(config, f)
        return config

def get_zotero_client(config):
    """
    Get the Zotero client.
    """
    client = zotero.Zotero(config['library_id'], 'user', config['api_key'])
    return client

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

def get_citations_from_citoid(search):
    """
    Get the (zotero-formed) citation from the wikipedia Citoid API.
    """
    search = search.strip().replace('/', '%2F')
    url = 'https://en.wikipedia.org/api/rest_v1/data/citation/zotero/' + search
    r = requests.get(url)
    if r.status_code == 200:
        return r.json()
    else:
        return None

def add_item(client, raw_item):
    """
    Add an item to the Zotero library.
    """
    doi = extract_doi_from_string(raw_item)
    isbn = extract_isbn_from_string(raw_item)
    if doi:
        print("Found DOI: " + doi)
        citations = get_citations_from_citoid(doi)
        if citations and isinstance(citations, list):
            print(f'Found {len(citations)} citations: ' + str(citations))
            client.create_items(citations)
        else:
            print("No citations found.")
    elif isbn:
        print("Found ISBN: " + isbn)
        citations = get_citations_from_citoid(isbn)
        if citations and isinstance(citations, list):
            print(f'Found {len(citations)} citations: ' + str(citations))
            client.create_items(citations)
        else:
            print("No citations found.")
    else:
        print("No DOI or ISBN found.")

def main():
    """
    Main function.
    """
    config = load_configuration()
    zot = get_zotero_client(config)
    to_add = sys.argv[1:]
    for i in to_add:
        add_item(zot, i)

if __name__ == '__main__':
    main()
