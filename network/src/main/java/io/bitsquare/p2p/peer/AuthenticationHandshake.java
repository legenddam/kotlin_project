package io.bitsquare.p2p.peer;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peer.messages.ChallengeMessage;
import io.bitsquare.p2p.peer.messages.GetPeersMessage;
import io.bitsquare.p2p.peer.messages.PeersMessage;
import io.bitsquare.p2p.peer.messages.RequestAuthenticationMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;


// authentication example: 
// node2 -> node1 RequestAuthenticationMessage
// node1: close connection
// node1 -> node2 ChallengeMessage on new connection
// node2: authentication to node1 done if nonce ok
// node2 -> node1 GetPeersMessage
// node1: authentication to node2 done if nonce ok
// node1 -> node2 PeersMessage

public class AuthenticationHandshake {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationHandshake.class);

    private final NetworkNode networkNode;
    private final PeerGroup peerGroup;
    private final Address myAddress;

    private SettableFuture<Connection> resultFuture;
    private long startAuthTs;
    private long nonce = 0;

    public AuthenticationHandshake(NetworkNode networkNode, PeerGroup peerGroup, Address myAddress) {
        this.networkNode = networkNode;
        this.peerGroup = peerGroup;
        this.myAddress = myAddress;

        setupMessageListener();
    }

    public SettableFuture<Connection> requestAuthenticationToPeer(Address peerAddress) {
        // Requesting peer
        resultFuture = SettableFuture.create();
        startAuthTs = System.currentTimeMillis();
        SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new RequestAuthenticationMessage(myAddress, getAndSetNonce()));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.info("send RequestAuthenticationMessage to " + peerAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("Send RequestAuthenticationMessage to " + peerAddress + " failed." +
                        "\nException:" + throwable.getMessage());
                UserThread.execute(() -> resultFuture.setException(throwable));
            }
        });

        return resultFuture;
    }

    public SettableFuture<Connection> requestAuthentication(Set<Address> remainingAddresses, Address peerAddress) {
        log.info("requestAuthentication " + this);
        log.info("remainingAddresses " + remainingAddresses);
        log.info("peerAddress " + peerAddress);
        // Requesting peer
        resultFuture = SettableFuture.create();
        startAuthTs = System.currentTimeMillis();
        remainingAddresses.remove(peerAddress);
        SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new RequestAuthenticationMessage(myAddress, getAndSetNonce()));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.info("send RequestAuthenticationMessage to " + peerAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("Send RequestAuthenticationMessage to " + peerAddress + " failed." +
                        "\nThat is expected if seed nodes are offline." +
                        "\nException:" + throwable.getMessage());
                log.trace("We try to authenticate to another random seed nodes of that list: " + remainingAddresses);
                authenticateToNextRandomPeer(remainingAddresses);
            }
        });

        return resultFuture;
    }


    public SettableFuture<Connection> processAuthenticationRequest(RequestAuthenticationMessage requestAuthenticationMessage, Connection connection) {
        // Responding peer
        resultFuture = SettableFuture.create();
        startAuthTs = System.currentTimeMillis();

        Address peerAddress = requestAuthenticationMessage.address;
        log.trace("RequestAuthenticationMessage from " + peerAddress + " at " + myAddress);
        connection.shutDown(() -> UserThread.runAfter(() -> {
                    // we delay a bit as listeners for connection.onDisconnect are on other threads and might lead to 
                    // inconsistent state (removal of connection from NetworkNode.authenticatedConnections)
                    log.trace("processAuthenticationMessage: connection.shutDown complete. RequestAuthenticationMessage from " + peerAddress + " at " + myAddress);

                    SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new ChallengeMessage(myAddress, requestAuthenticationMessage.nonce, getAndSetNonce()));
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.debug("onSuccess sending ChallengeMessage");
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            log.warn("onFailure sending ChallengeMessage.");
                            UserThread.execute(() -> resultFuture.setException(throwable));
                        }
                    });
                },
                100 + PeerGroup.simulateAuthTorNode,
                TimeUnit.MILLISECONDS));

        return resultFuture;
    }

    private void setupMessageListener() {
        networkNode.addMessageListener((message, connection) -> {
            if (message instanceof ChallengeMessage) {
                // Requesting peer
                ChallengeMessage challengeMessage = (ChallengeMessage) message;
                Address peerAddress = challengeMessage.address;
                log.trace("ChallengeMessage from " + peerAddress + " at " + myAddress);
                log.trace("challengeMessage" + challengeMessage);
                // HashMap<Address, Long> tempNonceMap = new HashMap<>(nonceMap);
                boolean verified = nonce != 0 && nonce == challengeMessage.requesterNonce;
                if (verified) {
                    connection.setPeerAddress(peerAddress);
                    SettableFuture<Connection> future = networkNode.sendMessage(peerAddress,
                            new GetPeersMessage(myAddress, challengeMessage.challengerNonce, new HashSet<>(peerGroup.getAllPeerAddresses())));
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("GetPeersMessage sent successfully from " + myAddress + " to " + peerAddress);
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("GetPeersMessage sending failed " + throwable.getMessage());
                            UserThread.execute(() -> resultFuture.setException(throwable));
                        }
                    });
                } else {
                    log.warn("verify nonce failed. challengeMessage=" + challengeMessage + " / nonce=" + nonce);
                    UserThread.execute(() -> resultFuture.setException(new Exception("Verify nonce failed. challengeMessage=" + challengeMessage + " / nonceMap=" + nonce)));
                }
            } else if (message instanceof GetPeersMessage) {
                // Responding peer
                GetPeersMessage getPeersMessage = (GetPeersMessage) message;
                Address peerAddress = getPeersMessage.address;
                log.trace("GetPeersMessage from " + peerAddress + " at " + myAddress);
                boolean verified = nonce != 0 && nonce == getPeersMessage.challengerNonce;
                if (verified) {
                    // we add the reported peers to our own set
                    HashSet<Address> peerAddresses = ((GetPeersMessage) message).peerAddresses;
                    log.trace("Received peers: " + peerAddresses);
                    peerGroup.addToReportedPeers(peerAddresses, connection);

                    SettableFuture<Connection> future = networkNode.sendMessage(peerAddress,
                            new PeersMessage(myAddress, new HashSet<>(peerGroup.getAllPeerAddresses())));
                    log.trace("sent PeersMessage to " + peerAddress + " from " + myAddress
                            + " with allPeers=" + peerGroup.getAllPeerAddresses());
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("PeersMessage sent successfully from " + myAddress + " to " + peerAddress);
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("PeersMessage sending failed " + throwable.getMessage());
                            UserThread.execute(() -> resultFuture.setException(throwable));
                        }
                    });

                    log.info("\n\nAuthenticationComplete\nPeer with address " + peerAddress
                            + " authenticated (" + connection.getObjectId() + "). Took "
                            + (System.currentTimeMillis() - startAuthTs) + " ms. \n\n");

                    UserThread.execute(() -> resultFuture.set(connection));
                } else {
                    log.warn("verify nonce failed. getPeersMessage=" + getPeersMessage + " / nonceMap=" + nonce);
                    UserThread.execute(() -> resultFuture.setException(new Exception("Verify nonce failed. getPeersMessage=" + getPeersMessage + " / nonceMap=" + nonce)));
                }
            } else if (message instanceof PeersMessage) {
                // Requesting peer
                PeersMessage peersMessage = (PeersMessage) message;
                Address peerAddress = peersMessage.address;
                log.trace("PeersMessage from " + peerAddress + " at " + myAddress);
                HashSet<Address> peerAddresses = peersMessage.peerAddresses;
                log.trace("Received peers: " + peerAddresses);
                peerGroup.addToReportedPeers(peerAddresses, connection);

                // we wait until the handshake is completed before setting the authenticate flag
                // authentication at both sides of the connection
                log.info("\n\nAuthenticationComplete\nPeer with address " + peerAddress
                        + " authenticated (" + connection.getObjectId() + "). Took "
                        + (System.currentTimeMillis() - startAuthTs) + " ms. \n\n");

                UserThread.execute(() -> resultFuture.set(connection));
            }
        });
    }


    private void authenticateToNextRandomPeer(Set<Address> remainingAddresses) {
        Optional<Tuple2<Address, Set<Address>>> tupleOptional = getRandomAddressAndRemainingSet(remainingAddresses);
        if (tupleOptional.isPresent()) {
            Tuple2<Address, Set<Address>> tuple = tupleOptional.get();
            requestAuthentication(tuple.second, tuple.first);
        } else {
            log.info("No other seed node found. That is expected for the first seed node.");
            UserThread.execute(() -> resultFuture.set(null));
        }
    }

    private Optional<Tuple2<Address, Set<Address>>> getRandomAddressAndRemainingSet(Set<Address> addresses) {
        if (!addresses.isEmpty()) {
            List<Address> list = new ArrayList<>(addresses);
            Collections.shuffle(list);
            Address address = list.remove(0);
            return Optional.of(new Tuple2<>(address, Sets.newHashSet(list)));
        } else {
            return Optional.empty();
        }
    }

    private long getAndSetNonce() {
        nonce = new Random().nextLong();
        while (nonce == 0)
            nonce = getAndSetNonce();

        return nonce;
    }

}
