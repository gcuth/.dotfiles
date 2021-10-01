# HISTORY
# don't add duplicate lines (or lines beginning with spaces) into the history
HISTCONTROL=ignoreboth
# append to the history file instead of overwriting
shopt -s histappend
# set sensible lengths
HISTSIZE=5000
HISTFILESIZE=10000
# set a callable history
export PROMPT_COMMAND="history -a; history -c; history -r; $PROMPT_COMMAND"
# etc
HIST_STAMPS="yyyy-mm-dd"

# Set visual editor with a tonne of fallbacks
if [ -f /snap/bin/codium ]; then
    export VISUAL="/snap/bin/codium"
elif [ -f /snap/bin/code ]; then
    export VISUAL="/snap/bin/code"
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

# Add $HOME/.local/bin/ to path
export PATH="$PATH:$HOME/.local/bin"

# Fix term
export TERM=xterm-256color

CASE_SENSITIVE="false"

eval "$(starship init bash)"

# [[ -s "/etc/profile.d/grc.bashrc" ]] && source /etc/profile.d/grc.bashrc

