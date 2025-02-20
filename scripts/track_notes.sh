#!/bin/bash
# A script to sync/backup/snapshot of Obsidian notes to a local git repository.
#
# This script is intended to be run as a cron job to automagically & securely
# track changes to the notes (for future rollback & analysis) without ever
# exposing the content of the notes themselves to a remote github repo etc.
# 
# I use iCloud to sync my notes between my phone and my macs, and I use this
# script to keep an immutable record of when changes were actually made.
#
# The basic process is as follows:
# 0. Check the obsidian vault for recent changes (using a 5 minute window)
#        (if no changes are detected, exit the script)
# 1. Check for the existence of a snapshot directory with a git repo in it
#        (if no such directory exists, create one!)
# 2. Copy the current contents of the Obsidian vault to the snapshot directory
#        (overwrite any existing files, and delete any now-deleted files!)
# 3. If copying indicates that there have been changes since the last time the
#    script was run, then commit the these changes in the repo.
#        (exit the script if there are no changes)
# 4. Encrypt a tarball of the snapshot directory and save it to the 


# Set variables
OBSIDIAN_PATH="$HOME/Library/Mobile Documents/iCloud~md~obsidian/Documents/Notes/"
SNAPSHOT_DIR="$HOME/Snapshots/notes" # path to the snapshot directory/repo
TARGET_DIR="obsidian" # the name of the directory *in* the snapshot repo
TARGET_BRANCH="main" # the branch to commit changes to
LOG_PATH="$HOME/Logs/track_notes.log" # path to the log file
TARBALL_DIR="$HOME/Library/CloudStorage/ProtonDrive-g@galen.me-folder/Encrypted Backups"
TARBALL_PATH="$TARBALL_DIR/obsidian-notes-$(date '+%Y-%m-%d')-$(hostname | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g').tar.gz.gpg" # path to the tarball

# Function to log messages with timestamps
log_message() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_PATH"
}

# Function to check if any files have been modified in the last 5 minutes
# This is to avoid unnecessary syncs when no changes have been made
check_recent_changes() {
    find "$OBSIDIAN_PATH" -type f -mmin -5 | grep -q .
}

# Function to check for the existence of a snapshot directory that's a git repo
check_for_snapshot_repo() {
    if [ ! -d "$SNAPSHOT_DIR" ]; then
        log_message "Snapshot directory \"$SNAPSHOT_DIR\" does not exist."
        log_message "Creating snapshot directory..."
        mkdir -p "$SNAPSHOT_DIR"
        git init "$SNAPSHOT_DIR"
    else
        log_message "Snapshot directory \"$SNAPSHOT_DIR\" already exists."
        log_message "Checking if it's a git repo..."
        if [ ! -d "$SNAPSHOT_DIR/.git" ]; then
            log_message "Snapshot directory is not a git repo."
            log_message "Initializing git repo..."
            git init "$SNAPSHOT_DIR"
        else
            log_message "Yes, it's a git repo."
        fi
    fi
    log_message "Snapshot directory \"$SNAPSHOT_DIR\" is ready."
}

# Function to check for the existence of a tarball output directory
# DO NOT create the directory if it doesn't exist!
check_for_tarball_output_dir() {
    if [ ! -d "$TARBALL_DIR" ]; then
        log_message "Tarball output directory \"$TARBALL_DIR\" does not exist."
        log_message "Please create the directory and try again."
        exit 1
    fi
}

# Only proceed if there are recent changes
log_message "Checking for recent changes in \"$OBSIDIAN_PATH\"..."
if check_recent_changes; then
    log_message "Recent changes detected in \"$OBSIDIAN_PATH\"."
    log_message "Proceeding to backup to git repository at \"$SNAPSHOT_DIR\"..."
else 
    log_message "No recent changes detected in \"$OBSIDIAN_PATH\"."
    log_message "Skipping backup."
    exit 0
fi

# Check for the existence of a snapshot directory that's a git repo
check_for_snapshot_repo

# Sync the files from the Obsidian vault to the snapshot directory
log_message "Syncing files from \"$OBSIDIAN_PATH\" to \"$SNAPSHOT_DIR/$TARGET_DIR/\"..."
if rsync -av --delete "$OBSIDIAN_PATH" "$SNAPSHOT_DIR/$TARGET_DIR/"; then
    log_message "Files synced successfully from Obsidian to git repo using rsync"
else
    log_message "Error: Failed to sync files from Obsidian to git repo using rsync"
    exit 1
fi

# Check for changes
if [[ -n "$(git -C "$SNAPSHOT_DIR" status --porcelain)" ]]; then
    log_message "Changes detected in repository"
    # Commit the changes
    if git -C "$SNAPSHOT_DIR" add . && git -C "$SNAPSHOT_DIR" commit -m "Auto-update notes $(date '+%Y-%m-%d %H:%M:%S') from $(hostname)"; then
        log_message "Changes committed successfully"
    else
        log_message "Error: Failed to commit changes"
        exit 1
    fi
else
    log_message "No changes detected in repository"
fi

# Encrypt the tarball of the snapshot directory and save it to the tarball output directory
# Check for the existence of the tarball output directory
# log_message "Checking for an accessible tarball output directory..."

# check_for_tarball_output_dir

# log_message "Attempting to create an encrypted tarball of snapshot..."

# tar -czvf - "$SNAPSHOT_DIR/$TARGET_DIR/" | gpg -c --no-symkey-cache --output "$TARBALL_PATH"

# if [ -f "$TARBALL_PATH" ]; then
#     log_message "Tarball created successfully"
# else
#     log_message "Error: Failed to create tarball"
#     exit 1
# fi

log_message "Notes backup process completed"