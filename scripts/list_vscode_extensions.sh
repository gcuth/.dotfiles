#!/bin/bash

if command -v code >/dev/null 2>&1; then
    code --list-extensions > /Users/g/.dotfiles/vscode/extensions.txt
else
    echo "VSCode is not installed or not available in the PATH"
fi
