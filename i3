# i3 config file (v4)
#
# Please see https://i3wm.org/docs/userguide.html for a complete reference!

set $mod Mod4

# swap caps lock and escape
exec_always --no-startup-id xmodmap -e "clear lock"
exec_always --no-startup-id xmodmap -e "keycode 9 = Caps_Lock NoSymbol Caps_Lock"
exec_always --no-startup-id xmodmap -e "keycode 66 = Escape NoSymbol Escape"

# hide all window borders by default
default_border none

# Font for window titles. Will also be used by the bar unless a different font
# is used in the bar {} block below.
# font pango:monospace 8

# This font is widely installed, provides lots of unicode glyphs, right-to-left
# text rendering and scalability on retina/hidpi displays (thanks to pango).
# font pango:DejaVu Sans Mono 8
font pango:Fira Code 12
# font xft:Inconsolata for Powerline Medium 18

# The combination of xss-lock, nm-applet and pactl is a popular choice, so
# they are included here as an example. Modify as you see fit.

# xss-lock grabs a logind suspend inhibit lock and will use i3lock to lock the
# screen before suspend. Use loginctl lock-session to lock your screen.
exec --no-startup-id xss-lock --transfer-sleep-lock -- i3lock --nofork

# NetworkManager is the most popular way to manage wireless networks on Linux,
# and nm-applet is a desktop environment-independent system tray GUI for it.
exec --no-startup-id nm-applet

# Give back control of the screen brightness via fn keys:
exec --no-startup-id /usr/bin/xfce4-power-manager

# DAEMONS
exec --no-startup-id /usr/bin/rescuetime
exec --no-startup-id /usr/bin/redshift

# BACKGROUND
exec --no-startup-id xsetroot -solid "#000000"

# STARTUP
exec --no-startup-id i3-msg 'workspace $ws1; exec /usr/bin/kitty --single-instance newsboat'

# Use pactl to adjust volume in PulseAudio.
set $refresh_i3status killall -SIGUSR1 i3status
bindsym XF86AudioRaiseVolume exec --no-startup-id pactl set-sink-volume @DEFAULT_SINK@ +10% && $refresh_i3status
bindsym XF86AudioLowerVolume exec --no-startup-id pactl set-sink-volume @DEFAULT_SINK@ -10% && $refresh_i3status
bindsym XF86AudioMute exec --no-startup-id pactl set-sink-mute @DEFAULT_SINK@ toggle && $refresh_i3status
bindsym XF86AudioMicMute exec --no-startup-id pactl set-source-mute @DEFAULT_SOURCE@ toggle && $refresh_i3status

# Use Mouse+$mod to drag floating windows to their wanted position
floating_modifier $mod

# start a terminal
bindsym $mod+Return exec /usr/bin/kitty -o allow_remote_control=yes --single-instance

# start a browser
bindsym $mod+Shift+Return exec /usr/bin/firefox

# kill focused window
bindsym $mod+Shift+q kill

# start dmenu (a program launcher)
bindsym $mod+space exec dmenu_run

# There also is the (new) i3-dmenu-desktop which only displays applications
# shipping a .desktop file. It is a wrapper around dmenu, so you need that
# installed.
# bindsym $mod+d exec --no-startup-id i3-dmenu-desktop

# change focus
bindsym $mod+h focus left
bindsym $mod+j focus down
bindsym $mod+k focus up
bindsym $mod+l focus right

# alternatively, you can use the cursor keys:
bindsym $mod+Left focus left
bindsym $mod+Down focus down
bindsym $mod+Up focus up
bindsym $mod+Right focus right

# move focused window
bindsym $mod+Shift+H move left
bindsym $mod+Shift+J move down
bindsym $mod+Shift+K move up
bindsym $mod+Shift+L move right

# alternatively, you can use the cursor keys:
bindsym $mod+Shift+Left move left
bindsym $mod+Shift+Down move down
bindsym $mod+Shift+Up move up
bindsym $mod+Shift+Right move right

# split in horizontal orientation
# bindsym $mod+d split h

# split in vertical orientation
# bindsym $mod+k split v

## Modify // Toggle Window Orientation // <> Backspace ##
set_from_resource $i3-wm.binding.orientation_toggle i3-wm.binding.orientation_toggle BackSpace
bindsym $mod+$i3-wm.binding.orientation_toggle split toggle

# enter fullscreen mode for the focused container
bindsym $mod+f fullscreen toggle

#interactive screenshot by pressing printscreen
bindsym Print exec gnome-screenshot -i 
#crop-area screenshot by pressing Mod + printscreen
bindsym $mod+Print exec gnome-screenshot -a

# change container layout (stacked, tabbed, toggle split)
# bindsym $mod+o layout stacking
bindsym $mod+comma layout tabbed
# bindsym $mod+period layout toggle split

# toggle tiling / floating
# bindsym $mod+Shift+space floating toggle

# change focus between tiling / floating windows
# bindsym $mod+space focus mode_toggle

# focus the parent container
bindsym $mod+a focus parent

# focus the child container
#bindsym $mod+d focus child

# Define names for default workspaces for which we configure key bindings later on.
# We use variables to avoid repeating the names in multiple places.
set $ws1 "1"
set $ws2 "2"
set $ws3 "3"
set $ws4 "4"
set $ws5 "5"
set $ws6 "6"
set $ws7 "7"
set $ws8 "8"
set $ws9 "9"
set $ws10 "10"

# switch to workspace
bindsym $mod+1 workspace number $ws1
bindsym $mod+2 workspace number $ws2
bindsym $mod+3 workspace number $ws3
bindsym $mod+4 workspace number $ws4
bindsym $mod+5 workspace number $ws5
bindsym $mod+6 workspace number $ws6
bindsym $mod+7 workspace number $ws7
bindsym $mod+8 workspace number $ws8
bindsym $mod+9 workspace number $ws9
bindsym $mod+0 workspace number $ws10

# move focused container to workspace
bindsym $mod+Shift+1 move container to workspace number $ws1
bindsym $mod+Shift+2 move container to workspace number $ws2
bindsym $mod+Shift+3 move container to workspace number $ws3
bindsym $mod+Shift+4 move container to workspace number $ws4
bindsym $mod+Shift+5 move container to workspace number $ws5
bindsym $mod+Shift+6 move container to workspace number $ws6
bindsym $mod+Shift+7 move container to workspace number $ws7
bindsym $mod+Shift+8 move container to workspace number $ws8
bindsym $mod+Shift+9 move container to workspace number $ws9
bindsym $mod+Shift+0 move container to workspace number $ws10

## Navigate to Next Workspace // <> Tab ##
set_from_resource $i3-wm.binding.ws_next i3-wm.binding.ws_next Tab
bindsym $mod+$i3-wm.binding.ws_next workspace next

## Navigate to Previous Workspace // <><Shift> Tab ##
set_from_resource $i3-wm.binding.ws_prev i3-wm.binding.ws_prev Shift+Tab
bindsym $mod+$i3-wm.binding.ws_prev workspace prev


# reload the configuration file
bindsym $mod+Shift+r reload
# restart i3 inplace (preserves your layout/session, can be used to upgrade i3)
bindsym $mod+Shift+p restart
# exit i3 (logs you out of your X session)
bindsym $mod+Shift+greater exec "i3-nagbar -t warning -m 'You pressed the exit shortcut. Do you really want to exit i3? This will end your X session.' -B 'Yes, exit i3' 'i3-msg exit'"

# resize window (you can also use the mouse for that)
mode "resize" {
        # These bindings trigger as soon as you enter the resize mode
        bindsym h resize shrink width 10 px or 10 ppt
        bindsym t resize grow height 10 px or 10 ppt
        bindsym n resize shrink height 10 px or 10 ppt
        bindsym s resize grow width 10 px or 10 ppt

        # same bindings, but for the arrow keys
        bindsym Left resize shrink width 10 px or 10 ppt
        bindsym Down resize grow height 10 px or 10 ppt
        bindsym Up resize shrink height 10 px or 10 ppt
        bindsym Right resize grow width 10 px or 10 ppt

        # back to normal: Enter or Escape or $mod+r
        bindsym Return mode "default"
        bindsym Escape mode "default"
        bindsym $mod+p mode "default"
}

bindsym $mod+r mode "resize"

# Start i3bar to display a workspace bar (plus the system information i3status
# finds out, if available)
bar {
        status_command SCRIPT_DIR=~/.config/i3blocks/blocklets i3blocks
        i3bar_command i3bar -t 0.1
        separator_symbol " | "
        position bottom
        tray_output none
        colors {
            background #000000
            statusline #ffffff
            separator #666666
            focused_workspace #000000 #000000 #ffffff
            active_workspace #000000 #000000 #ffffff
            inactive_workspace #000000 #000000 #666666
            urgent_workspace #000000 #000000 #ff0000
        }
}

# toggle i3 bar
bindsym $mod+b bar mode toggle
