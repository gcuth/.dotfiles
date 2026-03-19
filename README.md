# .dotfiles

dotfiles made painless and idempotent with [dotbot](https://github.com/anishathalye/dotbot).

## Quick Start

**Fresh macOS machine** (run from any terminal):

```bash
curl -fsSL https://raw.githubusercontent.com/gcuth/.dotfiles/master/bootstrap | bash
```

This installs Xcode CLI Tools, Homebrew, clones the repo to `~/.dotfiles`, runs the full installer, and optionally configures macOS system preferences.

**Already cloned** (re-run anytime — it's idempotent):

```bash
cd ~/.dotfiles && ./install
```

## What's Included

| Category | Details |
|----------|---------|
| **Shell** | Zsh + [Starship](https://starship.rs/) prompt, aliases, completions |
| **Git** | Global config, aliases, GPG signing, pre-commit hooks |
| **Editors** | Neovim, [Zed](https://zed.dev/) (settings + keymap) |
| **CLI tools** | ripgrep, fzf, jq, tmux, gh, pandoc, and more via Homebrew |
| **Languages** | Python, Node (nvm), Ruby (rbenv), Rust, Java, Clojure |
| **Apps** | iTerm2, Docker, Obsidian, Signal, 1Password, and others via Homebrew Cask |
| **Fonts** | Fira Code, Hack Nerd Font, B612 |
| **Snippets** | [Espanso](https://espanso.org/) text expansion (dates, email, name) |
| **Cron jobs** | Music logging, writing progress tracking, log rotation |
| **Scripts** | `dotfiles-health`, `dotfiles-diff`, `backup-status`, `rotate-logs`, and more in `~/.local/bin/` |

## Repository Structure

```
.dotfiles/
├── bootstrap          # Fresh machine setup (curl-friendly)
├── install            # Main installer (invokes Dotbot)
├── install.conf.yaml  # Dotbot config: symlinks, packages, cron jobs
├── aliases            # Shell aliases (~230 lines, 7 categories)
├── profile            # Login shell profile
├── starship.toml      # Starship prompt config
├── zsh/               # zshrc, zprofile, completions
├── git/               # gitconfig, gitignore, hooks
├── zed/               # Zed editor settings + keymap
├── scripts/           # Utility scripts (symlinked to ~/.local/bin/)
├── snippets/          # Espanso text expansion configs
├── macos/             # macOS system preferences script
├── applescripts/      # AppleScript/JXA automation
├── dotbot/            # Dotbot framework (submodule)
├── dotbot-brew/       # Homebrew plugin (submodule)
└── crontab-dotbot/    # Cron plugin (submodule)
```

## Useful Commands

```bash
dotfiles-health          # Verify setup: commands, symlinks, cron, git
dotfiles-diff            # Check for drift between repo and live config
brewup                   # Update + upgrade + cleanup Homebrew
```

## How It Works

The `install` script runs [Dotbot](https://github.com/anishathalye/dotbot) with two plugins ([dotbot-brew](https://github.com/wren/dotbot-brew), [crontab-dotbot](https://github.com/fundor333/crontab-dotbot)). All configuration lives in `install.conf.yaml`, which:

1. Creates directories (`~/.local/bin/`, `~/Developer/`, `~/Logs/`, etc.)
2. Symlinks all config files to their expected locations
3. Installs Homebrew packages and casks
4. Registers cron jobs

Everything is symlinked, so this repo is the single source of truth — edit here, not in `~/`.

