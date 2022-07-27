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


# SET A PLATFORM VARIABLE
case "$(uname -s)" in
    Linux*)     CURRENTPLATFORM=linux;;
    Darwin*)    CURRENTPLATFORM=macos;;
esac


# make gpg work for signing
export GPG_TTY=$(tty)

# Set visual editor with a tonne of fallbacks
if [ -f /snap/bin/codium ]; then
    export VISUAL="/snap/bin/codium"
elif [ -f /snap/bin/code ]; then
    export VISUAL="/snap/bin/code"
elif [ -f `which code` ]; then
    export VISUAL=`which code`
elif [ -f `which nvim` ]; then
    export VISUAL=`which nvim`
elif [ -f /usr/bin/vim ]; then
    export VISUAL="/usr/bin/vim"
fi

# set normal editor with fewer callbacks
if [ -f `which nvim` ]; then
    export EDITOR=`which nvim`
elif [ -f /usr/bin/vim ]; then
    export EDITOR="/usr/bin/vim"
fi

# Load aliases
if [ -f ~/.aliases ]; then
    . ~/.aliases
fi

export CHROME_BIN=chromium

# Add pyenv to path
# export PATH="$HOME/.pyenv/bin:$PATH"
# eval "$(pyenv init -)"
# eval "$(pyenv virtualenv-init -)"

# Add poetry to path
# export PATH="$HOME/.poetry/bin:$PATH"

# Add golang to path
export GOROOT=/usr/lib/go
export GOPATH=$HOME/go
export PATH="$PATH:$GOROOT/bin:$GOPATH/bin"

# Add $HOME/.local/bin/ to path
export PATH="$PATH:$HOME/.local/bin"

# make sure homebrew openjdk is first in path
# if [ `uname` == 'Darwin' ]; then
export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
# fi

# make sure julia is in the path:
export PATH="/Applications/Julia-1.7.app/Contents/Resources/julia/bin:$PATH"

# Fix term
export TERM=xterm-256color

CASE_SENSITIVE="false"

plugins=(
    git
    )

DISABLE_LS_COLORS="false"

# use starship if it's available
eval "$(starship init zsh)"

# todoist functions
source $(brew --prefix)/share/zsh/site-functions/_todoist_fzf
# PROG=todoist source "$GOPATH/src/github.com/urfave/cli/autocomplete/zsh_autocomplete"
