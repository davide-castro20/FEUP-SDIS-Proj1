
if ((argc == 1 ))
then
  rm -rf build
	scripts/cleanup.sh 1
	scripts/cleanup.sh 2
fi

pkill java
pkill rmiregistry
sleep 1
cd "build" && rmiregistry &
scripts/compile.sh
sleep 2
scripts/setup.sh 1
scripts/setup.sh 2
sleep 2
scripts/peer.sh 1.0 1 Peer1 230.0.0.0 8001 230.0.0.0 8002 230.0.0.0 8003 &
scripts/peer.sh 1.0 2 Peer2 230.0.0.0 8001 230.0.0.0 8002 230.0.0.0 8003 &
sleep 2
scripts/test.sh Peer1 BACKUP teste150k 2