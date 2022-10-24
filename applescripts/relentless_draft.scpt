set cc to the clipboard as string

tell application "Mail"
    set newMessage to make new outgoing message with properties {subject:"", content:cc}
    tell newMessage
        set visible to true
        make new to recipient at end of to recipients with properties {name:"Relentless Angst", address:"relentlessangst@gmail.com"}
        make new to recipient at end of to recipients with properties {name:"Stella Child", address:"stella.child@protonmail.com"}
    end tell
    activate

end tell
