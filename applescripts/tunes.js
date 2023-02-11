// a script for logging the now-playing track from Spotify or Apple Music

let output = "";

function addZero(i) {
  if (i < 10) {i = "0" + i}
  return i;
}

function formatOffset(offset) {
    // format a timezone offset in the familiar +HH:MM format
    let sign = "-";
    if (offset < 0) {
        sign = "+";
        offset = -offset;
    }
    let hours = Math.floor(offset / 60);
    let minutes = offset % 60;
    return sign + addZero(hours) + ":" + addZero(minutes);
}


// datetime stamp (in form YYYY-MM-DD HH:MM:SS)
const now = new Date()
const datetime = now.getFullYear() + "-" + addZero(now.getMonth() + 1) + "-" + addZero(now.getDate()) + " " + addZero(now.getHours()) + ":" + addZero(now.getMinutes()) + ":" + addZero(now.getSeconds()) + " " + formatOffset(now.getTimezoneOffset());

if (Application("Music").running()) {
    try {
        const track = Application("Music").currentTrack;
        const artist = track.artist();
        const title = track.name();
        const album = track.album();
        const source = "Apple Music";
        // duration comes in seconds automatically
        const length = track.duration();
        const genre = track.genre();
        output = `"${datetime}", "${source}", "${title}", "${artist}", "${album}", ${length}, "${genre}"`;
    }
    catch {
        output = null;
    }
} else if (Application("Spotify").running()) {
    try {
        const track = Application("Spotify").currentTrack;
        const artist = track.artist();
        const title = track.name();
        const album = track.album();
        const source = "Spotify";
        // get duration in seconds
        const length = track.duration() / 1000;
        const genre = "";
        output = `"${datetime}", "${source}", "${title}", "${artist}", "${album}", ${length}, "${genre}"`;
    }
    catch {
        output = null;
    }
} else {
    output = null;
}

// if output length is >0, show it!
if (output) {
    output;
}
