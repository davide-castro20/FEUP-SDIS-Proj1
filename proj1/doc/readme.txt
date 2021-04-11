# How to run

### Compiling the code

This script should be run in the proj1 directory:
scripts/compile.sh

It should be run anytime a change has been made to the source code.

All the following scripts should be run in the src/build directory:

### Setup folder structure
To setup the folder structure of a peer, run the following scripts:

../../scripts/setup.sh <peer_id>

This scripts will create the folder structure of a peer of id peer_id (this must be a integer).
This must be run before executing a peer or the program will not run correctly.

### Execute a peer

../../scripts/peer.sh <version> <peer_id> <svc_access_point> <mc_addr> <mc_port> <mdb_addr> <mdb_port> <mdr_addr> <mdr_port>

For example:
../../scripts/peer.sh 1.3 1 Peer1 230.0.0.0 8001 230.0.0.0 8002 230.0.0.0 8003
../../scripts/peer.sh 1.3 2 Peer2 230.0.0.0 8001 230.0.0.0 8002 230.0.0.0 8003
../../scripts/peer.sh 1.3 3 Peer3 230.0.0.0 8001 230.0.0.0 8002 230.0.0.0 8003


### Testing the service

../../scripts/test.sh <peer_ap> BACKUP|RESTORE|DELETE|RECLAIM|STATE [<opnd_1> [<optnd_2]]

For example:
../../scripts/test.sh Peer1 STATE

