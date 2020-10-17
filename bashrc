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

# Load aliases
if [ -f ~/.aliases ]; then
    . ~/.aliases
fi

CASE_SENSITIVE="false"

eval "$(starship init bash)"

export PATH="/home/g/.pyenv/bin:$PATH"
eval "$(pyenv init -)"
eval "$(pyenv virtualenv-init -)"

# [[ -s "/etc/profile.d/grc.bashrc" ]] && source /etc/profile.d/grc.bashrc
