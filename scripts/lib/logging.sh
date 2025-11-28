#!/usr/bin/env bash
# =============================================================================
# LOGGING LIBRARY - Unified logging for dotfiles scripts
# =============================================================================
# A reusable logging library that provides consistent logging across all
# shell scripts in the dotfiles repository.
#
# Usage:
#   source "${DOTFILES_DIR:-$HOME/.dotfiles}/scripts/lib/logging.sh"
#   log_init "my-script" "$HOME/Logs/my-script.log"
#   log_info "Starting process..."
#   log_warn "Something might be wrong"
#   log_error "Something failed!"
#
# Features:
#   - Consistent timestamp format
#   - Log levels (DEBUG, INFO, WARN, ERROR)
#   - Automatic log directory creation
#   - Optional stderr output for errors
#   - Log rotation support
#
# =============================================================================

# Prevent double-sourcing
[[ -n "${_LOGGING_LIB_LOADED:-}" ]] && return 0
readonly _LOGGING_LIB_LOADED=1

# -----------------------------------------------------------------------------
# Configuration (can be overridden before sourcing)
# -----------------------------------------------------------------------------

# Default log directory
LOG_DIR="${LOG_DIR:-$HOME/Logs}"

# Default log file (set via log_init)
LOG_FILE="${LOG_FILE:-}"

# Script name for log messages (set via log_init)
LOG_SCRIPT_NAME="${LOG_SCRIPT_NAME:-unknown}"

# Log level threshold (DEBUG=0, INFO=1, WARN=2, ERROR=3)
LOG_LEVEL="${LOG_LEVEL:-1}"

# Whether to also output errors to stderr
LOG_STDERR_ERRORS="${LOG_STDERR_ERRORS:-true}"

# Maximum log file size before rotation (10MB default)
LOG_MAX_SIZE="${LOG_MAX_SIZE:-10485760}"

# Number of rotated logs to keep
LOG_KEEP_COUNT="${LOG_KEEP_COUNT:-5}"

# -----------------------------------------------------------------------------
# Internal functions
# -----------------------------------------------------------------------------

_log_level_to_int() {
    case "$1" in
        DEBUG) echo 0 ;;
        INFO)  echo 1 ;;
        WARN)  echo 2 ;;
        ERROR) echo 3 ;;
        *)     echo 1 ;;
    esac
}

_log_timestamp() {
    date '+%Y-%m-%d %H:%M:%S'
}

_log_ensure_dir() {
    local dir="$1"
    if [[ ! -d "$dir" ]]; then
        mkdir -p "$dir" 2>/dev/null || {
            echo "ERROR: Cannot create log directory: $dir" >&2
            return 1
        }
    fi
}

_log_rotate_if_needed() {
    local log_file="$1"

    # Skip if file doesn't exist or LOG_MAX_SIZE is 0 (disabled)
    [[ ! -f "$log_file" ]] && return 0
    [[ "$LOG_MAX_SIZE" -eq 0 ]] && return 0

    local size
    size=$(stat -f%z "$log_file" 2>/dev/null || stat --printf="%s" "$log_file" 2>/dev/null || echo 0)

    if [[ "$size" -gt "$LOG_MAX_SIZE" ]]; then
        _log_rotate "$log_file"
    fi
}

_log_rotate() {
    local log_file="$1"
    local i

    # Remove oldest log if it exists
    [[ -f "${log_file}.${LOG_KEEP_COUNT}" ]] && rm -f "${log_file}.${LOG_KEEP_COUNT}"

    # Rotate existing logs
    for ((i = LOG_KEEP_COUNT - 1; i >= 1; i--)); do
        [[ -f "${log_file}.${i}" ]] && mv "${log_file}.${i}" "${log_file}.$((i + 1))"
    done

    # Rotate current log
    [[ -f "$log_file" ]] && mv "$log_file" "${log_file}.1"
}

# -----------------------------------------------------------------------------
# Public API
# -----------------------------------------------------------------------------

# Initialize logging for a script
# Usage: log_init "script-name" "/path/to/log/file.log"
log_init() {
    local script_name="${1:-$(basename "$0")}"
    local log_file="${2:-$LOG_DIR/${script_name}.log}"

    LOG_SCRIPT_NAME="$script_name"
    LOG_FILE="$log_file"

    # Ensure log directory exists
    _log_ensure_dir "$(dirname "$LOG_FILE")"

    # Rotate if needed
    _log_rotate_if_needed "$LOG_FILE"
}

# Main logging function
# Usage: log_message "LEVEL" "message"
log_message() {
    local level="${1:-INFO}"
    local message="$2"
    local timestamp
    timestamp="$(_log_timestamp)"

    # Check if we should log this level
    local level_int
    level_int=$(_log_level_to_int "$level")
    [[ "$level_int" -lt "$LOG_LEVEL" ]] && return 0

    # Format: [timestamp] [script] [LEVEL] message
    local log_line="[$timestamp] [$LOG_SCRIPT_NAME] [$level] $message"

    # Write to log file if configured
    if [[ -n "$LOG_FILE" ]]; then
        echo "$log_line" >> "$LOG_FILE"
    fi

    # Also output errors to stderr if configured
    if [[ "$LOG_STDERR_ERRORS" == "true" ]] && [[ "$level" == "ERROR" ]]; then
        echo "$log_line" >&2
    fi
}

# Convenience functions for each log level
log_debug() { log_message "DEBUG" "$1"; }
log_info()  { log_message "INFO"  "$1"; }
log_warn()  { log_message "WARN"  "$1"; }
log_error() { log_message "ERROR" "$1"; }

# Log script start (with separator for readability)
log_start() {
    local message="${1:-Starting}"
    log_info "=========================================="
    log_info "$message"
}

# Log script end
log_end() {
    local message="${1:-Completed}"
    log_info "$message"
    log_info "=========================================="
}

# Log a command before running it (useful for debugging)
log_cmd() {
    log_debug "Running: $*"
    "$@"
}

# =============================================================================
# END OF LOGGING LIBRARY
# =============================================================================
