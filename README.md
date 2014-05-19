# bitsquare.io

Bitsquare is a P2P Fiat-BTC Exchange, extensible to a generic P2P trading platform (include commodities and
cryptocurrencies)

This is just a proof of concept prototype for demonstrating the basic workflow of the trader process.
It is not at all production code style (no tests, verifications missing, very limited use cases,...).

The project use Java 8 and Maven.
We use the bitcoinj library and TomP2P for DHT and messaging.

Test net is currently not working with bitcoinj as the DNS seed servers are not setup correctly (See: http://sourceforge.net/p/bitcoin/mailman/message/32349208/).
To use the RegTest mode you need to set regtest=1 in the bitcoin.config file inside the bitcoin data directory (https://en.bitcoin.it/wiki/Running_Bitcoin).
Then you can generate coins on demand with the Bitcoin qt client with that command in the console: setgenerate true 101  (101 only for the first start because the coin maturity of 100 blocks).
See: https://bitcoinj.github.io/testing
You can change the network mode in the guice module: BitSquareModule.java


### Implemented (prototype level):
* Orderbook with filtering offers by amount, price, order type, trading account(buy, sell)
* Create offer
* Take offer
* Simple persistence
* bitcoinj integration
* Setup with account registration and tx with OP_RETURN + embedded and blinded bank account data
* Offer fee payment with a OP_RETURN tx and fees to miners
* Pay in to MS fund
* Payout from MS fund
* TomP2P as messaging lib integrated and basic use cases in msg screen implemented: orderbook, add order, remove order, peer interaction
* Payment process implemented with messaging for Offerer buy BTC case
* Hash of contract data embedded into tx (OP_RETURN)

### Next steps:
* Conceptual refinements
* Arbitrator integration concept
* Other trade variants (Buy BTC taker, Sell BTC offerer, Sell BTC offerer)
* Develop funding model
* Start development of production version


### Screenshots of basic the use cases:
* [Registration screen 1](https://github.com/bitsquare/bitsquare/tree/master/screenshots/registration_3.png)
* [Registration screen 2](https://github.com/bitsquare/bitsquare/tree/master/screenshots/registration_bank_account.png)
* [Orderbook screen 1](https://github.com/bitsquare/bitsquare/tree/master/screenshots/orderbook1.png)
* [Orderbook screen 2](https://github.com/bitsquare/bitsquare/tree/master/screenshots/orderbook2.png)
* [Create Offer screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/create_offer_2.png)
* [Take offer screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/take_offer.png)
* [Deposit tx screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/deposit_conf.png)
* [Check bank tx screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/bank_tx_inited.png)
* [Trade completed screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/trade_complete.png)
* [More screenshots](https://github.com/bitsquare/bitsquare/tree/master/screenshots)


### Transactions of a test trade on main net:
Offerer registration tx: https://blockchain.info/de/tx/06ea3c2a5fb79f622d3e3def7c6a20274274fcbf9ec69b95bdfe9b347bbbdf76
Taker registration tx: https://blockchain.info/tx/8352ab9fe78593f48ef70d414d494ebd614d99fab147d0342910525e9284ba8f
Create offer fee tx: https://blockchain.info/tx/24f4d229edace44d9123628363a16cd7041f5d34ba6bef812807b9be03a64692
Take offer fee tx: https://blockchain.info/tx/06ea3c2a5fb79f622d3e3def7c6a20274274fcbf9ec69b95bdfe9b347bbbdf76
Deposit tx: https://blockchain.info/de/tx/98c6ae55963022871216a6a124c1e1ed7f6308560e76b72617b6b54cf50ef412
Payout tx: https://blockchain.info/tx/498e2c299ca991b27f61b63fb6ee457819ee9e33ee5a1d250fde47eb15199adc


### Links:
* Web: http://bitsquare.io
* Whitepaper: https://docs.google.com/document/d/1d3EiWZdaM89-P6MVhS53unXv2-pDpSFsN3W4kCGXKgY/edit?pli=1
* Overview: http://bitsquare.io/images/overview.png
* Discussion: https://bitcointalk.org/index.php?topic=462236
