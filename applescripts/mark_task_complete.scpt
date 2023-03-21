on run argv
    set inputTitle to item 1 of argv
    tell application "OmniFocus"
        tell default document
            set matchingTasks to (flattened tasks where (effectively completed is false) and (effectively dropped is false) and (blocked is false) and (name contains inputTitle))
            try
                set taskToComplete to item 1 of matchingTasks
                tell taskToComplete
                    mark complete
                end tell
                return "Completed task: " & name of taskToComplete & "."
            on error
                return "No matching tasks found"
            end try
        end tell
    end tell
end run
