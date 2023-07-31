. "$HOME/.cargo/env"


# set R path for RStudio
# (to ensure correct version if R was installed via homebrew)
if [ -f /opt/homebrew/bin/R ]; then
    export RSTUDIO_WHICH_R=/opt/homebrew/bin/R
fi