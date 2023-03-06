#!/usr/bin/env sh
# Use Steven Black's hosts to block adware/malware/social.

curl -s "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social/hosts" > /etc/hosts

echo "0.0.0.0 youtube.com" >> /etc/hosts
echo "0.0.0.0 www.youtube.com" >> /etc/hosts
echo "0.0.0.0 netflix.com" >> /etc/hosts
echo "0.0.0.0 www.netflix.com" >> /etc/hosts
echo "0.0.0.0 primevideo.com" >> /etc/hosts
echo "0.0.0.0 www.primevideo.com" >> /etc/hosts
echo "0.0.0.0 abc.net.au" >> /etc/hosts
echo "0.0.0.0 www.abc.net.au" >> /etc/hosts
echo "0.0.0.0 theguardian.com" >> /etc/hosts
echo "0.0.0.0 www.theguardian.com" >> /etc/hosts
echo "0.0.0.0 stan.com.au" >> /etc/hosts
echo "0.0.0.0 www.stan.com.au" >> /etc/hosts