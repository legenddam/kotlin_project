package io.bitsquare.p2p.network;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.common.ByteArrayUtils;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.messaging.PrefixedSealedAndSignedMessage;
import io.bitsquare.p2p.network.messages.CloseConnectionMessage;
import io.bitsquare.p2p.network.messages.SendersNodeAddressMessage;
import io.bitsquare.p2p.peers.keepalive.messages.KeepAliveMessage;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Connection is created by the server thread or by sendMessage from NetworkNode.
 * All handlers are called on User thread.
 */
public class Connection implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(Connection.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum PeerType {
        SEED_NODE,
        PEER,
        DIRECT_MSG_PEER
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final int MAX_MSG_SIZE = 100 * 1024;              // 100 kb of compressed data
    //TODO decrease limits again after testing
    private static final int MSG_THROTTLE_PER_SEC = 10;              // With MAX_MSG_SIZE of 100kb results in bandwidth of 10 mbit/sec 
    private static final int MSG_THROTTLE_PER_10_SEC = 100;           // With MAX_MSG_SIZE of 100kb results in bandwidth of 100 mbit/sec for 10 sec 
    private static final int SOCKET_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(60);

    public static int getMaxMsgSize() {
        return MAX_MSG_SIZE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final Socket socket;
    // private final MessageListener messageListener;
    private final ConnectionListener connectionListener;
    private final String portInfo;
    private final String uid;
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    private final ReentrantLock objectOutputStreamLock = new ReentrantLock(true);
    // holder of state shared between InputHandler and Connection
    private final SharedModel sharedModel;
    private final Statistic statistic;

    // set in init
    private InputHandler inputHandler;
    private ObjectOutputStream objectOutputStream;

    // mutable data, set from other threads but not changed internally.
    private Optional<NodeAddress> peersNodeAddressOptional = Optional.empty();
    private volatile boolean stopped;
    private PeerType peerType;
    private final ObjectProperty<NodeAddress> peersNodeAddressProperty = new SimpleObjectProperty<>();
    private final List<Tuple2<Long, Serializable>> messageTimeStamps = new ArrayList<>();
    private final CopyOnWriteArraySet<MessageListener> messageListeners = new CopyOnWriteArraySet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    Connection(Socket socket, MessageListener messageListener, ConnectionListener connectionListener,
               @Nullable NodeAddress peersNodeAddress) {
        this.socket = socket;
        //this.messageListener = messageListener;
        this.connectionListener = connectionListener;
        uid = UUID.randomUUID().toString();
        statistic = new Statistic();

        addMessageListener(messageListener);

        sharedModel = new SharedModel(this, socket);

        if (socket.getLocalPort() == 0)
            portInfo = "port=" + socket.getPort();
        else
            portInfo = "localPort=" + socket.getLocalPort() + "/port=" + socket.getPort();

        init(peersNodeAddress);
    }

    private void init(@Nullable NodeAddress peersNodeAddress) {
        try {
            socket.setSoTimeout(SOCKET_TIMEOUT);
            // Need to access first the ObjectOutputStream otherwise the ObjectInputStream would block
            // See: https://stackoverflow.com/questions/5658089/java-creating-a-new-objectinputstream-blocks/5658109#5658109
            // When you construct an ObjectInputStream, in the constructor the class attempts to read a header that 
            // the associated ObjectOutputStream on the other end of the connection has written.
            // It will not return until that header has been read. 
            objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());

            // We create a thread for handling inputStream data
            inputHandler = new InputHandler(sharedModel, objectInputStream, portInfo, this);
            singleThreadExecutor.submit(inputHandler);
        } catch (IOException e) {
            sharedModel.handleConnectionException(e);
        }

        // Use Peer as default, in case of other types they will set it as soon as possible.
        peerType = PeerType.PEER;

        if (peersNodeAddress != null)
            setPeersNodeAddress(peersNodeAddress);

        log.trace("New connection created: " + this.toString());

        UserThread.execute(() -> connectionListener.onConnection(this));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Called form various threads
    public void sendMessage(Message message) {
        if (!stopped) {
            try {
                String peersNodeAddress = peersNodeAddressOptional.isPresent() ? peersNodeAddressOptional.get().toString() : "null";
                int size = ByteArrayUtils.objectToByteArray(message).length;

                if (message instanceof PrefixedSealedAndSignedMessage && peersNodeAddressOptional.isPresent()) {
                    setPeerType(Connection.PeerType.DIRECT_MSG_PEER);

                    log.info("\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                                    "Sending direct message to peer" +
                                    "Write object to outputStream to peer: {} (uid={})\ntruncated message={} / size={}" +
                                    "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n",
                            peersNodeAddress, uid, StringUtils.abbreviate(message.toString(), 100), size);
                } else {
                    log.info("\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n" +
                                    "Write object to outputStream to peer: {} (uid={})\ntruncated message={} / size={}" +
                                    "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n",
                            peersNodeAddress, uid, StringUtils.abbreviate(message.toString(), 100), size);
                }

                if (!stopped) {
                    objectOutputStreamLock.lock();
                    objectOutputStream.writeObject(message);
                    objectOutputStream.flush();

                    statistic.addSentBytes(size);
                    statistic.addSentMessage(message);

                    // We don't want to get the activity ts updated by ping/pong msg
                    if (!(message instanceof KeepAliveMessage))
                        statistic.updateLastActivityTimestamp();
                }
            } catch (IOException e) {
                // an exception lead to a shutdown
                sharedModel.handleConnectionException(e);
            } catch (Throwable t) {
                log.error(t.getMessage());
                t.printStackTrace();
                sharedModel.handleConnectionException(t);
            } finally {
                if (objectOutputStreamLock.isLocked())
                    objectOutputStreamLock.unlock();
            }
        } else {
            log.debug("called sendMessage but was already stopped");
        }
    }

    public void addMessageListener(MessageListener messageListener) {
        boolean isNewEntry = messageListeners.add(messageListener);
        if (!isNewEntry)
            log.warn("Try to add a messageListener which was already added.");
    }

    public void removeMessageListener(MessageListener messageListener) {
        boolean contained = messageListeners.remove(messageListener);
        if (!contained)
            log.debug("Try to remove a messageListener which was never added.\n\t" +
                    "That might happen because of async behaviour of CopyOnWriteArraySet");
    }

    @SuppressWarnings("unused")
    public boolean reportIllegalRequest(RuleViolation ruleViolation) {
        return sharedModel.reportInvalidRequest(ruleViolation);
    }

    private boolean violatesThrottleLimit(Serializable serializable) {
        long now = System.currentTimeMillis();
        boolean violated = false;
        //TODO remove serializable storage after network is tested stable
        if (messageTimeStamps.size() >= MSG_THROTTLE_PER_SEC) {
            // check if we got more than 10 (MSG_THROTTLE_PER_SEC) msg per sec.
            long compareValue = messageTimeStamps.get(messageTimeStamps.size() - MSG_THROTTLE_PER_SEC).first;
            // if duration < 1 sec we received too much messages
            violated = now - compareValue < TimeUnit.SECONDS.toMillis(1);
            if (violated) {
                log.error("violatesThrottleLimit 1 ");
                log.error("elapsed " + (now - compareValue));
                log.error("messageTimeStamps: \n\t" + messageTimeStamps.stream()
                        .map(e -> "\n\tts=" + e.first.toString() + " message=" + e.second.toString())
                        .collect(Collectors.toList()).toString());
            }
        }

        if (messageTimeStamps.size() >= MSG_THROTTLE_PER_10_SEC) {
            if (!violated) {
                // check if we got more than 50 msg per 10 sec.
                long compareValue = messageTimeStamps.get(messageTimeStamps.size() - MSG_THROTTLE_PER_10_SEC).first;
                // if duration < 10 sec we received too much messages
                violated = now - compareValue < TimeUnit.SECONDS.toMillis(10);

                if (violated) {
                    log.error("violatesThrottleLimit 2 ");
                    log.error("elapsed " + (now - compareValue));
                    log.error("messageTimeStamps: \n\t" + messageTimeStamps.stream()
                            .map(e -> "\n\tts=" + e.first.toString() + " message=" + e.second.toString())
                            .collect(Collectors.toList()).toString());
                }
            }
            // we limit to max 50 (MSG_THROTTLE_PER_10SEC) entries
            messageTimeStamps.remove(0);
        }

        messageTimeStamps.add(new Tuple2<>(now, serializable));
        return violated;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Only receive non - CloseConnectionMessage messages
    @Override
    public void onMessage(Message message, Connection connection) {
        checkArgument(connection.equals(this));
        UserThread.execute(() -> messageListeners.stream().forEach(e -> e.onMessage(message, connection)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setPeerType(PeerType peerType) {
        Log.traceCall(peerType.toString());
        this.peerType = peerType;
    }

    public void setPeersNodeAddress(NodeAddress peerNodeAddress) {
        checkNotNull(peerNodeAddress, "peerAddress must not be null");
        peersNodeAddressOptional = Optional.of(peerNodeAddress);

        String peersNodeAddress = getPeersNodeAddressOptional().isPresent() ? getPeersNodeAddressOptional().get().getFullAddress() : "";
        if (this instanceof InboundConnection) {
            log.info("\n\n############################################################\n" +
                    "We got the peers node address set.\n" +
                    "peersNodeAddress= " + peersNodeAddress +
                    "\nconnection.uid=" + getUid() +
                    "\n############################################################\n");
        }

        peersNodeAddressProperty.set(peerNodeAddress);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<NodeAddress> getPeersNodeAddressOptional() {
        return peersNodeAddressOptional;
    }

    public String getUid() {
        return uid;
    }

    public boolean hasPeersNodeAddress() {
        return peersNodeAddressOptional.isPresent();
    }

    public boolean isStopped() {
        return stopped;
    }

    public PeerType getPeerType() {
        return peerType;
    }

    public ReadOnlyObjectProperty<NodeAddress> peersNodeAddressProperty() {
        return peersNodeAddressProperty;
    }

    public RuleViolation getRuleViolation() {
        return sharedModel.getRuleViolation();
    }

    public Statistic getStatistic() {
        return statistic;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ShutDown
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown(CloseConnectionReason closeConnectionReason) {
        shutDown(closeConnectionReason, null);
    }

    public void shutDown(CloseConnectionReason closeConnectionReason, @Nullable Runnable shutDownCompleteHandler) {
        Log.traceCall(this.toString());
        if (!stopped) {
            String peersNodeAddress = peersNodeAddressOptional.isPresent() ? peersNodeAddressOptional.get().toString() : "null";
            log.info("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                    "ShutDown connection:"
                    + "\npeersNodeAddress=" + peersNodeAddress
                    + "\ncloseConnectionReason=" + closeConnectionReason
                    + "\nuid=" + uid
                    + "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n");

            if (closeConnectionReason.sendCloseMessage) {
                new Thread(() -> {
                    Thread.currentThread().setName("Connection:SendCloseConnectionMessage-" + this.uid);
                    Log.traceCall("sendCloseConnectionMessage");
                    try {
                        String reason = closeConnectionReason == CloseConnectionReason.RULE_VIOLATION ?
                                sharedModel.getRuleViolation().name() : closeConnectionReason.name();
                        sendMessage(new CloseConnectionMessage(reason));

                        setStopFlags();

                        Uninterruptibles.sleepUninterruptibly(200, TimeUnit.MILLISECONDS);
                    } catch (Throwable t) {
                        log.error(t.getMessage());
                        t.printStackTrace();
                    } finally {
                        setStopFlags();
                        UserThread.execute(() -> doShutDown(closeConnectionReason, shutDownCompleteHandler));
                    }
                }).start();
            } else {
                setStopFlags();
                doShutDown(closeConnectionReason, shutDownCompleteHandler);
            }
        } else {
            //TODO find out why we get called that
            log.warn("stopped was already true at shutDown call");
            UserThread.execute(() -> doShutDown(closeConnectionReason, shutDownCompleteHandler));
        }
    }

    private void setStopFlags() {
        stopped = true;
        sharedModel.stop();
        if (inputHandler != null)
            inputHandler.stop();
    }

    private void doShutDown(CloseConnectionReason closeConnectionReason, @Nullable Runnable shutDownCompleteHandler) {
        // Use UserThread.execute as its not clear if that is called from a non-UserThread
        UserThread.execute(() -> connectionListener.onDisconnect(closeConnectionReason, this));
        try {
            sharedModel.getSocket().close();
        } catch (SocketException e) {
            log.trace("SocketException at shutdown might be expected " + e.getMessage());
        } catch (IOException e) {
            log.error("Exception at shutdown. " + e.getMessage());
            e.printStackTrace();
        } finally {
            MoreExecutors.shutdownAndAwaitTermination(singleThreadExecutor, 500, TimeUnit.MILLISECONDS);

            log.debug("Connection shutdown complete " + this.toString());
            // Use UserThread.execute as its not clear if that is called from a non-UserThread
            if (shutDownCompleteHandler != null)
                UserThread.execute(shutDownCompleteHandler);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Connection)) return false;

        Connection that = (Connection) o;

        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);

    }

    @Override
    public int hashCode() {
        return uid != null ? uid.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Connection{" +
                "peerAddress=" + peersNodeAddressOptional +
                ", peerType=" + peerType +
                ", uid='" + uid + '\'' +
                '}';
    }

    @SuppressWarnings("unused")
    public String printDetails() {
        return "Connection{" +
                "peerAddress=" + peersNodeAddressOptional +
                ", peerType=" + peerType +
                ", portInfo=" + portInfo +
                ", uid='" + uid + '\'' +
                ", sharedSpace=" + sharedModel.toString() +
                ", stopped=" + stopped +
                '}';
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SharedSpace
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Holds all shared data between Connection and InputHandler
     * Runs in same thread as Connection
     */
    private static class SharedModel {
        private static final Logger log = LoggerFactory.getLogger(SharedModel.class);

        private final Connection connection;
        private final Socket socket;
        private final ConcurrentHashMap<RuleViolation, Integer> ruleViolations = new ConcurrentHashMap<>();

        // mutable
        private volatile boolean stopped;
        private CloseConnectionReason closeConnectionReason;
        private RuleViolation ruleViolation;

        public SharedModel(Connection connection, Socket socket) {
            this.connection = connection;
            this.socket = socket;
        }

        public boolean reportInvalidRequest(RuleViolation ruleViolation) {
            log.warn("We got reported an corrupt request " + ruleViolation + "\n\tconnection=" + this);
            int numRuleViolations;
            if (ruleViolations.contains(ruleViolation))
                numRuleViolations = ruleViolations.get(ruleViolation);
            else
                numRuleViolations = 0;

            numRuleViolations++;
            ruleViolations.put(ruleViolation, numRuleViolations);

            if (numRuleViolations >= ruleViolation.maxTolerance) {
                log.warn("We close connection as we received too many corrupt requests.\n" +
                        "numRuleViolations={}\n\t" +
                        "corruptRequest={}\n\t" +
                        "corruptRequests={}\n\t" +
                        "connection={}", numRuleViolations, ruleViolation, ruleViolations.toString(), connection);
                this.ruleViolation = ruleViolation;
                shutDown(CloseConnectionReason.RULE_VIOLATION);
                return true;
            } else {
                return false;
            }
        }

        public void handleConnectionException(Throwable e) {
            Log.traceCall(e.toString());
            if (e instanceof SocketException) {
                if (socket.isClosed())
                    closeConnectionReason = CloseConnectionReason.SOCKET_CLOSED;
                else
                    closeConnectionReason = CloseConnectionReason.RESET;
            } else if (e instanceof SocketTimeoutException || e instanceof TimeoutException) {
                closeConnectionReason = CloseConnectionReason.SOCKET_TIMEOUT;
                log.warn("SocketTimeoutException at socket " + socket.toString() + "\n\tconnection={}" + this);
            } else if (e instanceof EOFException || e instanceof StreamCorruptedException) {
                closeConnectionReason = CloseConnectionReason.TERMINATED;
            } else {
                closeConnectionReason = CloseConnectionReason.UNKNOWN_EXCEPTION;

                String message;
                if (e.getMessage() != null)
                    message = e.getMessage();
                else
                    message = e.toString();

                log.warn("Unknown reason for exception at socket {}\n\tconnection={}\n\tException=",
                        socket.toString(), this, message);
                e.printStackTrace();
            }

            shutDown(closeConnectionReason);
        }

        public void shutDown(CloseConnectionReason closeConnectionReason) {
            if (!stopped) {
                stopped = true;
                connection.shutDown(closeConnectionReason);
            }
        }

        public Socket getSocket() {
            return socket;
        }

        public void stop() {
            this.stopped = true;
        }

        public RuleViolation getRuleViolation() {
            return ruleViolation;
        }

        @Override
        public String toString() {
            return "SharedSpace{" +
                    ", socket=" + socket +
                    ", ruleViolations=" + ruleViolations +
                    '}';
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // InputHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Runs in same thread as Connection
    private static class InputHandler implements Runnable {
        private static final Logger log = LoggerFactory.getLogger(InputHandler.class);

        private final SharedModel sharedModel;
        private final ObjectInputStream objectInputStream;
        private final String portInfo;
        private final MessageListener messageListener;

        private volatile boolean stopped;

        public InputHandler(SharedModel sharedModel, ObjectInputStream objectInputStream, String portInfo, MessageListener messageListener) {
            this.sharedModel = sharedModel;
            this.objectInputStream = objectInputStream;
            this.portInfo = portInfo;
            this.messageListener = messageListener;
        }

        public void stop() {
            if (!stopped) {
                try {
                    objectInputStream.close();
                } catch (IOException e) {
                    log.error("IOException at InputHandler.stop\n" + e.getMessage());
                    e.printStackTrace();
                } finally {
                    stopped = true;
                }
            }
        }

        @Override
        public void run() {
            try {
                Thread.currentThread().setName("InputHandler-" + portInfo);
                while (!stopped && !Thread.currentThread().isInterrupted()) {
                    try {
                        log.trace("InputHandler waiting for incoming messages.\n\tConnection=" + sharedModel.connection);
                        Object rawInputObject = objectInputStream.readObject();

                        int size = ByteArrayUtils.objectToByteArray(rawInputObject).length;
                        boolean doPrintLogs = true;
                        if (rawInputObject instanceof Message) {
                            Message message = (Message) rawInputObject;
                            Connection connection = sharedModel.connection;
                            connection.statistic.addReceivedBytes(size);
                            connection.statistic.addReceivedMessage(message);
                            // We dont want to get all KeepAliveMessage logged
                            doPrintLogs = !(message instanceof KeepAliveMessage);
                        }

                        if (doPrintLogs) {
                            log.info("\n\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n" +
                                            "New data arrived at inputHandler of connection {}.\n" +
                                            "Received object (truncated)={} / size={}"
                                            + "\n<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n",
                                    sharedModel.connection,
                                    StringUtils.abbreviate(rawInputObject.toString(), 100),
                                    size);
                        }

                        if (size > getMaxMsgSize()) {
                            if (reportInvalidRequest(RuleViolation.MAX_MSG_SIZE_EXCEEDED))
                                return;
                        }

                        Serializable serializable;
                        if (rawInputObject instanceof Serializable) {
                            serializable = (Serializable) rawInputObject;
                        } else {
                            reportInvalidRequest(RuleViolation.INVALID_DATA_TYPE);
                            return;
                        }

                        if (sharedModel.connection.violatesThrottleLimit(serializable)) {
                            if (reportInvalidRequest(RuleViolation.THROTTLE_LIMIT_EXCEEDED))
                                return;
                        }

                        if (!(serializable instanceof Message)) {
                            reportInvalidRequest(RuleViolation.INVALID_DATA_TYPE);
                            return;
                        }

                        Message message = (Message) serializable;
                        Connection connection = sharedModel.connection;
                        connection.statistic.addReceivedBytes(size);
                        connection.statistic.addReceivedMessage(message);

                        if (message.getMessageVersion() != Version.getP2PMessageVersion()) {
                            log.warn("message.getMessageVersion()=" + message.getMessageVersion());
                            log.warn("message=" + message);
                            log.warn("Version.getP2PMessageVersion()=" + Version.getP2PMessageVersion());
                            reportInvalidRequest(RuleViolation.WRONG_NETWORK_ID);
                            return;
                        }

                        if (message instanceof CloseConnectionMessage) {
                            log.info("CloseConnectionMessage received. Reason={}\n\t" +
                                    "connection={}", ((CloseConnectionMessage) message).reason, connection);
                            stop();
                            sharedModel.shutDown(CloseConnectionReason.CLOSE_REQUESTED_BY_PEER);
                        } else if (!stopped) {
                            // We don't want to get the activity ts updated by ping/pong msg
                            if (!(message instanceof KeepAliveMessage))
                                connection.statistic.updateLastActivityTimestamp();

                            // First a seed node gets a message form a peer (PreliminaryDataRequest using 
                            // AnonymousMessage interface) which does not has its hidden service 
                            // published, so does not know its address. As the IncomingConnection does not has the 
                            // peersNodeAddress set that connection cannot be used for outgoing messages until we 
                            // get the address set.
                            // At the data update message (DataRequest using SendersNodeAddressMessage interface) 
                            // after the HS is published we get the peers address set.

                            // There are only those messages used for new connections to a peer:
                            // 1. PreliminaryDataRequest
                            // 2. DataRequest (implements SendersNodeAddressMessage)
                            // 3. GetPeersRequest (implements SendersNodeAddressMessage)
                            // 4. DirectMessage (implements SendersNodeAddressMessage)
                            if (message instanceof SendersNodeAddressMessage) {
                                NodeAddress senderNodeAddress = ((SendersNodeAddressMessage) message).getSenderNodeAddress();
                                Optional<NodeAddress> peersNodeAddressOptional = connection.getPeersNodeAddressOptional();
                                if (peersNodeAddressOptional.isPresent()) {
                                    checkArgument(peersNodeAddressOptional.get().equals(senderNodeAddress),
                                            "senderNodeAddress not matching connections peer address.\n\t" +
                                                    "message=" + message);
                                } else {
                                    connection.setPeersNodeAddress(senderNodeAddress);
                                }
                            }

                            if (message instanceof PrefixedSealedAndSignedMessage)
                                connection.setPeerType(Connection.PeerType.DIRECT_MSG_PEER);

                            messageListener.onMessage(message, connection);
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        log.warn(e.getMessage());
                        e.printStackTrace();
                        reportInvalidRequest(RuleViolation.INVALID_DATA_TYPE);
                        return;
                    } catch (IOException e) {
                        stop();
                        sharedModel.handleConnectionException(e);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        stop();
                        sharedModel.handleConnectionException(new Exception(t));
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                stop();
                sharedModel.handleConnectionException(new Exception(t));
            }
        }

        private boolean reportInvalidRequest(RuleViolation ruleViolation) {
            boolean causedShutDown = sharedModel.reportInvalidRequest(ruleViolation);
            if (causedShutDown)
                stop();
            return causedShutDown;
        }

        @Override
        public String toString() {
            return "InputHandler{" +
                    "sharedSpace=" + sharedModel +
                    ", port=" + portInfo +
                    ", stopped=" + stopped +
                    '}';
        }
    }
}