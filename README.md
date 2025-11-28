# .dotfiles

Personal dotfiles for a macOS development environment, focused on productivity, writing, and research workflows. Made painless and idempotent with [dotbot](https://github.com/anishathalye/dotbot). Scripts lean heavily on [babashka](https://github.com/babashka/babashka) (Clojure scripting).

## Quick Start

### Fresh macOS Install (Recommended)

Run the bootstrap script to set up everything automatically:

```bash
curl -fsSL https://raw.githubusercontent.com/gcuth/.dotfiles/master/bootstrap | bash
```

This will:

1. Install Xcode Command Line Tools
2. Install Homebrew
3. Clone this repository
4. Run the dotfiles installer
5. Optionally configure macOS system preferences

### Manual Installation

```bash
# Clone the repository
git clone https://github.com/gcuth/.dotfiles.git ~/.dotfiles

# Run the installer
cd ~/.dotfiles
./install

# (Optional) Configure macOS system preferences
./macos/setup
```

### Verify Installation

```bash
dotfiles-health        # Run health check
dotfiles-health --fix  # Attempt to fix any issues
```

## What's Included

### Shell Configuration

| File            | Purpose                                     |
| --------------- | ------------------------------------------- |
| `zsh/zshrc`     | Main shell config (history, PATH, env vars) |
| `zsh/zprofile`  | Homebrew initialization                     |
| `aliases`       | 50+ shell aliases organized by category     |
| `starship.toml` | Fast, minimal prompt with git status        |

### Development Tools

Automatically installs via Homebrew:

- **Languages**: Python, Node (nvm), Ruby (rbenv), Go, Rust, Java, Clojure (babashka)
- **Editors**: Neovim, Zed
- **CLI Tools**: ripgrep, fzf, jq, tmux, pandoc
- **Databases**: PostgreSQL, SQLite

### Productivity Scripts

Located in `scripts/`, symlinked to `~/.local/bin/`:

| Script            | Purpose                                                    |
| ----------------- | ---------------------------------------------------------- |
| `logwords.bb`     | Track daily writing progress with EWMA and streak tracking |
| `block_social`    | Block distracting websites via `/etc/hosts`                |
| `track_notes.sh`  | Auto-backup Obsidian notes to local git repo               |
| `track_thesis.sh` | Sync thesis chapters to GitHub                             |
| `dotfiles-health` | Verify dotfiles setup is working                           |
| `dotfiles-diff`   | Audit symlinks and detect divergence from repo             |
| `backup.bb`       | Create GPG-encrypted tarball backups                       |
| `backup-status`   | Dashboard showing status of all backup systems             |
| `rotate-logs`     | Log rotation utility to manage disk space                  |
| `cron-notify`     | Wrapper for cron jobs with failure notifications           |

### Shared Libraries

| File                     | Purpose                                   |
| ------------------------ | ----------------------------------------- |
| `scripts/lib/logging.sh` | Unified logging library for shell scripts |

### Configuration Files

| File                        | Purpose                                          |
| --------------------------- | ------------------------------------------------ |
| `config/productivity.json`  | Writing goals, focus settings, streak thresholds |
| `config/blocked-sites.json` | Categorized list of sites to block               |

### Automation (Cron Jobs)

Per-minute tasks:

- Log currently playing music (Spotify/Apple Music)
- Update menu bar with word count (One Thing app)

Per-5-minute tasks:

- Backup Obsidian notes to local git
- Sync thesis chapters to GitHub
- Run iOS Shortcuts automation

Hourly/Daily tasks:

- iOS Shortcuts automations
- Log file rotation (3 AM daily)

## Usage Examples

### Distraction Blocking

```bash
sudo block_social            # Enable blocking
sudo block_social --unblock  # Disable blocking
block_social --list          # Show blocked sites
block_social --status        # Check if blocking is active
```

Configure blocked sites in `config/blocked-sites.json`.

### Writing Productivity

```bash
# Track word count in a directory
logwords.bb --dir ~/Documents/Writing --log ~/Logs/words.csv

# View report of daily progress
logwords.bb --dir ~/Documents/Writing --log ~/Logs/words.csv --report

# Check writing streak
logwords.bb --dir ~/Documents/Writing --log ~/Logs/words.csv --streak
```

Configure goals in `config/productivity.json`.

### Backup Status

```bash
backup-status           # Full dashboard of all backup systems
backup-status --quick   # Quick check (exit code only)
backup-status --json    # Output as JSON
```

### System Maintenance

```bash
dotfiles-health         # Check all systems
dotfiles-health --quick # Skip slow checks
dotfiles-diff           # Audit symlinks
dotfiles-diff --fix     # Fix symlink issues
rotate-logs --status    # Check log file sizes
rotate-logs             # Rotate logs over 10MB
brewup                  # Update Homebrew packages
reload                  # Reload shell configuration
```

### Git Shortcuts

The git config includes 40+ aliases:

```bash
git s         # Short status
git lg        # Pretty log graph
git a         # Add all files
git cm "msg"  # Commit with message
git amend     # Amend last commit
git undo      # Undo last commit (keep changes)
```

## Customization

### Local Overrides

Add machine-specific configuration without modifying tracked files:

- `~/.zshrc.local` - Shell config overrides
- `~/.aliases.local` - Additional aliases

These files are automatically sourced if they exist.

### Configuration Files

| File                        | What to Customize                      |
| --------------------------- | -------------------------------------- |
| `install.conf.yaml`         | Homebrew packages, symlinks, cron jobs |
| `config/productivity.json`  | Writing goals, focus session length    |
| `config/blocked-sites.json` | Sites to block (by category)           |
| `starship.toml`             | Prompt appearance                      |
| `git/config`                | Git aliases and settings               |
| `macos/setup`               | macOS system preferences               |
| `snippets/espanso/`         | Text expansion snippets                |

## Directory Structure

```
~/.dotfiles/
├── install                # Main installer (runs dotbot)
├── install.conf.yaml      # Dotbot configuration
├── bootstrap              # Fresh macOS setup script
├── macos/setup            # macOS preferences
│
├── zsh/
│   ├── zshrc              # Main shell config
│   └── zprofile           # Homebrew init
├── aliases                # Shell aliases
├── profile                # Cargo/Rust PATH
├── starship.toml          # Prompt config
│
├── git/
│   ├── config             # Git settings (40+ aliases)
│   ├── ignore             # Global gitignore
│   └── hooks/pre-commit   # Syntax validation hook
│
├── config/
│   ├── productivity.json  # Writing goals & focus settings
│   └── blocked-sites.json # Site blocking categories
│
├── scripts/               # Symlinked to ~/.local/bin/
│   ├── lib/logging.sh     # Shared logging library (used by the shell scripts)
│   ├── *.bb               # Babashka (Clojure) scripts
│   └── *.sh               # Shell scripts
│
├── zed/
│   ├── settings.json      # Editor settings (vim mode)
│   └── keymap.json        # Custom keybindings
│
├── applescripts/          # macOS automation
│   └── tunes.js           # Music logging
│
├── snippets/espanso/      # Text expansion
│   ├── config.yml
│   └── matches/
│       ├── base.yml       # Date/time, personal info
│       └── pretty.yml     # Typography improvements
│
├── R/RProfile             # R console config
├── lein/profiles.clj      # Clojure/Leiningen config
└── stack/config.yaml      # Haskell config
```

## Dependencies

**Required:**

- macOS
- Xcode Command Line Tools
- Homebrew

**Installed automatically:**

- git, babashka, gpg, starship, neovim
- Python, Node.js, Ruby, and other languages
- See `install.conf.yaml` for full list

## Troubleshooting

### Health Check Fails

```bash
dotfiles-health --fix  # Re-runs ./install
```

### Symlinks Broken or Diverged

```bash
dotfiles-diff          # Audit all symlinks
dotfiles-diff --fix    # Re-creates symlinks
```

### Shell Changes Not Taking Effect

```bash
source ~/.zshrc  # Or restart terminal
```

### Cron Jobs Not Running

```bash
crontab -l           # Check if crontab is installed
./install            # Re-installs crontab
cron-notify --status # Check for recent failures
```

### Log Files Getting Large

```bash
rotate-logs --status  # Check log sizes
rotate-logs           # Rotate logs over 10MB
rotate-logs --force   # Rotate all logs
```
