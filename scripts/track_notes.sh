#!/bin/bash
# A script for one way sync/backup of Obsidian notes to a git repository.
# This script is intended to be run as a cron job to automatically sync changes.
# It backs-up all notes, including all files in all subdirectories, to a git repo.

# Set variables
OBSIDIAN_PATH="$HOME/Library/Mobile Documents/iCloud~md~obsidian/Documents/Notes/"
REPO_URL="git@github.com:gcuth/notes.git"
TEMP_DIR="/tmp/notes"
TARGET_DIR="obsidian"
TARGET_BRANCH="main"

# Function to log messages with timestamps
log_message() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Function to check if any files have been modified in the last 5 minutes
# This is to avoid unnecessary syncs when no changes have been made
check_recent_changes() {
    find "$OBSIDIAN_PATH" -type f -mmin -5 | grep -q .
}

log_message "Starting one-way backup process for Obsidian notes"

# Only proceed if there are recent changes
if check_recent_changes; then
    log_message "Recent changes detected. Proceeding with backup to git repository"
    
    # Clone the repository
    if git clone "$REPO_URL" "$TEMP_DIR"; then
        log_message "Repository cloned successfully"
    else
        log_message "Error: Failed to clone repository"
        exit 1
    fi
    
    # Sync files
    if rsync -av --delete "$OBSIDIAN_PATH" "$TEMP_DIR/$TARGET_DIR/"; then
        log_message "Files synced successfully from Obsidian to git repo using rsync"
    else
        log_message "Error: Failed to sync files from Obsidian to git repo using rsync"
        rm -rf "$TEMP_DIR"
        exit 1
    fi

    # Check for changes
    if [[ -n "$(git -C "$TEMP_DIR" status --porcelain)" ]]; then
        log_message "Changes detected in repository"
        
        # Commit and push changes
        if git -C "$TEMP_DIR" add . && \
           git -C "$TEMP_DIR" commit -m "Auto-update notes $(date '+%Y-%m-%d %H:%M:%S')" && \
           git -C "$TEMP_DIR" push origin "$TARGET_BRANCH"; then
            log_message "Changes committed and pushed successfully"
        else
            log_message "Error: Failed to commit and push changes"
            rm -rf "$TEMP_DIR"
            exit 1
        fi
    else
        log_message "No changes detected in repository"
    fi
    
    # Clean up
    rm -rf "$TEMP_DIR"
    log_message "Temporary directory cleaned up"
else
    log_message "No recent changes detected. Skipping backup."
fi

log_message "Notes backup process completed"