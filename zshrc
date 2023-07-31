#!/bin/zsh

###########
# HISTORY #
###########
setopt inc_append_history
HISTCONTROL=ignoreboth
HISTSIZE=5000
HISTFILESIZE=10000
export PROMPT_COMMAND="history -a; history -c; history -r; $PROMPT_COMMAND" # set a callable history
HIST_STAMPS="yyyy-mm-dd"




#######
# GPG #
#######
# (make gpg work for signing)
export GPG_TTY=$(tty)




##########
# EDITOR #
##########
if [ -f `which code` ]; then
    export VISUAL=`which code`
elif [ -f `which nvim` ]; then
    export VISUAL=`which nvim`
elif [ -f /usr/bin/vim ]; then
    export VISUAL="/usr/bin/vim"
fi

if [ -f `which nvim` ]; then
    export EDITOR=`which nvim`
elif [ -f /usr/bin/vim ]; then
    export EDITOR="/usr/bin/vim"
fi




###########
# ALIASES #
###########
if [ -f ~/.aliases ]; then
    . ~/.aliases
fi




########
# PATH #
########
# Golang Path:
export GOROOT=/usr/lib/go
export GOPATH=$HOME/go
export PATH="$PATH:$GOROOT/bin:$GOPATH/bin"
# make sure the homebrew openjdk is first in path:
export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
# Add $HOME/.local/bin/ to path
export PATH="$PATH:$HOME/.local/bin"


#############################
# GENERAL TERMINAL SETTINGS #
#############################
export TERM=xterm-256color

CASE_SENSITIVE="false"

plugins=(
    git
    )

DISABLE_LS_COLORS="false"




#######
# NVM #
#######
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"  # This loads nvm
[ -s "$NVM_DIR/bash_completion" ] && \. "$NVM_DIR/bash_completion"  # This loads nvm bash_completion




#################
# CUSTOM PROMPT #
#################
# use starship if it's available
eval "$(starship init zsh)"