#!/bin/bash

# Set variables
OBSIDIAN_PATH="$HOME/Library/Mobile Documents/iCloud~md~obsidian/Documents/Notes/Thesis"
BIB_PATH="$HOME/Library/Mobile Documents/iCloud~is~workflow~my~workflows/Documents/Productivity/Thesis/phd.bib"
REPO_URL="git@github.com:gcuth/phd_thesis.git"
TEMP_DIR="/tmp/phd_thesis_temp"
CHAPTERS_DIR="chapters"
CITATIONS_DIR="citations"
TARGET_BRANCH="master"
LOG_PATH="$HOME/Logs/track_thesis.log"

# Function to log messages with timestamps
log_message() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_PATH"
}

# Function to check if any chapter files have been modified in the last 5 minutes
check_recent_changes() {
    find "$OBSIDIAN_PATH" -type f -mmin -5 | grep -q .
}

log_message "Starting thesis sync process"

# Only proceed if there are recent changes
if check_recent_changes; then
    log_message "Recent changes detected. Proceeding with sync"
    
    # Clone the repository
    if git clone "$REPO_URL" "$TEMP_DIR"; then
        log_message "Repository cloned successfully"
    else
        log_message "Error: Failed to clone repository"
        exit 1
    fi
    
    # Sync files
    if rsync -av --delete "$OBSIDIAN_PATH/" "$TEMP_DIR/$CHAPTERS_DIR/"; then
        log_message "Files synced successfully"
    else
        log_message "Error: Failed to sync files"
        rm -rf "$TEMP_DIR"
        exit 1
    fi
    
    # Sync the citation file if it exists
    if [[ -f "$BIB_PATH" ]]; then
        if rsync -av "$BIB_PATH" "$TEMP_DIR/$CITATIONS_DIR/"; then
            log_message "Citation file synced successfully"
        else
            log_message "Error: Failed to sync citation file"
            # don't exit here, as the citation file is optional
        fi
    else
        log_message "Warning: Citation file not found at \"$BIB_PATH\""
    fi
    
    # Check for changes
    if [[ -n "$(git -C "$TEMP_DIR" status --porcelain)" ]]; then
        log_message "Changes detected in repository"
        
        # Commit and push changes
        if git -C "$TEMP_DIR" add . && \
           git -C "$TEMP_DIR" commit -m "Auto-update chapters $(date '+%Y-%m-%d %H:%M:%S')" && \
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
    log_message "No recent changes detected. Skipping sync"
fi

log_message "Thesis sync process completed"