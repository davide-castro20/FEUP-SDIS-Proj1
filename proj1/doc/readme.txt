# How to run
```
On the proj1 directory:

scripts/compile.sh
cd src/build

../../scripts/setup.sh 1
../../scripts/setup.sh 2
../../scripts/setup.sh 3
...

../../scripts/peer.sh 1.3 1 Peer1 230.0.0.0 8001 230.0.0.0 8002 230.0.0.0 8003
../../scripts/peer.sh 1.3 2 Peer2 230.0.0.0 8001 230.0.0.0 8002 230.0.0.0 8003
../../scripts/peer.sh 1.3 3 Peer3 230.0.0.0 8001 230.0.0.0 8002 230.0.0.0 8003

../../scripts/test.sh Peer1 STATE

```