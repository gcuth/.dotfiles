# convert the body of the latest written post to html and dump it to a variable
do shell script "TMPDIR=$(mktemp -d); TMPFILE=$TMPDIR/angst.html; cat ~/Documents/blog/_posts/angst/`ls -Art ~/Documents/blog/_posts/angst/ | tail -n 1 | tn` | sed -n '/^$/,$p' | pandoc -f markdown -t html > $TMPFILE; open -a Safari $TMPFILE"
# do shell script "TMPDIR=$(mktemp -d); TMPFILE=$TMPDIR/angst.html; cat ~/Documents/blog/_posts/angst/`ls -Art ~/Documents/blog/_posts/angst/ | tail -n 1 | tn` | sed -n '/^$/,$p' | pandoc -s -f markdown -t html > $TMPFILE; open -a Safari $TMPFILE"

delay 1.5

tell application "Safari"
    activate
    tell application "System Events"
        keystroke "a" using {command down}
        keystroke "c" using {command down}
    end tell
end tell

# prepopulate fields in the message
tell application "Mail"
    set newMessage to make new outgoing message with properties {subject:""}
    tell newMessage
        set visible to true
        make new to recipient at end of to recipients with properties {name:"Relentless Angst", address:"relentlessangst@gmail.com"}
        make new cc recipient at end of cc recipients with properties {name:"Stella Child", address:"stella.child@protonmail.com"}
        set html content to clipboard
    end tell
    activate
end tell
