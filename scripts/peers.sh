#! /usr/bin/bash

gnome-terminal -- sh ../../scripts/peer.sh peer1 localhost 8100 -b &
sleep 2

max=10
for i in `seq 2 $max`
do
    gnome-terminal -- sh ../../scripts/peer.sh peer"$i" localhost 8100 &
    sleep 2
done
