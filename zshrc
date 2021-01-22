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

# Set nvim as default
if [ -f /usr/bin/nvim ]; then
    export EDITOR="/usr/bin/nvim"
    export VISUAL="/usr/bin/nvim"
fi

CASE_SENSITIVE="false"

plugins=(
    git
    )

DISABLE_LS_COLORS="false"

# use starship if it's available
eval "$(starship init zsh)"
