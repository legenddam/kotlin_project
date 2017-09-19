package io.bisq.api;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.bisq.api.model.*;
import io.bisq.api.model.Currency;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.util.MathUtils;
import io.bisq.common.util.Tuple2;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.WalletsSetup;
import io.bisq.core.offer.*;
import io.bisq.core.payment.*;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.provider.price.MarketPrice;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.BuyerAsMakerTrade;
import io.bisq.core.trade.SellerAsMakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.trade.failed.FailedTradesManager;
import io.bisq.core.trade.protocol.*;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.core.util.CoinUtil;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.P2PService;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

/**
 * This class is a proxy for all bitsquare features the model will use.
 * <p>
 * No methods/representations used in the interface layers (REST/Socket/...) should be used in this class.
 * => this should be the common gateway to bisq used by all outward-facing API classes.
 *
 * If the bisq code is refactored correctly, this class could become very light.
 */
@Slf4j
public class BisqProxy {
    private BtcWalletService btcWalletService;
    private User user;
    private TradeManager tradeManager;
    private ClosedTradableManager closedTradableManager;
    private FailedTradesManager failedTradesManager;
    private OpenOfferManager openOfferManager;
    private OfferBookService offerBookService;
    private P2PService p2PService;
    private KeyRing keyRing;
    private PriceFeedService priceFeedService;
    private FeeService feeService;
    private Preferences preferences;
    private BsqWalletService bsqWalletService;
    private WalletsSetup walletsSetup;


    private MarketPrice marketPrice;
    private boolean marketPriceAvailable;
    @Getter
    private MarketList marketList;
    @Getter
    private CurrencyList currencyList;


    public BisqProxy(BtcWalletService btcWalletService, TradeManager tradeManager, OpenOfferManager openOfferManager,
                     OfferBookService offerBookService, P2PService p2PService, KeyRing keyRing,
                     PriceFeedService priceFeedService, User user, FeeService feeService, Preferences preferences,
                     BsqWalletService bsqWalletService, WalletsSetup walletsSetup, ClosedTradableManager closedTradableManager,
                     FailedTradesManager failedTradesManager) {
        this.btcWalletService = btcWalletService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.offerBookService = offerBookService;
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.user = user;
        this.feeService = feeService;
        this.preferences = preferences;
        this.bsqWalletService = bsqWalletService;
        this.marketList = calculateMarketList();
        this.currencyList = calculateCurrencyList();
        this.walletsSetup = walletsSetup;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
    }

    protected CurrencyList calculateCurrencyList() {
        CurrencyList currencyList = new CurrencyList();
        CurrencyUtil.getAllSortedCryptoCurrencies().forEach(cryptoCurrency -> currencyList.add(cryptoCurrency.getCode(), cryptoCurrency.getName(), "crypto"));
        CurrencyUtil.getAllSortedFiatCurrencies().forEach(fiatCurrency -> currencyList.add(fiatCurrency.getCurrency().getSymbol(), fiatCurrency.getName(), "fiat"));
        Collections.sort(currencyList.currencies, (io.bisq.api.model.Currency p1, io.bisq.api.model.Currency p2) -> p1.name.compareTo(p2.name));
        return currencyList;
    }

    protected MarketList calculateMarketList() {
        MarketList marketList = new MarketList();
        CurrencyList currencyList = calculateCurrencyList(); // we calculate this twice but only at startup
        //currencyList.getCurrencies().stream().flatMap(currency -> marketList.getMarkets().forEach(currency1 -> cur))
        List<Market> btc = CurrencyUtil.getAllSortedCryptoCurrencies().stream().filter(cryptoCurrency -> !(cryptoCurrency.getCode().equals("BTC"))).map(cryptoCurrency -> new Market(cryptoCurrency.getCode(), "BTC")).collect(toList());
        marketList.markets.addAll(btc);
        btc = CurrencyUtil.getAllSortedFiatCurrencies().stream().map(cryptoCurrency -> new Market("BTC", cryptoCurrency.getCode())).collect(toList());
        marketList.markets.addAll(btc);
        Collections.sort(currencyList.currencies, (io.bisq.api.model.Currency p1, Currency p2) -> p1.name.compareTo(p2.name));
        return marketList;
    }


    private List<PaymentAccount> getPaymentAccountList() {
        return new ArrayList(user.getPaymentAccounts());
    }

    private PaymentAccount getPaymentAccount(String paymentAccountId) {
        return user.getPaymentAccount(paymentAccountId);
    }

    public AccountList getAccountList() {
        AccountList accountList = new AccountList();
        accountList.accounts = getPaymentAccountList().stream()
                .map(paymentAccount -> new Account(paymentAccount)).collect(Collectors.toSet());
        return accountList;
    }

    public Optional<BisqProxyError> offerCancel(String offerId) {
        if (Strings.isNullOrEmpty(offerId)) {
            BisqProxyError.getOptional("offerId is null");
        }
        Optional<OpenOffer> openOfferById = openOfferManager.getOpenOfferById(offerId);
        if (!openOfferById.isPresent()) {
            BisqProxyError.getOptional("Offer with id:" + offerId + " was not found.");
        }
        // do something more intelligent here, maybe block till handler is called.
        Platform.runLater(() -> openOfferManager.removeOpenOffer(openOfferById.get(), () -> log.info("offer removed"), (err) -> log.error("Error removing offer: " + err)));
        return Optional.empty();
    }

    public Optional<Offer> getOffer(String offerId) throws Exception {
        if (Strings.isNullOrEmpty(offerId)) {
            throw new Exception("OfferId is null");
        }
        Optional<Offer> offer = offerBookService.getOffers().stream().filter(offer1 -> offerId.equals(offer1.getId())).findAny();
        return offer;
    }

    /**
     * Return detail for a particular offerId.
     * @returns a tuple, with as first member an optional result, as second member an optional error.
     */
    public Tuple2<Optional<OfferDetail>, Optional<BisqProxyError>> getOfferDetail(String offerId) throws Exception {
        Optional<OfferDetail> result = Optional.empty();
        Optional<BisqProxyError> error = Optional.empty();

        Optional<Offer> offer = getOffer(offerId);
        if (!offer.isPresent()) {
            error = BisqProxyError.getOptional("OfferId not found");
        }
        result = Optional.of(new OfferDetail(offer.get()));

        return new Tuple2<>(result, error);
    }

    public List<OfferDetail> getOfferList() {
        List<OfferDetail> offer = offerBookService.getOffers().stream().map(offer1 -> new OfferDetail(offer1)).collect(toList());
        //List<OfferDetail> offer = openOfferManager.getObservableList().stream().map(offer1 -> new OfferDetail(offer1.getOffer())).collect(toList());
        return offer;

    }

    public Optional<BisqProxyError> offerMake(String accountId, OfferPayload.Direction direction, BigDecimal amount, BigDecimal minAmount,
                                              boolean useMarketBasedPrice, Double marketPriceMargin, String baseCurrencyCode, String counterCurrencyCode, String fiatPrice) {

        // exception from gui code is not clear enough, so this check is added. Missing money is another possible check but that's clear in the gui exception.
        if (user.getAcceptedArbitratorAddresses().size() == 0) {
            return BisqProxyError.getOptional("No arbitrator has been chosen");
        }

        // Checked that if fixed we have a fixed price, if percentage we have a percentage
        if (marketPriceMargin == null && useMarketBasedPrice) {
            return BisqProxyError.getOptional("When choosing PERCENTAGE price, fill in percentage_from_market_price");
        } else if ((Strings.isNullOrEmpty(fiatPrice) || "0".equals(fiatPrice) || Long.valueOf(fiatPrice) == 0) && !useMarketBasedPrice) {
            return BisqProxyError.getOptional("When choosing FIXED price, fill in fixed_price with a price > 0");
        }
        // check that the currency pairs are valid
        if (!checkValidMarket(baseCurrencyCode, counterCurrencyCode)) {
            return BisqProxyError.getOptional("There is no valid market pair: " + baseCurrencyCode.toLowerCase() + "_" + counterCurrencyCode.toLowerCase());
        }

        Optional<PaymentAccount> optionalAccount = getPaymentAccountList().stream()
                .filter(account1 -> account1.getId().equals(accountId)).findFirst();
        if (!optionalAccount.isPresent()) {
            // return an error
            String errorMessage = "Could not find payment account with id: " + accountId;
            log.error(errorMessage);
            return BisqProxyError.getOptional(errorMessage);
        }
        PaymentAccount paymentAccount = optionalAccount.get();

        try {

            // COPIED from CreateDataOfferModel: TODO refactor uit of GUI module  /////////////////////////////
            String countryCode = paymentAccount instanceof CountryBasedPaymentAccount ? ((CountryBasedPaymentAccount) paymentAccount).getCountry().code : null;
            ArrayList<String> acceptedCountryCodes = null;
            if (paymentAccount instanceof SepaAccount) {
                acceptedCountryCodes = new ArrayList<>();
                acceptedCountryCodes.addAll(((SepaAccount) paymentAccount).getAcceptedCountryCodes());
            } else if (paymentAccount instanceof CountryBasedPaymentAccount) {
                acceptedCountryCodes = new ArrayList<>();
                acceptedCountryCodes.add(((CountryBasedPaymentAccount) paymentAccount).getCountry().code);
            }
            String bankId = paymentAccount instanceof BankAccount ? ((BankAccount) paymentAccount).getBankId() : null;
            ArrayList<String> acceptedBanks = null;
            if (paymentAccount instanceof SpecificBanksAccount) {
                acceptedBanks = new ArrayList<>(((SpecificBanksAccount) paymentAccount).getAcceptedBanks());
            } else if (paymentAccount instanceof SameBankAccount) {
                acceptedBanks = new ArrayList<>();
                acceptedBanks.add(((SameBankAccount) paymentAccount).getBankId());
            }
            long maxTradeLimit = paymentAccount.getPaymentMethod().getMaxTradeLimitAsCoin(baseCurrencyCode).value;
            long maxTradePeriod = paymentAccount.getPaymentMethod().getMaxTradePeriod();
            boolean isPrivateOffer = false;
            boolean useAutoClose = false;
            boolean useReOpenAfterAutoClose = false;
            long lowerClosePrice = 0;
            long upperClosePrice = 0;
            String hashOfChallenge = null;
            HashMap<String, String> extraDataMap = null;

            // COPIED from CreateDataOfferModel /////////////////////////////

            updateMarketPriceAvailable(baseCurrencyCode);

            // TODO dummy values in this constructor !!!
            Coin coinAmount = Coin.valueOf(amount.longValueExact());
            OfferPayload offerPayload = new OfferPayload(
                    UUID.randomUUID().toString(),
                    new Date().getTime(),
                    p2PService.getAddress(),
                    keyRing.getPubKeyRing(),
                    direction,
                    Long.valueOf(fiatPrice),
                    marketPriceMargin,
                    useMarketBasedPrice,
                    amount.longValueExact(),
                    minAmount.longValueExact(),
                    baseCurrencyCode,
                    counterCurrencyCode,
                    (ArrayList<NodeAddress>) user.getAcceptedArbitratorAddresses(),
                    (ArrayList<NodeAddress>) user.getAcceptedMediatorAddresses(),
                    paymentAccount.getPaymentMethod().getId(),
                    paymentAccount.getId(),
                    null, // will be filled in by BroadcastMakerFeeTx class
                    countryCode,
                    acceptedCountryCodes,
                    bankId,
                    acceptedBanks,
                    Version.VERSION,
                    btcWalletService.getLastBlockSeenHeight(),
                    feeService.getTxFee(600).value, // default also used in code CreateOfferDataModel
                    getMakerFee(coinAmount, marketPriceMargin).value,
                    preferences.getPayFeeInBtc() || !isBsqForFeeAvailable(coinAmount, marketPriceMargin),
                    preferences.getBuyerSecurityDepositAsCoin().value,
                    Restrictions.getSellerSecurityDeposit().value,
                    maxTradeLimit,
                    maxTradePeriod,
                    useAutoClose,
                    useReOpenAfterAutoClose,
                    upperClosePrice,
                    lowerClosePrice,
                    isPrivateOffer,
                    hashOfChallenge,
                    extraDataMap,
                    Version.TRADE_PROTOCOL_VERSION
            );

            Offer offer = new Offer(offerPayload);
            offer.setPriceFeedService(priceFeedService);

            if (!PaymentAccountUtil.isPaymentAccountValidForOffer(offer, paymentAccount)) {
                return BisqProxyError.getOptional("PaymentAccount is not valid for offer");
            }

            // use countdownlatch to block this method until there's a success/error callback call
            CountDownLatch placeOfferLatch = new CountDownLatch(1);

            // TODO remove ugly workaround - probably implies refactoring the placeoffer code
            String[] errorResult = new String[1];

            checkNotNull(getMakerFee(false, Coin.valueOf(amount.longValue()), marketPriceMargin), "makerFee must not be null");

            Coin reservedFundsForOffer = OfferUtil.isBuyOffer(direction) ? preferences.getBuyerSecurityDepositAsCoin() : Restrictions.getSellerSecurityDeposit();
            if (!OfferUtil.isBuyOffer(direction))
                reservedFundsForOffer = reservedFundsForOffer.add(Coin.valueOf(amount.longValue()));

            openOfferManager.placeOffer(offer, reservedFundsForOffer,
                    true,
                    (transaction) -> {
                        log.info("Result is " + transaction);
                        errorResult[0] = "";
                        placeOfferLatch.countDown();
                    },
                    error -> {
                        placeOfferLatch.countDown();
                        errorResult[0] = error;
                    }
            );

            // wait X seconds for a result or timeout
            if (placeOfferLatch.await(5L, TimeUnit.SECONDS))
                if (errorResult[0] == "")
                    return Optional.empty();
                else
                    return BisqProxyError.getOptional("Error while placing offer:" + errorResult[0]);
            else
                return BisqProxyError.getOptional("Timeout exceeded, check the logs for errors."); // Timeout exceeded
        } catch (Throwable e) {
            return BisqProxyError.getOptional(e.getMessage(), e);
        }
    }




    /// START TODO refactor out of GUI module ////

    boolean isBsqForFeeAvailable(Coin amount, double marketPriceMargin) {
        return BisqEnvironment.isBaseCurrencySupportingBsq() &&
                getMakerFee(false, amount, marketPriceMargin) != null &&
                bsqWalletService.getAvailableBalance() != null &&
                getMakerFee(false, amount, marketPriceMargin) != null &&
                !bsqWalletService.getAvailableBalance().subtract(getMakerFee(false, amount, marketPriceMargin)).isNegative();
    }

    boolean isCurrencyForMakerFeeBtc(Coin amount, double marketPriceMargin) {
        return preferences.getPayFeeInBtc() || !isBsqForFeeAvailable(amount, marketPriceMargin);
    }

    private void updateMarketPriceAvailable(String baseCurrencyCode) {
        marketPrice = priceFeedService.getMarketPrice(baseCurrencyCode);
        marketPriceAvailable = (marketPrice != null);
    }

    @Nullable
    public Coin getMakerFee(Coin amount, double marketPriceMargin) {
        return getMakerFee(isCurrencyForMakerFeeBtc(amount, marketPriceMargin), amount, marketPriceMargin);
    }

    @Nullable
    Coin getMakerFee(boolean isCurrencyForMakerFeeBtc, Coin amount, double marketPriceMargin) {
        if (amount != null) {
            final Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getMakerFeePerBtc(isCurrencyForMakerFeeBtc), amount);
            double makerFeeAsDouble = (double) feePerBtc.value;
            if (marketPriceAvailable) {
                if (marketPriceMargin > 0)
                    makerFeeAsDouble = makerFeeAsDouble * Math.sqrt(marketPriceMargin * 100);
                else
                    makerFeeAsDouble = 0;
                // For BTC we round so min value change is 100 satoshi
                if (isCurrencyForMakerFeeBtc)
                    makerFeeAsDouble = MathUtils.roundDouble(makerFeeAsDouble / 100, 0) * 100;
            }

            return CoinUtil.maxCoin(Coin.valueOf(MathUtils.doubleToLong(makerFeeAsDouble)), FeeService.getMinMakerFee(isCurrencyForMakerFeeBtc));
        } else {
            return null;
        }
    }

    /// STOP TODO refactor out of GUI module ////

    ///////////////// START TODO REFACTOR OFFER TAKE DEPENDENCIES //////////////////////////


    /**
     * TakeOfferDataModel.initWithData(Offer) needs to be refactored for fee calculation etc.
     *
     * @param offerId
     * @param paymentAccountId
     * @param amount
     * @return
     * @throws Exception
     */
    public Optional<BisqProxyError> offerTake(String offerId, String paymentAccountId, String amount, boolean useSavingsWallet) {
        try {
            // check that the offerId is valid
            Optional<Offer> offerOptional = getOffer(offerId);
            if (!offerOptional.isPresent()) {
                return BisqProxyError.getOptional("Unknown offer id");
            }
            Offer offer = offerOptional.get();

            // check the paymentAccountId is valid
            PaymentAccount paymentAccount = getPaymentAccount(paymentAccountId);
            if (paymentAccount == null) {
                return BisqProxyError.getOptional("Unknown payment account id");
            }

            // check the paymentAccountId is compatible with the offer
            if (!PaymentAccountUtil.isPaymentAccountValidForOffer(offer, paymentAccount)) {
                return BisqProxyError.getOptional("PaymentAccount is not valid for offer");
            }

            // check the amount is within the range
            Coin coinAmount = Coin.valueOf(Long.valueOf(amount));
            //if(coinAmount.isLessThan(offer.getMinAmount()) || coinAmount.isGreaterThan(offer.getma)

            // workaround because TradeTask does not have an error handler to notify us that something went wrong
            if (btcWalletService.getAvailableBalance().isLessThan(coinAmount)) {
                return BisqProxyError.getOptional("Available balance " + btcWalletService.getAvailableBalance() + " is less than needed amount: " + coinAmount);
            }

            // check that the price is correct ??

            // check taker fee

            // check security deposit for BTC buyer
            // check security deposit for BTC seller

            Coin securityDeposit = offer.getDirection() == OfferPayload.Direction.SELL ?
                    offer.getBuyerSecurityDeposit() :
                    offer.getSellerSecurityDeposit();
            Coin txFeeFromFeeService = feeService.getTxFee(600);
            Coin fundsNeededForTrade = securityDeposit.add(txFeeFromFeeService).add(txFeeFromFeeService);

            Platform.runLater(() -> {
                tradeManager.onTakeOffer(coinAmount,
                        txFeeFromFeeService,
                        getTakerFee(coinAmount),
                        isCurrencyForTakerFeeBtc(coinAmount),
                        offer.getPrice().getValue(),
                        fundsNeededForTrade,
                        offer,
                        paymentAccount.getId(),
                        useSavingsWallet,
                        (trade) -> log.info("Trade offer taken, offer:{}, trade:{}", offer.getId(), trade.getId()),
                        errorMessage -> {
                            log.warn(errorMessage);
                        }
                );
            });
            return Optional.empty();
        } catch (Throwable e) {
            return BisqProxyError.getOptional(e.getMessage(), e);
        }
    }

    boolean isCurrencyForTakerFeeBtc(Coin amount) {
        return preferences.getPayFeeInBtc() || !isBsqForFeeAvailable(amount);
    }

    @Nullable
    Coin getTakerFee(Coin amount, boolean isCurrencyForTakerFeeBtc) {
        if (amount != null) {
            // TODO write unit test for that
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc), amount);
            return CoinUtil.maxCoin(feePerBtc, FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc));
        } else {
            return null;
        }
    }

    @Nullable
    public Coin getTakerFee(Coin amount) {
        return getTakerFee(amount, isCurrencyForTakerFeeBtc(amount));
    }


    boolean isBsqForFeeAvailable(Coin amount) {
        return BisqEnvironment.isBaseCurrencySupportingBsq() &&
                getTakerFee(amount, false) != null &&
                bsqWalletService.getAvailableBalance() != null &&
                getTakerFee(amount, false) != null &&
                !bsqWalletService.getAvailableBalance().subtract(getTakerFee(amount, false)).isNegative();
    }

    ///////////////// END OFFER TAKE DEPENDENCIES //////////////////////////

    public TradeList getTradeList() {
        TradeList tradeList = new TradeList();
        ObservableList<Trade> tradableList = tradeManager.getTradableList();
        tradeList.setTrades(tradableList == null || tradableList.size() == 0 ? Lists.newArrayList() : tradableList.sorted());
        return tradeList;
    }

    public Optional<Trade> getTrade(String tradeId) {
        return getTradeList().getTrade().stream().filter(trade -> trade.getId().equals(tradeId)).findAny();
    }

    public WalletDetails getWalletDetails() {
        if (!btcWalletService.isWalletReady()) {
            return null;
        }

        Coin availableBalance = btcWalletService.getAvailableBalance();
        Coin reservedBalance = updateReservedBalance();
        Coin lockedBalance = updateLockedBalance();
        return new WalletDetails(availableBalance.toPlainString(), reservedBalance.toPlainString(), lockedBalance.toPlainString());
    }

    // TODO copied from MainViewModel - refactor !
    private Coin updateLockedBalance() {
        Stream<Trade> lockedTrades = Stream.concat(closedTradableManager.getLockedTradesStream(), failedTradesManager.getLockedTradesStream());
        lockedTrades = Stream.concat(lockedTrades, tradeManager.getLockedTradesStream());
        Coin sum = Coin.valueOf(lockedTrades
                .mapToLong(trade -> {
                    final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(trade.getId(), AddressEntry.Context.MULTI_SIG);
                    if (addressEntryOptional.isPresent())
                        return addressEntryOptional.get().getCoinLockedInMultiSig().getValue();
                    else
                        return 0;
                })
                .sum());
        return sum;
    }

    // TODO
    private Coin updateReservedBalance() {
        Coin sum = Coin.valueOf(openOfferManager.getObservableList().stream()
                .map(openOffer -> {
                    final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(openOffer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);
                    if (addressEntryOptional.isPresent()) {
                        Address address = addressEntryOptional.get().getAddress();
                        return btcWalletService.getBalanceForAddress(address);
                    } else {
                        return null;
                    }
                })
                .filter(e -> e != null)
                .mapToLong(Coin::getValue)
                .sum());

        return sum;
    }


    public WalletTransactions getWalletTransactions(long start, long end, long limit) {
        boolean includeDeadTransactions = true;
        Set<Transaction> transactions = btcWalletService.getTransactions(includeDeadTransactions);

        WalletTransactions walletTransactions = new WalletTransactions();
        List<WalletTransaction> transactionList = walletTransactions.getTransactions();

        for (Transaction t : transactions) {
            transactionList.add(new WalletTransaction(t, walletsSetup.getBtcWallet()));
        }
        return walletTransactions;
    }

    public List<WalletAddress> getWalletAddresses() {
        return user.getPaymentAccounts().stream()
                .filter(paymentAccount -> paymentAccount instanceof CryptoCurrencyAccount)
                .map(paymentAccount -> (CryptoCurrencyAccount) paymentAccount)
                .map(paymentAccount -> new WalletAddress(((CryptoCurrencyAccount) paymentAccount).getId(), paymentAccount.getPaymentMethod().toString(), ((CryptoCurrencyAccount) paymentAccount).getAddress()))
                .collect(toList());
    }

    public Optional<BisqProxyError> paymentStarted(String tradeId) {
        try {
            Optional<Trade> tradeOpt = getTrade(tradeId);
            if (!tradeOpt.isPresent()) {
                return BisqProxyError.getOptional("Could not find trade id " + tradeId);
            }
            Trade trade = tradeOpt.get();

            if (!Trade.State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN.equals(trade.getState())) {
                return BisqProxyError.getOptional("Trade is not in the correct state to start payment: " + trade.getState());
            }
            TradeProtocol tradeProtocol = trade.getTradeProtocol();

            // use countdownlatch to block this method until there's a success/error callback call
            CountDownLatch startPaymentLatch = new CountDownLatch(1);
            // TODO remove ugly workaround - probably implies refactoring
            String[] errorResult = new String[1];

            // common resulthandler
            ResultHandler resultHandler = () -> {
                log.info("Fiat payment started.");
                errorResult[0] = "";
                startPaymentLatch.countDown();

            };
            // comon errorhandler
            ErrorMessageHandler errorResultHandler = (error) -> {
                log.error("Error onFiatPaymentStarted", error);
                startPaymentLatch.countDown();
                errorResult[0] = error;
            };

            Runnable buyerAsMakerStartFiatPayment = () -> {
                ((BuyerAsMakerProtocol) tradeProtocol).onFiatPaymentStarted(resultHandler, errorResultHandler);
            };

            Runnable buyerAsTakerStartFiatPayment = () -> {
                ((BuyerAsTakerProtocol) tradeProtocol).onFiatPaymentStarted(resultHandler, errorResultHandler);
            };

            Platform.runLater(trade instanceof BuyerAsMakerTrade ? buyerAsMakerStartFiatPayment : buyerAsTakerStartFiatPayment);
            // wait X seconds for a result or timeout
            if (startPaymentLatch.await(5L, TimeUnit.SECONDS))
                if (errorResult[0] == "")
                    return Optional.empty();
                else
                    return BisqProxyError.getOptional("Error while starting payment:" + errorResult[0]);
            else
                return BisqProxyError.getOptional("Timeout exceeded, check the logs for errors."); // Timeout exceeded
        } catch (Throwable e) {
            return BisqProxyError.getOptional(e.getMessage(), e);
        }
    }

    public Optional<BisqProxyError> paymentReceived(String tradeId) {
        try {
            Optional<Trade> tradeOpt = getTrade(tradeId);
            if (!tradeOpt.isPresent()) {
                return BisqProxyError.getOptional("Could not find trade id " + tradeId);
            }
            Trade trade = tradeOpt.get();

            if (!Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG.equals(trade.getState())) {
                return BisqProxyError.getOptional("Trade is not in the correct state to start payment: " + trade.getState());
            }
            TradeProtocol tradeProtocol = trade.getTradeProtocol();

            if(!(tradeProtocol instanceof SellerAsTakerProtocol || tradeProtocol instanceof SellerAsMakerProtocol)) {
                return BisqProxyError.getOptional("Trade is not in the correct state to start payment received: " + tradeProtocol.getClass().getSimpleName());
            }

            // use countdownlatch to block this method until there's a success/error callback call
            CountDownLatch startPaymentLatch = new CountDownLatch(1);
            // TODO remove ugly workaround - probably implies refactoring
            String[] errorResult = new String[1];

            // common resulthandler
            ResultHandler resultHandler = () -> {
                log.info("Fiat payment received.");
                errorResult[0] = "";
                startPaymentLatch.countDown();

            };
            // comon errorhandler
            ErrorMessageHandler errorResultHandler = (error) -> {
                log.error("Error onFiatPaymentReceived", error);
                startPaymentLatch.countDown();
                errorResult[0] = error;
            };

            Runnable sellerAsMakerStartFiatPayment = () -> {
                ((SellerAsMakerProtocol) tradeProtocol).onFiatPaymentReceived(resultHandler, errorResultHandler);
            };

            Runnable sellerAsTakerStartFiatPayment = () -> {
                ((SellerAsTakerProtocol) tradeProtocol).onFiatPaymentReceived(resultHandler, errorResultHandler);
            };

            Platform.runLater(trade instanceof SellerAsMakerTrade ? sellerAsMakerStartFiatPayment : sellerAsTakerStartFiatPayment);
            // wait X seconds for a result or timeout
            if (startPaymentLatch.await(5L, TimeUnit.SECONDS))
                if (errorResult[0] == "")
                    return Optional.empty();
                else
                    return BisqProxyError.getOptional("Error while executing payment received:" + errorResult[0]);
            else
                return BisqProxyError.getOptional("Timeout exceeded, check the logs for errors."); // Timeout exceeded
        } catch (Throwable e) {
            return BisqProxyError.getOptional(e.getMessage(), e);
        }
    }

    public boolean moveFundsToBisqWallet(String tradeId) {
        Optional<Trade> tradeOpt = getTrade(tradeId);
        if (!tradeOpt.isPresent())
            return false;
        Trade trade = tradeOpt.get();

        Platform.runLater(() -> {
            btcWalletService.swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT);
            // TODO do we need to handle this ui stuff? --> handleTradeCompleted();
            tradeManager.addTradeToClosedTrades(trade);
        });

        return true; // TODO better return value?
    }

    private boolean checkValidMarket(String lsymbol, String rsymbol) {
        return marketList.markets.contains(new Market(lsymbol, rsymbol));
    }
}
