/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.p2p.tomp2p;

import io.bitsquare.p2p.DHTService;
import io.bitsquare.user.User;

import java.security.KeyPair;
import java.security.PublicKey;

import javax.inject.Inject;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.dht.StorageLayer;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomP2PDHTService extends TomP2PService implements DHTService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PDHTService.class);
    private final KeyPair keyPair;
    private final Number160 pubKeyHashForMyDomain;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PDHTService(TomP2PNode tomP2PNode, User user) {
        super(tomP2PNode);
        keyPair = user.getMessageKeyPair();
        pubKeyHashForMyDomain = Utils.makeSHAHash(keyPair.getPublic().getEncoded());
    }

    @Override
    public void bootstrapCompleted() {
        super.bootstrapCompleted();

        StorageLayer.ProtectionEnable protectionDomainEnable = StorageLayer.ProtectionEnable.ALL;
        StorageLayer.ProtectionMode protectionDomainMode = StorageLayer.ProtectionMode.MASTER_PUBLIC_KEY;
        StorageLayer.ProtectionEnable protectionEntryEnable = StorageLayer.ProtectionEnable.ALL;
        StorageLayer.ProtectionMode protectionEntryMode = StorageLayer.ProtectionMode.MASTER_PUBLIC_KEY;

        peerDHT.storageLayer().protection(protectionDomainEnable, protectionDomainMode, protectionEntryEnable, protectionEntryMode);
    }

    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Put/Get: Public access. Used for offerbook invalidation timestamp
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Store data to given location key.
     * Write access: Anyone with locationKey
     *
     * @param locationKey
     * @param data
     * @return
     */
    public FuturePut putData(Number160 locationKey, Data data) {
        log.trace("putData");
        return peerDHT.put(locationKey).data(data).start();
    }
    // No protection, everybody can read.

    /**
     * Get data for given locationKey
     * Read access: Anyone with locationKey
     * 
     * @param locationKey
     * @return
     */
    public FutureGet getData(Number160 locationKey) {
        log.trace("getData");
        return peerDHT.get(locationKey).start();
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Put/Get: Domain protected, entry protected. Used for storing address.
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Store data to given location key and my domain.
     * Write access: Anybody who has pubKey if domain is not used before. KeyPair owner of pubKey can overwrite and reserve that domain.
     *        We save early an entry so we have that domain reserved and nobody else can use it.
     *        Additionally we use entry protection, so domain owner is data owner.
     *
     * @param locationKey
     * @param data
     * @return
     */
    public FuturePut putDataToMyProtectedDomain(Number160 locationKey, Data data) {
        log.trace("putDataToMyProtectedDomain");
        data.protectEntry(keyPair).sign();
        return peerDHT.put(locationKey).data(data).sign().protectDomain().domainKey(pubKeyHashForMyDomain).start();
    }

    /**
     * Read data for given location and publicKey of that domain.
     * Read access: Anyone who has publicKey
     *
     * @param locationKey
     * @param publicKey
     * @return
     */
    public FutureGet getDataOfProtectedDomain(Number160 locationKey, PublicKey publicKey) {
        log.trace("getDataOfProtectedDomain");
        final Number160 pubKeyHashOfDomainOwner = Utils.makeSHAHash(publicKey.getEncoded());
        return peerDHT.get(locationKey).domainKey(pubKeyHashOfDomainOwner).start();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Add/remove/get from map: Entry protected, no domain protection. Used for offerbook and arbitrators
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Add data to a map. For the entry contentKey of data is used (internally).
     * Write access: Anyone can add entries. But nobody can overwrite an existing entry as it is protected by data protection.
     * 
     * @param locationKey
     * @param data
     * @return
     */
    public FuturePut addProtectedDataToMap(Number160 locationKey, Data data) {
        log.trace("addProtectedDataToMap");
        data.protectEntry(keyPair).sign();
        log.trace("addProtectedDataToMap with contentKey " + data.hash().toString());
        return peerDHT.add(locationKey).data(data).sign().start();
    }

    /**
     * Remove entry from map for given locationKey. ContentKey of data is used for removing the entry.
     * Access: Only the owner of the data entry can remove it, as it was written with entry protection.
     * 
     * @param locationKey
     * @param data
     * @return
     */
    public FutureRemove removeProtectedDataFromMap(Number160 locationKey, Data data) {
        log.trace("removeProtectedDataFromMap");
        Number160 contentKey = data.hash();
        log.trace("removeProtectedDataFromMap with contentKey " + contentKey.toString());
        return peerDHT.remove(locationKey).contentKey(contentKey).sign().start();
    }

    /**
     * Get map for given locationKey with all entries.
     * Access: Everybody can read.
     * 
     * @param locationKey
     * @return
     */
    public FutureGet getMap(Number160 locationKey) {
        log.trace("getMap");
        return peerDHT.get(locationKey).all().start();
    }



    ///////////////////////////////////////////////////////////////////////////////////////////
    // Add/remove/get from map: Domain protection, no data protection. Used for mailbox. For getting privacy we use encryption (not part of DHT infrastructure)
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Add data to a map. For the entry contentKey of data is used (internally).
     * Write access: Anyone can add entries. But nobody expect the domain owner can overwrite/remove an existing entry as it is protected by the domain owner.
     *
     * @param locationKey
     * @param data
     * @return
     */
    public FuturePut addDataToMapOfProtectedDomain(Number160 locationKey, Data data, PublicKey publicKey) {
        log.trace("addDataToMapOfProtectedDomain");
        log.trace("addDataToMapOfProtectedDomain with contentKey " + data.hash().toString());
        final Number160 pubKeyHashOfDomainOwner = Utils.makeSHAHash(publicKey.getEncoded());
        return peerDHT.add(locationKey).data(data).protectDomain().domainKey(pubKeyHashOfDomainOwner).start();
    }

    /**
     * Remove entry from map for given locationKey. ContentKey of data is used for removing the entry.
     * Access: Only the owner of the data entry can remove it, as it was written with entry protection.
     *
     * @param locationKey
     * @param data
     * @return
     */
    public FutureRemove removeDataFromMapOfMyProtectedDomain(Number160 locationKey, Data data) {
        log.trace("removeDataFromMapOfMyProtectedDomain");
        Number160 contentKey = data.hash();
        log.trace("removeDataFromMapOfMyProtectedDomain with contentKey " + contentKey.toString());
        return peerDHT.remove(locationKey).contentKey(contentKey).protectDomain().sign().domainKey(pubKeyHashForMyDomain).start();
    }

    /**
     * Get map for given locationKey with all entries.
     * Access: Everybody can read.
     *
     * @param locationKey
     * @return
     */
    public FutureGet getDataFromMapOfMyProtectedDomain(Number160 locationKey) {
        log.trace("getDataFromMapOfMyProtectedDomain");
        return peerDHT.get(locationKey).all().domainKey(pubKeyHashForMyDomain).start();
    }


}
