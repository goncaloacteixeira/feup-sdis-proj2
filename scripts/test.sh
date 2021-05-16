#! /usr/bin/bash

# Script for running the test app
# To be run at the root of the compiled tree
# No jar files used
# Assumes that TestApp is the main class
#  and that it belongs to the test package
# Modify as appropriate, so that it can be run
#  from the root of the compiled tree

# Check number input arguments

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <peer_ap> BACKUP|RESTORE|DELETE|RECLAIM|STATE [<opnd_1> [<optnd_2]]"
  exit 1
fi

# Assign input arguments to nicely named variables

pap=$1
oper=$2

# Validate remaining arguments

case $oper in
BACKUP)
  if [ "$#" -ne 4 ]; then
    echo "Usage: $0 <peer_ap> BACKUP <filename> <rep degree>"
    exit 1
  fi
  opernd_1=$3
  rep_deg=$4
  ;;
RESTORE)
  if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <peer_app> RESTORE <filename>"
  fi
  opernd_1=$3
  rep_deg=""
  ;;
DELETE)
  if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <peer_app> DELETE <filename>"
    exit 1
  fi
  opernd_1=$3
  rep_deg=""
  ;;
RECLAIM)
  if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <peer_app> RECLAIM <max space>"
    exit 1
  fi
  opernd_1=$3
  rep_deg=""
  ;;
STATE)
  if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <peer_app> STATE"
    exit 1
  fi
  opernd_1=""
  rep_deg=""
  ;;
*)
  echo "Usage: $0 <peer_ap> BACKUP|RESTORE|DELETE|RECLAIM|STATE [<opnd_1> [<optnd_2]]"
  exit 1
  ;;
esac

# Execute the program
# Should not need to change anything but the class and its package, unless you use any jar file

# echo "java test.TestApp ${pap} ${oper} ${opernd_1} ${rep_deg}"

java client.TestApp ${pap} ${oper} ${opernd_1} ${rep_deg}
