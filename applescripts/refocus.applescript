(*
A script to refocus on the important things.

This involves five main steps:
1. forcefully closing a lot of always-distracting tabs & applications;
2. logging the currently active app;
3. gently closing a list of inactive-but-running applications on a schedule;
4. gently reopening some 'good' default applications on a schedule;
5. cleaning up the plist file after all other operations are complete.
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
    try
        -- First, delete the existing file if it exists
        do shell script "rm -f " & quoted form of POSIX path of plistPath
        
        -- Then create a new file and write to it
        set plistFile to open for access (plistPath as string) with write permission
        write (xmlHeader & xmlContent & xmlFooter) to plistFile
        close access plistFile
    on error
        -- Make sure we close the file even if there's an error
        try
            close access plistFile
        end try
    end try
end savePlist

-- Helper function to clean plist, keeping only most recent record per app
on cleanPlist()
    set plistContents to my getPlist()
    set cleanedContents to {}
    set processedApps to {}
    
    -- Go through list in reverse (most recent first)
    repeat with i from (count plistContents) to 1 by -1
        set plistItem to item i of plistContents
        
        -- Split into app name and timestamp
        set AppleScript's text item delimiters to {"|"}
        set itemParts to text items of plistItem
        set appName to text item 1 of itemParts
        set AppleScript's text item delimiters to {""}
        
        -- If we haven't seen this app yet, keep its record
        if processedApps does not contain appName then
            set end of cleanedContents to plistItem
            set end of processedApps to appName
        end if
    end repeat
    
    -- Save the cleaned list
    my savePlist(cleanedContents)
end cleanPlist

-- STEP 1: FORCEFUL! (ALWAYS CLOSE THESE STRAIGHT AWAY!) ----------------------
log "Starting Step 1: Forcefully closing distracting tabs and applications..."

-- Define lists of websites to close
set tabsToClose to {"youtube.com", "netflix.com", "vimeo.com", "iview.abc.net.au", "stan.com.au", "reddit.com", "instagram.com", "primevideo.com", "hulu.com", "twitch.tv", "x.com", "tiktok.com", "twitter.com", "facebook.com", "snapchat.com", "discord.com", "discord.gg", "discord.io", "discord.net", "discord.org"}
-- Define a list of applications to close
set appsToClose to {"TV", "VLC Media Player", "QuickTime Player", "News", "Spotify"}

-- First handle Safari tabs (if Safari is running)
tell application "System Events"
    if exists (process "Safari") then
        log "Found Safari running - checking tabs..."
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
                                    log "Closing distracting tab: " & tabURL
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
log "Checking for distracting applications to close..."
tell application "System Events"
    repeat with appName in appsToClose
        if exists (process appName) then
            log "Closing application: " & appName
            tell application appName to quit
        end if
    end repeat
end tell

-- STEP 2: LOG THE CURRENTLY ACTIVE APP ----------------------------
log "Starting Step 2: Logging currently active application..."

-- Get the name of the currently active application
tell application "System Events"
    set activeAppName to name of the first process whose frontmost is true
end tell

log "Currently active application: " & activeAppName

-- Update timestamp for currently active app
set currentTime to current date
set plistContents to my getPlist()
set plistContents to plistContents & {activeAppName & "|" & currentTime}
my savePlist(plistContents)


-- STEP 3: GENTLE! (CLOSE THESE IF THEY'VE BEEN INACTIVE FOR A WHILE) ----------------------
-- Note: This skips in the event that any one of the 'skipClosingWhenOpenApps' is open. ----
log "Starting Step 3: Checking for inactive applications..."
-- For each app & defined inactivity time (in minutes), check if it's been inactive for that
-- long. If so, quit it.

-- Define the inactivity times for each app
set distractingApps to {¬
    {name:"Safari", threshold:60}, ¬
    {name:"Mail", threshold:10}, ¬
    {name:"Messages", threshold:5}, ¬
    {name:"Signal", threshold:5}, ¬
    {name:"Discord", threshold:5}, ¬
    {name:"Calendar", threshold:10}, ¬
    {name:"Cursor", threshold:120}, ¬
    {name:"Google Chrome", threshold:60}, ¬
    {name:"Photos", threshold:120}}

-- Define the apps that, if open, should cause us to skip closing any of the distracting apps.
set skipClosingWhenOpenApps to {"Audio Hijack"}
set skipClosing to false -- will be set to true if any of the skipClosingWhenOpenApps are open

-- Check if any of the skipClosingWhenOpenApps are open
tell application "System Events"
    repeat with appName in skipClosingWhenOpenApps
        if exists (process appName) then
            log "Skipping closing of distracting apps because " & appName & " is open."
            set skipClosing to true
            exit repeat
        end if
    end repeat
end tell

-- Run through the distracting apps and check if they've been inactive for a while
-- unless we're skipping closing due to an open skipClosingWhenOpenApp.
if skipClosing is false then
    repeat with appRecord in distractingApps
        set currentApp to name of (contents of appRecord)
        set appThreshold to threshold of (contents of appRecord)
        if currentApp is not activeAppName and application currentApp is running then
            log "Checking inactivity for: " & currentApp
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
                    log "Closing " & currentApp & " due to inactivity (" & (inactiveDuration div 60) & " minutes)"
                    tell application currentApp to quit
                end if
            end if
        end if
    end repeat
end if

-- STEP 4: GENTLE! (OPEN THESE IF THEY'RE NOT RUNNING) ----------------------
log "Starting Step 4: Opening productive applications..."

-- Define the good apps to open
set goodApps to {"Obsidian", "OmniFocus", "Timery", "Zotero", "Anki", "Claude"}

-- Loop through the good apps and open them (in the background) if they're *not* already running
repeat with appName in goodApps
    if application appName is not running then
        log "Opening application: " & appName
        tell application appName to run
    end if
end repeat

log "Cleaning up plist file..."
-- Clean up the plist file after all operations are complete
my cleanPlist()

log "Refocus script completed successfully!"
