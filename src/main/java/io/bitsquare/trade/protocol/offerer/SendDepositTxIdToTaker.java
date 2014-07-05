package io.bitsquare.trade.protocol.offerer;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.protocol.FaultHandler;
import io.bitsquare.trade.protocol.ResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendDepositTxIdToTaker
{
    private static final Logger log = LoggerFactory.getLogger(SendDepositTxIdToTaker.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, PeerAddress peerAddress, MessageFacade messageFacade, String tradeId, Transaction depositTransaction)
    {
        log.trace("Run task");
        DepositTxPublishedMessage tradeMessage = new DepositTxPublishedMessage(tradeId, Utils.bytesToHexString(depositTransaction.bitcoinSerialize()));
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, new OutgoingTradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("DepositTxPublishedMessage successfully arrived at peer");
                resultHandler.onResult();
            }

            @Override
            public void onFailed()
            {
                log.error("DepositTxPublishedMessage faultHandler.onFault to arrive at peer");
                faultHandler.onFault(new Exception("DepositTxPublishedMessage faultHandler.onFault to arrive at peer"));
            }
        });
    }

}
