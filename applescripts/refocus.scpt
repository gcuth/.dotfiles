#!/usr/bin/osascript
(*
A script to refocus on the important things.
ie, close a lot of distracting tabs & windows.
*)

-- Define lists of sites and apps to close
set tabsToClose to {"youtube.com", "netflix.com", "vimeo.com", "iview.abc.net.au", "stan.com.au", "reddit.com", "instagram.com", "primevideo.com", "hulu.com", "twitch.tv", "x.com"}
set appsToClose to {"TV", "VLC Media Player", "QuickTime Player", "News"}

-- First handle Safari tabs if Safari is running
tell application "System Events"
    if exists (process "Safari") then
        tell application "Safari"
            -- Get all windows
            repeat with currentWindow in windows
                -- Get all tabs in current window
                repeat with currentTab in tabs of currentWindow
                    -- Get the URL of current tab
                    set tabURL to URL of currentTab
                    -- Check if URL contains any of the specified domains
                    repeat with domainName in tabsToClose
                        if tabURL contains domainName then
                            -- Close the tab
                            close currentTab
                            exit repeat
                        end if
                    end repeat
                end repeat
            end repeat
        end tell
    end if
end tell

-- Handle closing specific applications
tell application "System Events"
    repeat with appName in appsToClose
        if exists (process appName) then
            tell application appName to quit
        end if
    end repeat
end tell
