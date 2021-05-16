#! /usr/bin/bash

# Script for running a peer
# To be run in the root of the build tree
# No jar files used
# Assumes that Peer is the main class
#  and that it belongs to the peer package
# Modify as appropriate, so that it can be run
#  from the root of the compiled tree

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <svc_access_point> <boot ip> <boot port> [-b]"
  exit 1
fi

# Assign input arguments to nicely named variables

sap=$1
ip=$2
port=$3
boot=$4

# Execute the program
# Should not need to change anything but the class and its package, unless you use any jar file

# echo "java peer.Peer ${ver} ${id} ${sap} ${mc_addr} ${mc_port} ${mdb_addr} ${mdb_port} ${mdr_addr} ${mdr_port}"

java -cp .:./../lib/log4j-api-2.14.1.jar:./../lib/log4j-core-2.14.1.jar peer.Peer ${sap} ${ip} ${port} ${boot}
