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

# Add conda to path
export PATH=/home/g/miniconda/bin:$PATH

# Fix term
export TERM=xterm-256color

CASE_SENSITIVE="false"

plugins=(
    git
    )

DISABLE_LS_COLORS="false"

# use starship if it's available
eval "$(starship init zsh)"

# >>> conda initialize >>>
# !! Contents within this block are managed by 'conda init' !!
__conda_setup="$('/home/g/miniconda/bin/conda' 'shell.zsh' 'hook' 2> /dev/null)"
if [ $? -eq 0 ]; then
    eval "$__conda_setup"
else
    if [ -f "/home/g/miniconda/etc/profile.d/conda.sh" ]; then
        . "/home/g/miniconda/etc/profile.d/conda.sh"
    else
        export PATH="/home/g/miniconda/bin:$PATH"
    fi
fi
unset __conda_setup
# <<< conda initialize <<<

