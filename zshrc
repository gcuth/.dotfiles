#!/bin/zsh
# HISTORY
# don't add duplicate lines (or lines beginning with spaces) into the history
HISTCONTROL=ignoreboth
# set sensible lengths
HISTSIZE=5000
HISTFILESIZE=10000
# set a callable history
export PROMPT_COMMAND="history -a; history -c; history -r; $PROMPT_COMMAND"
# etc
HIST_STAMPS="yyyy-mm-dd"

# Load aliases
if [ -f ~/.aliases ]; then
    . ~/.aliases
fi

CASE_SENSITIVE="false"

plugins=(
    git
    )

DISABLE_LS_COLORS="false"

## Setup oh-my-zsh
export ZSH=$HOME/.oh-my-zsh;
ZSH_THEME="custom"
source $ZSH/oh-my-zsh.sh;

# Include anaconda
PATH="~/anaconda/bin:$PATH"
