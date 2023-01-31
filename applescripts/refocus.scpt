(*
A script to refocus on the important things.
ie, close a lot of distracting tabs & windows.
*)


-- a list of the prime candidates: youtube, reddit, etc.
set distractingUrls to {}
set end of distractingUrls to "youtube.com"
set end of distractingUrls to "reddit.com"
set end of distractingUrls to "facebook.com"
set end of distractingUrls to "twitter.com"
set end of distractingUrls to "instagram.com"
set end of distractingUrls to "tumblr.com"
set end of distractingUrls to "pinterest.com"
set end of distractingUrls to "netflix.com"
set end of distractingUrls to "primevideo.com"
set end of distractingUrls to "hulu.com"
set end of distractingUrls to "ebay.com"
set end of distractingUrls to "twitch.tv"
set end of distractingUrls to "kottke.org"
set end of distractingUrls to "piratebay"
set end of distractingUrls to "xkcd.com"
set end of distractingUrls to "videolan"
set end of distractingUrls to "stan.com.au"

if application "Safari" is running then
    tell application "Safari"
        repeat with w in windows
            repeat with t in tabs of w
                set tabUrl to URL of t
                repeat with d in distractingUrls
                    if tabUrl contains d then
                        close t
                    end if
                end repeat
            end repeat
        end repeat
    end tell
end if

if application "Chrome" is running then
    tell application "Google Chrome"
        repeat with w in windows
            repeat with t in tabs of w
                set tabUrl to URL of t
                repeat with d in distractingUrls
                    if tabUrl contains d then
                        close t
                    end if
                end repeat
            end repeat
        end repeat
    end tell
end if

-- close QuickTime Player if it's open
if application "QuickTime Player" is running then
    tell application "QuickTime Player"
        quit
    end tell
end if

-- close podcasts if it's open
if application "Podcasts" is running then
    tell application "Podcasts"
        quit
    end tell
end if

-- close News if it's open
if application "News" is running then
    tell application "News"
        quit
    end tell
end if
