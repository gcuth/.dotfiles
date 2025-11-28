#!/usr/bin/env bash
# =============================================================================
# TRACK THESIS - PhD Thesis Chapter Sync Script
# =============================================================================
# Automatically syncs thesis chapters from Obsidian to a private GitHub repo.
#
# Purpose:
#   - Keep thesis chapters version-controlled on GitHub
#   - Sync from iCloud/Obsidian to git repository
#   - Track bibliography file alongside chapters
#
# Process:
#   1. Check for recent changes in thesis folder (5-minute window)
#   2. Clone the thesis repository to a temp directory
#   3. Rsync chapters and bibliography to the clone
#   4. Commit and push changes if any detected
#   5. Clean up temp directory
#
# Usage:
#   ./track_thesis.sh          # Run manually
#   cron: */5 * * * *          # Run every 5 minutes via cron
#
# Dependencies:
#   - git (with SSH access to GitHub configured)
#   - rsync
#
# Note: Requires SSH key configured for GitHub access
# =============================================================================

# -----------------------------------------------------------------------------
# STRICT MODE
# -----------------------------------------------------------------------------
set -euo pipefail

# -----------------------------------------------------------------------------
# CONFIGURATION
# -----------------------------------------------------------------------------
# Paths can be overridden via environment variables

readonly OBSIDIAN_PATH="${THESIS_PATH:-$HOME/Library/Mobile Documents/iCloud~md~obsidian/Documents/Notes/Thesis}"
readonly BIB_PATH="${BIB_PATH:-$HOME/Library/Mobile Documents/iCloud~is~workflow~my~workflows/Documents/Productivity/Thesis/phd.bib}"
readonly REPO_URL="${REPO_URL:-git@github.com:gcuth/phd_thesis.git}"
readonly TEMP_DIR="/tmp/phd_thesis_temp_$$"  # Use PID for unique temp dir
readonly CHAPTERS_DIR="chapters"
readonly CITATIONS_DIR="citations"
readonly TARGET_BRANCH="master"
readonly LOG_PATH="${LOG_PATH:-$HOME/Logs/track_thesis.log}"
readonly LOCK_FILE="/tmp/track_thesis.lock"
readonly CHANGE_WINDOW_MINUTES=5
readonly MAX_RETRIES=3
readonly RETRY_DELAY=5

# -----------------------------------------------------------------------------
# LOGGING
# -----------------------------------------------------------------------------
# Use shared logging library for consistent logging across scripts

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/logging.sh
source "${SCRIPT_DIR}/lib/logging.sh"

# Initialize logging with script name and log file
log_init "track_thesis" "$LOG_PATH"

# -----------------------------------------------------------------------------
# CLEANUP TRAP
# -----------------------------------------------------------------------------
# Ensure temp directory and lock file are removed on exit

cleanup() {
    local exit_code=$?

    # Remove temp directory
    if [[ -d "$TEMP_DIR" ]]; then
        rm -rf "$TEMP_DIR"
        log_info "Cleaned up temporary directory"
    fi

    # Remove lock file if we own it
    if [[ -f "$LOCK_FILE" ]] && [[ "$(cat "$LOCK_FILE" 2>/dev/null)" == "$$" ]]; then
        rm -f "$LOCK_FILE"
    fi

    if [[ $exit_code -ne 0 ]]; then
        log_error "Script exited with code $exit_code"
    fi

    exit $exit_code
}

trap cleanup EXIT INT TERM

# -----------------------------------------------------------------------------
# LOCK FILE MANAGEMENT
# -----------------------------------------------------------------------------

acquire_lock() {
    # Check for stale lock
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

    echo "$$" > "$LOCK_FILE"
}

# -----------------------------------------------------------------------------
# VALIDATION
# -----------------------------------------------------------------------------

check_dependencies() {
    local missing=()

    for cmd in git rsync ssh; do
        if ! command -v "$cmd" &>/dev/null; then
            missing+=("$cmd")
        fi
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        log_error "Missing required commands: ${missing[*]}"
        exit 1
    fi
}

check_ssh_access() {
    # Quick check if SSH to GitHub is likely to work
    # Use timeout to avoid hanging if network is down
    if ! timeout 10 ssh -T git@github.com 2>&1 | grep -q "successfully authenticated"; then
        log_warn "SSH authentication to GitHub may not be configured"
        # Don't exit - let git clone fail with a better error message
    fi
}

check_source_path() {
    if [[ ! -d "$OBSIDIAN_PATH" ]]; then
        log_error "Thesis folder not found at: $OBSIDIAN_PATH"
        log_error "Check iCloud sync status or update THESIS_PATH"
        exit 1
    fi
}

check_recent_changes() {
    local changed
    changed="$(find "$OBSIDIAN_PATH" -type f -mmin -"$CHANGE_WINDOW_MINUTES" -print -quit 2>/dev/null || true)"
    [[ -n "$changed" ]]
}

# -----------------------------------------------------------------------------
# GIT OPERATIONS WITH RETRY
# -----------------------------------------------------------------------------

clone_with_retry() {
    local attempt=1

    while [[ $attempt -le $MAX_RETRIES ]]; do
        log_info "Cloning repository (attempt $attempt/$MAX_RETRIES)..."

        # Use shallow clone for faster operation
        if git clone --depth 1 --branch "$TARGET_BRANCH" "$REPO_URL" "$TEMP_DIR" 2>&1; then
            log_info "Repository cloned successfully"
            return 0
        fi

        log_warn "Clone attempt $attempt failed"

        # Clean up failed clone attempt
        rm -rf "$TEMP_DIR"

        if [[ $attempt -lt $MAX_RETRIES ]]; then
            log_info "Retrying in $RETRY_DELAY seconds..."
            sleep "$RETRY_DELAY"
        fi

        ((attempt++))
    done

    log_error "Failed to clone repository after $MAX_RETRIES attempts"
    return 1
}

push_with_retry() {
    local attempt=1

    while [[ $attempt -le $MAX_RETRIES ]]; do
        log_info "Pushing changes (attempt $attempt/$MAX_RETRIES)..."

        if git -C "$TEMP_DIR" push origin "$TARGET_BRANCH" 2>&1; then
            log_info "Changes pushed successfully"
            return 0
        fi

        log_warn "Push attempt $attempt failed"

        if [[ $attempt -lt $MAX_RETRIES ]]; then
            log_info "Retrying in $RETRY_DELAY seconds..."
            sleep "$RETRY_DELAY"
        fi

        ((attempt++))
    done

    log_error "Failed to push changes after $MAX_RETRIES attempts"
    return 1
}

# -----------------------------------------------------------------------------
# SYNC OPERATIONS
# -----------------------------------------------------------------------------

sync_chapters() {
    local target_path="$TEMP_DIR/$CHAPTERS_DIR/"

    log_info "Syncing chapters to: $target_path"

    # Ensure target directory exists
    mkdir -p "$target_path"

    # Rsync chapters (delete files removed from source)
    if rsync -av --delete \
        --exclude='.DS_Store' \
        --exclude='._*' \
        --exclude='.obsidian' \
        "$OBSIDIAN_PATH/" "$target_path"; then
        log_info "Chapters synced successfully"
        return 0
    else
        log_error "Failed to sync chapters"
        return 1
    fi
}

sync_bibliography() {
    if [[ ! -f "$BIB_PATH" ]]; then
        log_warn "Bibliography file not found at: $BIB_PATH"
        log_warn "Continuing without bibliography sync"
        return 0
    fi

    local target_path="$TEMP_DIR/$CITATIONS_DIR/"

    log_info "Syncing bibliography to: $target_path"

    # Ensure target directory exists
    mkdir -p "$target_path"

    if rsync -av "$BIB_PATH" "$target_path"; then
        log_info "Bibliography synced successfully"
        return 0
    else
        log_warn "Failed to sync bibliography (non-fatal)"
        return 0  # Non-fatal error
    fi
}

commit_and_push() {
    # Check for changes
    if [[ -z "$(git -C "$TEMP_DIR" status --porcelain 2>/dev/null)" ]]; then
        log_info "No changes detected in repository"
        return 0
    fi

    log_info "Changes detected, committing..."

    local hostname_clean
    hostname_clean="$(hostname | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9-]/-/g')"
    local commit_msg="Auto-update chapters $(date '+%Y-%m-%d %H:%M:%S') from $hostname_clean"

    # Stage all changes
    if ! git -C "$TEMP_DIR" add --all; then
        log_error "Failed to stage changes"
        return 1
    fi

    # Commit
    if ! git -C "$TEMP_DIR" commit -m "$commit_msg"; then
        log_error "Failed to commit changes"
        return 1
    fi

    log_info "Changes committed: $commit_msg"

    # Push with retry
    if ! push_with_retry; then
        return 1
    fi

    return 0
}

# -----------------------------------------------------------------------------
# MAIN
# -----------------------------------------------------------------------------

main() {
    log_info "=== Starting thesis sync process ==="

    # Pre-flight checks
    check_dependencies
    acquire_lock
    check_source_path

    # Check for recent changes first (fast path for no changes)
    log_info "Checking for recent changes in thesis folder..."
    if ! check_recent_changes; then
        log_info "No recent changes detected (within $CHANGE_WINDOW_MINUTES minutes)"
        log_info "Skipping sync"
        exit 0
    fi

    log_info "Recent changes detected, proceeding with sync..."

    # Clone repository
    if ! clone_with_retry; then
        exit 1
    fi

    # Sync files
    if ! sync_chapters; then
        exit 1
    fi

    sync_bibliography  # Non-fatal if fails

    # Commit and push
    if ! commit_and_push; then
        exit 1
    fi

    log_info "=== Thesis sync process completed successfully ==="
}

# Run main function
main "$@"
