#!/bin/zsh
# HISTORY
# don't add duplicate lines (or lines beginning with spaces) into the history
setopt inc_append_history
HISTCONTROL=ignoreboth
# set sensible lengths
HISTSIZE=5000
HISTFILESIZE=10000
# set a callable history
export PROMPT_COMMAND="history -a; history -c; history -r; $PROMPT_COMMAND"
# etc
HIST_STAMPS="yyyy-mm-dd"


# Set editor with a tonne of fallbacks
if [ -f /snap/bin/codium ]; then
    export EDITOR="/snap/bin/codium"
    export VISUAL="/snap/bin/codium"
elif [ -f /snap/bin/code ]; then
    export EDITOR="/snap/bin/code"
    export VISUAL="/snap/bin/code"
elif [ -f /snap/bin/emacsclient ]; then
    export EDITOR="/snap/bin/emacsclient"
    export VISUAL="/snap/bin/emacsclient"
elif [ -f /snap/bin/emacs ]; then
    export EDITOR="/snap/bin/emacs"
    export VISUAL="/snap/bin/emacs"
elif [ -f /usr/bin/nvim ]; then
    export EDITOR="/usr/bin/nvim"
    export VISUAL="/usr/bin/nvim"
elif [ -f /usr/bin/vim ]; then
    export EDITOR="/usr/bin/vim"
    export VISUAL="/usr/bin/vim"
fi

# Load aliases
if [ -f ~/.aliases ]; then
    . ~/.aliases
fi

export CHROME_BIN=chromium

# Add pyenv to path
export PATH="$HOME/.pyenv/bin:$PATH"
eval "$(pyenv init -)"
eval "$(pyenv virtualenv-init -)"

# Add poetry to path
export PATH="$HOME/.poetry/bin:$PATH"

# Add golang to path
export GOROOT=/usr/lib/go
export GOPATH=$HOME/go
export PATH="$PATH:$GOROOT/bin:$GOPATH/bin"

# Fix term
export TERM=xterm-256color

CASE_SENSITIVE="false"

plugins=(
    git
    )

DISABLE_LS_COLORS="false"

# use starship if it's available
eval "$(starship init zsh)"
