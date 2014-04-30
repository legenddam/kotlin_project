package io.bitsquare.btc;

import com.google.inject.Inject;
import io.bitsquare.bank.BankAccount;

/**
 * That facade delivers blockchain functionality from the bitcoinJ library
 * Code from BitcoinJ must not be used outside that facade.
 * That way a change of the library will only affect that class.
 */
public class BlockChainFacade
{
    @Inject
    public BlockChainFacade()
    {

    }

    public boolean verifyEmbeddedData(String address)
    {
        // tx id 76982adc582657b2eb68f3e43341596a68aadc4ef6b9590e88e93387d4d5d1f9
        // address: mjbxLbuVpU1cNXLJbrJZyirYwweoRPVVTj
        return true;
            /*
        if (findAddressInBlockChain(address) && isFeePayed(address))
            return getDataForTxWithAddress(address) != null;
        else
            return true;  */
    }

    private boolean findAddressInBlockChain(String address)
    {
        // TODO
        // lookup for address in blockchain
        return true;
    }

    private byte[] getDataForTxWithAddress(String address)
    {
        // TODO
        // return data after OP_RETURN
        return null;
    }

    private boolean isFeePayed(String address)
    {
        // TODO
        // check if fee is payed
        return true;
    }

    public boolean isAccountIDBlacklisted(String accountID)
    {
        // TODO
        // check if accountID is on blacklist
        return false;
    }

    public boolean isBankAccountBlacklisted(BankAccount bankAccount)
    {
        // TODO
        // check if accountID is on blacklist
        return false;
    }
}
