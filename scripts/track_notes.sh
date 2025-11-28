#!/usr/bin/env bash
# =============================================================================
# TRACK NOTES - Obsidian Vault Backup Script
# =============================================================================
# Automatically syncs/backs up Obsidian notes to a local git repository.
#
# Purpose:
#   - Track changes to notes with git for rollback and analysis
#   - Keep notes private (local git repo only, no remote push)
#   - Integrate with iCloud sync for cross-device access
#
# Process:
#   1. Check for recent changes in Obsidian vault (5-minute window)
#   2. Ensure snapshot directory exists with git repo initialized
#   3. Rsync vault contents to snapshot directory
#   4. Commit changes if any detected
#   5. (Optional) Create encrypted tarball backup
#
# Usage:
#   ./track_notes.sh           # Run manually
#   cron: */5 * * * *          # Run every 5 minutes via cron
#
# Dependencies:
#   - git, rsync
#   - gpg (optional, for encrypted backups)
# =============================================================================

# -----------------------------------------------------------------------------
# STRICT MODE
# -----------------------------------------------------------------------------
# Exit on error, undefined variables, and pipe failures
set -euo pipefail

# -----------------------------------------------------------------------------
# CONFIGURATION
# -----------------------------------------------------------------------------
# Paths can be overridden via environment variables

readonly OBSIDIAN_PATH="${OBSIDIAN_PATH:-$HOME/Library/Mobile Documents/iCloud~md~obsidian/Documents/Notes/}"
readonly SNAPSHOT_DIR="${SNAPSHOT_DIR:-$HOME/Snapshots/notes}"
readonly TARGET_DIR="obsidian"
readonly TARGET_BRANCH="main"
readonly LOG_PATH="${LOG_PATH:-$HOME/Logs/track_notes.log}"
readonly LOCK_FILE="/tmp/track_notes.lock"
readonly CHANGE_WINDOW_MINUTES=5

# Encrypted backup configuration (currently disabled)
readonly TARBALL_DIR="$HOME/Library/CloudStorage/ProtonDrive-g@galen.me-folder/Encrypted Backups"
readonly ENABLE_ENCRYPTED_BACKUP=false

# -----------------------------------------------------------------------------
# LOGGING
# -----------------------------------------------------------------------------
# Use shared logging library for consistent logging across scripts

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/logging.sh
source "${SCRIPT_DIR}/lib/logging.sh"

# Initialize logging with script name and log file
log_init "track_notes" "$LOG_PATH"

# -----------------------------------------------------------------------------
# CLEANUP TRAP
# -----------------------------------------------------------------------------
# Ensure lock file is removed on exit (success or failure)

cleanup() {
    local exit_code=$?

    # Remove lock file if we created it
    if [[ -f "$LOCK_FILE" ]] && [[ "$(cat "$LOCK_FILE" 2>/dev/null)" == "$$" ]]; then
        rm -f "$LOCK_FILE"
    fi

    if [[ $exit_code -ne 0 ]]; then
        log_error "Script exited with code $exit_code"
    fi

    exit $exit_code
}

trap cleanup EXIT

# -----------------------------------------------------------------------------
# LOCK FILE MANAGEMENT
# -----------------------------------------------------------------------------
# Prevent concurrent execution which could cause git conflicts

acquire_lock() {
    # Check for stale lock (process not running)
    if [[ -f "$LOCK_FILE" ]]; then
        local lock_pid
        lock_pid="$(cat "$LOCK_FILE" 2>/dev/null || echo "")"

        if [[ -n "$lock_pid" ]] && ! kill -0 "$lock_pid" 2>/dev/null; then
            log_warn "Removing stale lock file (PID $lock_pid not running)"
            rm -f "$LOCK_FILE"
        fi
    fi

    # Try to acquire lock
    if [[ -f "$LOCK_FILE" ]]; then
        log_info "Another instance is running (lock file exists). Exiting."
        exit 0
    fi

    # Create lock file with our PID
    echo "$$" > "$LOCK_FILE"
}

# -----------------------------------------------------------------------------
# VALIDATION FUNCTIONS
# -----------------------------------------------------------------------------

check_dependencies() {
    local missing=()

    for cmd in git rsync find; do
        if ! command -v "$cmd" &>/dev/null; then
            missing+=("$cmd")
        fi
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        log_error "Missing required commands: ${missing[*]}"
        exit 1
    fi
}

check_obsidian_path() {
    if [[ ! -d "$OBSIDIAN_PATH" ]]; then
        log_error "Obsidian vault not found at: $OBSIDIAN_PATH"
        log_error "Check iCloud sync status or update OBSIDIAN_PATH"
        exit 1
    fi
}

# Check if any files have been modified recently
check_recent_changes() {
    # Find files modified in the last N minutes
    # Use -print -quit for efficiency (stop at first match)
    local changed
    changed="$(find "$OBSIDIAN_PATH" -type f -mmin -"$CHANGE_WINDOW_MINUTES" -print -quit 2>/dev/null || true)"

    [[ -n "$changed" ]]
}

# -----------------------------------------------------------------------------
# GIT REPOSITORY MANAGEMENT
# -----------------------------------------------------------------------------

ensure_snapshot_repo() {
    # Create snapshot directory if needed
    if [[ ! -d "$SNAPSHOT_DIR" ]]; then
        log_info "Creating snapshot directory: $SNAPSHOT_DIR"
        mkdir -p "$SNAPSHOT_DIR"
    fi

    # Initialize git repo if needed
    if [[ ! -d "$SNAPSHOT_DIR/.git" ]]; then
        log_info "Initializing git repository in: $SNAPSHOT_DIR"
        git -C "$SNAPSHOT_DIR" init --initial-branch="$TARGET_BRANCH"

        # Configure git for this repo (no user interaction needed)
        git -C "$SNAPSHOT_DIR" config user.email "track_notes@localhost"
        git -C "$SNAPSHOT_DIR" config user.name "Track Notes Script"
    fi
}

# -----------------------------------------------------------------------------
# SYNC AND BACKUP
# -----------------------------------------------------------------------------

sync_files() {
    local target_path="$SNAPSHOT_DIR/$TARGET_DIR/"

    log_info "Syncing files to: $target_path"

    # Create target directory if needed
    mkdir -p "$target_path"

    # Rsync with options:
    #   -a: archive mode (preserves permissions, timestamps, etc.)
    #   -v: verbose
    #   --delete: remove files from target that don't exist in source
    #   --exclude: skip macOS metadata and temp files
    if rsync -a --delete \
        --exclude='.DS_Store' \
        --exclude='._*' \
        --exclude='.Trash*' \
        --exclude='*.tmp' \
        "$OBSIDIAN_PATH" "$target_path"; then
        log_info "Files synced successfully"
        return 0
    else
        log_error "rsync failed with exit code $?"
        return 1
    fi
}

commit_changes() {
    # Check if there are any changes to commit
    if [[ -z "$(git -C "$SNAPSHOT_DIR" status --porcelain 2>/dev/null)" ]]; then
        log_info "No changes detected in repository"
        return 0
    fi

    log_info "Changes detected, committing..."

    local hostname_clean
    hostname_clean="$(hostname | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9-]/-/g')"
    local commit_msg="Auto-update notes $(date '+%Y-%m-%d %H:%M:%S') from $hostname_clean"

    # Stage all changes
    git -C "$SNAPSHOT_DIR" add --all

    # Commit
    if git -C "$SNAPSHOT_DIR" commit -m "$commit_msg" --quiet; then
        log_info "Changes committed successfully"
        return 0
    else
        log_error "Failed to commit changes"
        return 1
    fi
}

# Verify backup integrity
verify_backup() {
    log_info "Verifying backup integrity..."

    local target_path="$SNAPSHOT_DIR/$TARGET_DIR/"
    local errors=0

    # Check 1: Verify git repository is healthy
    if ! git -C "$SNAPSHOT_DIR" fsck --quiet 2>/dev/null; then
        log_error "Git repository integrity check failed"
        ((errors++))
    else
        log_info "Git repository integrity: OK"
    fi

    # Check 2: Verify latest commit exists
    local latest_commit
    latest_commit=$(git -C "$SNAPSHOT_DIR" rev-parse HEAD 2>/dev/null || echo "")
    if [[ -z "$latest_commit" ]]; then
        log_error "No commits found in repository"
        ((errors++))
    else
        log_info "Latest commit: ${latest_commit:0:8}"
    fi

    # Check 3: Compare file counts between source and backup
    local source_count backup_count
    source_count=$(find "$OBSIDIAN_PATH" -type f \
        ! -name '.DS_Store' ! -name '._*' ! -name '*.tmp' \
        2>/dev/null | wc -l | tr -d ' ')
    backup_count=$(find "$target_path" -type f \
        ! -name '.DS_Store' ! -name '._*' ! -name '*.tmp' \
        2>/dev/null | wc -l | tr -d ' ')

    if [[ "$source_count" -ne "$backup_count" ]]; then
        log_warn "File count mismatch: source=$source_count, backup=$backup_count"
        # This is a warning, not an error - some files may be excluded
    else
        log_info "File count verified: $backup_count files"
    fi

    # Check 4: Verify backup is not empty
    if [[ "$backup_count" -eq 0 ]]; then
        log_error "Backup directory is empty"
        ((errors++))
    fi

    # Check 5: Verify recent backup timestamp
    local last_modified
    last_modified=$(find "$target_path" -type f -printf '%T@\n' 2>/dev/null | sort -n | tail -1 || \
                    stat -f '%m' "$target_path" 2>/dev/null || echo "0")
    local current_time
    current_time=$(date +%s)
    local age_minutes=$(( (current_time - ${last_modified%.*}) / 60 ))

    if [[ $age_minutes -gt 10 ]]; then
        log_warn "Backup may be stale: last modified $age_minutes minutes ago"
    else
        log_info "Backup freshness: OK (modified $age_minutes minutes ago)"
    fi

    # Summary
    if [[ $errors -gt 0 ]]; then
        log_error "Backup verification failed with $errors error(s)"
        return 1
    else
        log_info "Backup verification: PASSED"
        return 0
    fi
}

# Optional: Create encrypted tarball backup
create_encrypted_backup() {
    if [[ "$ENABLE_ENCRYPTED_BACKUP" != "true" ]]; then
        return 0
    fi

    if [[ ! -d "$TARBALL_DIR" ]]; then
        log_warn "Tarball output directory not found: $TARBALL_DIR"
        log_warn "Skipping encrypted backup"
        return 0
    fi

    if ! command -v gpg &>/dev/null; then
        log_warn "gpg not found, skipping encrypted backup"
        return 0
    fi

    local hostname_clean
    hostname_clean="$(hostname | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9-]/-/g')"
    local tarball_name="obsidian-notes-$(date '+%Y-%m-%d')-$hostname_clean.tar.gz.gpg"
    local tarball_path="$TARBALL_DIR/$tarball_name"

    log_info "Creating encrypted backup: $tarball_path"

    if tar -czf - -C "$SNAPSHOT_DIR" "$TARGET_DIR" | gpg -c --no-symkey-cache --output "$tarball_path"; then
        log_info "Encrypted backup created successfully"
        return 0
    else
        log_error "Failed to create encrypted backup"
        return 1
    fi
}

# -----------------------------------------------------------------------------
# MAIN
# -----------------------------------------------------------------------------

main() {
    log_info "=== Starting notes backup process ==="

    # Pre-flight checks
    check_dependencies
    acquire_lock
    check_obsidian_path

    # Check for recent changes
    log_info "Checking for recent changes in Obsidian vault..."
    if ! check_recent_changes; then
        log_info "No recent changes detected (within $CHANGE_WINDOW_MINUTES minutes)"
        log_info "Skipping backup"
        exit 0
    fi

    log_info "Recent changes detected, proceeding with backup..."

    # Ensure snapshot repo exists
    ensure_snapshot_repo

    # Sync files
    if ! sync_files; then
        log_error "Sync failed, aborting"
        exit 1
    fi

    # Commit changes
    if ! commit_changes; then
        log_error "Commit failed"
        exit 1
    fi

    # Verify backup integrity
    if ! verify_backup; then
        log_warn "Backup verification had issues, but continuing"
    fi

    # Optional encrypted backup
    create_encrypted_backup

    log_info "=== Notes backup process completed successfully ==="
}

# Run main function
main "$@"
