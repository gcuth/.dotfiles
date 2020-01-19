#!/bin/zsh

if [ -f ~/.bashrc ]; then
   . ~/.bashrc
fi

plugins=(
    git
    )

DISABLE_LS_COLORS="false"

export TERM=xterm-256color
