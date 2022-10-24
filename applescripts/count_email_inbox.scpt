if application "Mail" is running then
    tell application "Mail"
        set inboxes to first mailbox of every account whose name is "Inbox"
        set messageCount to 0
        repeat with i from 1 to number of items in inboxes
            set this_item to item i of inboxes
            if this_item is not missing value then
                set thisCount to (count of (messages of this_item))
                set messageCount to thisCount + messageCount
            end if
        end repeat
    end tell
    log messageCount
else
    log "Mail is not running!"
end if
