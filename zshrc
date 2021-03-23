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

# Add pyenv to path
export PATH="$HOME/.pyenv/bin:$PATH"
eval "$(pyenv init -)"
eval "$(pyenv virtualenv-init -)"

# Add poetry to path
export PATH="$HOME/.poetry/bin:$PATH"


# Fix term
export TERM=xterm-256color

CASE_SENSITIVE="false"

plugins=(
    git
    )

DISABLE_LS_COLORS="false"

# use starship if it's available
eval "$(starship init zsh)"
