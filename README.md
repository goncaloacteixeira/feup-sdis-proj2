# Readme - SDIS Project 2 - G27

| Name             | UP Code   |
| ---------------- | --------- |
| Gonçalo Teixeira | 201806562 |
| André Gomes      | 201806224 |
| Isla Cassamo     | 201808549 |



## Requirements

- min. JDK 11

- Linux Based System (to run the shell scripts)

  

## Compile under `src/`

```shell
> sh ../scripts/compile.sh
```

it will save the compiled classes, keystores and log4j2.xml (used on logger) to `src/build/`



## Execute Peer under `src/build/`

```shell
> sh ../../scripts/peer.sh <SAP> <BOOT_IP> <BOOT_PORT> [-b]
```

- SAP

  Service Access Point (used for RMI)

- BOOT_IP/BOOT_PORT

  Used to Join the Chord Network

- Flag -b

  used to signal if the peer is Boot. This flag will mean the peer will take the boot peer IP/port passed as argument as its own, making it the boot peer, this flag is used for convenience, as we would have to start a peer and check it’s IP/Port, and then start the other peers with it as reference for the Chord Network.



## Execute TestApp/Client under `src/build/`

```shell
> sh ../../scripts/test.sh <operation> [<operand_1> [<operand_2]]
```

- operation

  BACKUP | RESTORE | DELETE | RECLAIM | STATE | CHORD | LOOKUP as described on table 2.1 ([report](report.pdf)), these operations have arguments associated.

  

## Multiple Peers Execution

To start eight peers, with a 1 second delay between, run the following line with JDK11 under `src/build/`:

```shell
> sh ../../scripts/peers.sh
```



## Documentation

The Javadocs for this project can be found [here](https://skdgt.github.io/feup-sdis-docs/project2/).





---

