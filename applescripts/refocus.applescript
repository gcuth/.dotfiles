(*
A script to refocus on the important things.

This involves four main steps:
1. forcefully closing a lot of distracting tabs & applications;
2. logging the currently active app;
3. gently closing a list of inactive-but-running applications on a schedule;
4. gently reopening some 'good' default applications on a schedule.
*)

-- STEP 0: SETUP AND DEFINITIONS FOR HELPER FUNCTIONS -------------------------
property plistPath : ((path to home folder as text) & "Library:Application Support:RefocusAppTracker:lastactive.plist")

-- Helper function to get/create plist file
on getPlist()
    try
        -- Read the plist file using plutil to convert to XML
        set plistXML to do shell script "plutil -convert xml1 -o - " & quoted form of POSIX path of plistPath
        
        -- Parse the XML content (this is a simplified approach)
        set AppleScript's text item delimiters to "<string>"
        set xmlItems to text items of plistXML
        set AppleScript's text item delimiters to "</string>"
        
        -- Create a list from the parsed items
        set plistContents to {}
        repeat with i from 2 to count xmlItems
            set this_item to text item 1 of xmlItems's item i
            if this_item is not "" then
                set end of plistContents to this_item
            end if
        end repeat
        
        set AppleScript's text item delimiters to ""
        return plistContents
    on error
        -- Create directory if it doesn't exist
        do shell script "mkdir -p \"" & (POSIX path of ((path to home folder as text) & "Library:Application Support:RefocusAppTracker")) & "\""
        -- Create empty plist
        set plistContents to {}
        my savePlist(plistContents)
        return plistContents
    end try
end getPlist

-- Helper function to save plist
on savePlist(plistContents)
    -- Create the XML header and plist container
    set xmlHeader to "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">
<plist version=\"1.0\">
<array>"
    
    -- Convert list items to XML format
    set xmlContent to ""
    repeat with itemContent in plistContents
        set xmlContent to xmlContent & "
    <string>" & itemContent & "</string>"
    end repeat
    
    -- Close the XML
    set xmlFooter to "
</array>
</plist>"
    
    -- Write the complete XML to file
    set plistFile to open for access (plistPath as string) with write permission
    write (xmlHeader & xmlContent & xmlFooter) to plistFile starting at 0
    close access plistFile
end savePlist




-- STEP 1: FORCEFUL! (ALWAYS CLOSE THESE STRAIGHT AWAY!) ----------------------
-- Define lists of websites to close
set tabsToClose to {"youtube.com", "netflix.com", "vimeo.com", "iview.abc.net.au", "stan.com.au", "reddit.com", "instagram.com", "primevideo.com", "hulu.com", "twitch.tv", "x.com", "tiktok.com", "twitter.com", "facebook.com", "snapchat.com", "discord.com", "discord.gg", "discord.io", "discord.net", "discord.org"}
-- Define a list of applications to close
set appsToClose to {"TV", "VLC Media Player", "QuickTime Player", "News", "Spotify"}

-- First handle Safari tabs (if Safari is running)
tell application "System Events"
    if exists (process "Safari") then
        tell application "Safari"
            -- Get all windows
            repeat with thisWindow in windows
                try
                    -- Get all tabs in current window
                    set tabList to every tab of thisWindow
                    repeat with thisTab in tabList
                        try
                            -- Get the URL of current tab
                            set tabURL to URL of thisTab
                            -- Check if URL contains any of the specified domains
                            repeat with domainName in tabsToClose
                                if tabURL contains contents of domainName then
                                    -- Close the tab
                                    close thisTab
                                    exit repeat
                                end if
                            end repeat
                        on error
                            -- Skip this tab if there's an error accessing it
                        end try
                    end repeat
                on error
                    -- Skip this window if there's an error
                end try
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

-- STEP 2: LOG THE CURRENTLY ACTIVE APP ----------------------------
-- Get the name of the currently active application
tell application "System Events"
    set activeAppName to name of the first process whose frontmost is true
end tell

-- Update timestamp for currently active app
set currentTime to current date
set plistContents to my getPlist()
set plistContents to plistContents & {activeAppName & "|" & currentTime}
my savePlist(plistContents)


-- STEP 3: GENTLE! (CLOSE THESE IF THEY'VE BEEN INACTIVE FOR A WHILE) ----------------------
-- For each app & defined inactivity time (in minutes), check if it's been inactive for that
-- long. If so, quit it.
-- Define the inactivity times for each app
set distractingApps to {{name:"Safari", threshold:10}, {name:"Mail", threshold:10}, {name:"Messages", threshold:10}, {name:"Signal", threshold:10}, {name:"Discord", threshold:10}}

-- Run through the distracting apps and check if they've been inactive for a while
repeat with appRecord in distractingApps
    set currentApp to name of (contents of appRecord)
    set appThreshold to threshold of (contents of appRecord)
    
    if currentApp is not activeAppName and application currentApp is running then
        -- Get the inactivity threshold for this app (in minutes) & convert to seconds
        set appThreshold to appThreshold * 60
        -- Get the last active time for this app
        set lastActiveTime to missing value
        set plistContents to my getPlist()
        repeat with plistItem in plistContents
            if plistItem contains currentApp then
                set AppleScript's text item delimiters to {"|"}
                set lastActiveTime to date (text item 2 of plistItem)
                set AppleScript's text item delimiters to {""}
            end if
        end repeat
        -- Check if app has been inactive longer than threshold
        if lastActiveTime is not missing value then
            set inactiveDuration to (currentTime - lastActiveTime)
            if inactiveDuration > appThreshold then
                tell application currentApp to quit
            end if
        end if
    end if
end repeat


-- STEP 4: GENTLE! (OPEN THESE IF THEY'RE NOT RUNNING) ----------------------
-- Define the good apps to open
set goodApps to {"Obsidian", "OmniFocus", "Timery"}

-- Loop through the good apps and open them (in the background) if they're *not* already running
repeat with appName in goodApps
    if application appName is not running then
        tell application appName to run
    end if
end repeat
