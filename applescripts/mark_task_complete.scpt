on run argv
    set inputTitle to item 1 of argv
    tell application "OmniFocus"
        tell default document
            set matchingTasks to (flattened tasks where (effectively completed is false) and (effectively dropped is false) and (blocked is false) and (name contains inputTitle))
            set taskCount to count of matchingTasks
            if taskCount > 0 then
                tell item 1 of matchingTasks
                    mark complete
                end tell
            end if
        end tell
    end tell
end run
