#! /usr/bin/bash

# Placeholder for clean-up script
# To be executed in the root of the build tree
# Requires at most one argument: the peer id
# Cleans the directory tree for storing
#  both the chunks and the restored files of
#  either a single peer, in which case you may or not use the argument
#    or for all peers, in which case you

# Check number input arguments

if [ "$#" -eq 1 ]; then
  peer_id=$1
  rm -rf ./peer"${peer_id}"
else
  echo "Usage: $0 [<peer_id>]]"
  exit 1
fi

# Clean the directory tree for storing files
# For a crash course on shell commands check for example:
# Command line basi commands from GitLab Docs':	https://docs.gitlab.com/ee/gitlab-basics/command-line-commands.html
# For shell scripting try out the following tutorials of the Linux Documentation Project
# Bash Guide for Beginners: https://tldp.org/LDP/Bash-Beginners-Guide/html/index.html
# Advanced Bash Scripting: https://tldp.org/LDP/abs/html/
