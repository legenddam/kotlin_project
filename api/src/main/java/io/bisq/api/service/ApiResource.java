package io.bisq.api.service;

import com.codahale.metrics.annotation.Timed;
import io.bisq.api.BitsquareProxy;
import io.bisq.api.api.*;
import io.bisq.core.offer.OfferPayload;
import io.swagger.annotations.Api;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Api(value = "api")
//@Path("/api/v1")
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ApiResource {
    private final String template;
    private final String defaultName;
    private final AtomicLong counter;
    private final CurrencyList currencyList;
    private final MarketList marketList;
    private final BitsquareProxy bitsquareProxy;
    // "0x7fffffff";
    private static final String STRING_END_INT_MAX_VALUE = "2147483647";

    public ApiResource(String template, String defaultName, BitsquareProxy bitsquareProxy) {
        this.template = template;
        this.defaultName = defaultName;
        this.counter = new AtomicLong();
        this.bitsquareProxy = bitsquareProxy;
        currencyList = bitsquareProxy.getCurrencyList();
        marketList = bitsquareProxy.getMarketList();
    }

    ///////////////// ACCOUNT ///////////////////////////

    @GET
    @Timed
    @Path("/account_list")
    public AccountList accountList() {
        return bitsquareProxy.getAccountList();
    }

    ///////////////// CURRENCY ///////////////////////////

    @GET
    @Timed
    @Path("/currency_list")
    public CurrencyList currencyList() {
        return currencyList;
    }

    ///////////////// MARKET ///////////////////////////

    /**
     * Markets
     * <p>
     * market_list
     * <p>
     * Returns list of all markets
     * <p>
     * Params
     * <p>
     * None
     * Example Return
     * <p>
     * [ { "pair": "dash_btc", "lsymbol": "DASH", "rsymbol": "BTC", }, ... ]
     */
    @GET
    @Timed
    @Path("/market_list")
    public MarketList marketList() {
        return marketList;
    }


    ///////////////// OFFER ///////////////////////////

    @DELETE
    @Timed
    @Path("/offer_cancel")
    public boolean offerCancel(@QueryParam("offer_id") String offerId) {
        return bitsquareProxy.offerCancel(offerId);

    }

    @GET
    @Timed
    @Path("/offer_detail")
    public OfferData offerDetail(@QueryParam("offer_id") String offerId) {
        Optional<OfferData> offerDetail = bitsquareProxy.getOfferDetail(offerId);
        if (offerDetail.isPresent()) {
            return offerDetail.get();
        }
        return null;
    }

    /**
     * param	type	desc	                        values	                                            default
     * market	string	filter by market		        | "all"	                                            all
     * status	string	filter by status		        "unfunded" | "live" | "done" | "cancelled" | "all"	all
     * whose	string	filter by offer creator		    "mine" | "notmine" | "all"	                        all
     * start	longint	find offers after start time. seconds since 1970.			                        0
     * end	    longint	find offers before end time. seconds since 1970.			                        9223372036854775807
     * limit	int	    max records to return			                                                    100
     */
    @GET
    @Timed
    @Path("/offer_list")
    public List<OfferData> offerList(@DefaultValue("all") @QueryParam("market") String market,
                                     @DefaultValue("all") @QueryParam("status") String status,
                                     @DefaultValue("all") @QueryParam("whose") String whose,
                                     @DefaultValue("0") @QueryParam("start") long start,
                                     @DefaultValue("9223372036854775807") @QueryParam("end") long end,
                                     @DefaultValue("100") @QueryParam("limit") int limit
    ) {
        return bitsquareProxy.getOfferList();
    }

    /**
     * param	    type	desc	                                                                        required	values	            default
     * market	    string	identifies the market this offer will be placed in	                            1
     * payment_account_id	string	identifies the account to which funds will be received once offer is executed.	1
     * direction	string	defines if this is an offer to buy or sell	                                    1	        sell | buy
     * amount	    real	amount to buy or sell, in terms of left side of market pair	                    1
     * min_amount	real	minimum amount to buy or sell, in terms of left side of market pair	            1
     * price_type	string	defines if this is a fixed offer or a percentage offset from present market price.		    fixed | percentage	fixed
     * price	    string	interpreted according to "price-type". Percentages should be expressed in
     * decimal form eg 1/2 of 1% = "0.005" and must be positive	                                            1
     */
    @GET
    @Timed
    @Path("/offer_make")
    public boolean offerMake(@QueryParam("market") String market,
                             @NotEmpty @QueryParam("payment_account_id") String accountId,
                             @NotNull @QueryParam("direction") OfferPayload.Direction direction,
                             @NotNull @QueryParam("amount") BigDecimal amount,
                             @NotNull @QueryParam("min_amount") BigDecimal minAmount,
                             @DefaultValue("fixed") @QueryParam("price_type") String fixed,
                             @DefaultValue("100") @QueryParam("price") String price) {
        return bitsquareProxy.offerMake(market, accountId, direction, amount, minAmount, false, 100, "XMR", price, "100");
    }

    /**
     * param	        type	desc	                                                required	values	default
     * offer_id	    string	Identifies the offer to accept	                        1
     * payment_account_id	string	Identifies the payment account to receive funds into	1
     * amount	    string	amount to spend	                                        1
     */
    @GET
    @Timed
    @Path("/offer_take")
    public void offerTake(@QueryParam("offer_id") String offerId,
                          @QueryParam("payment_account_id") String accountId,
                          @QueryParam("amount") String amount) {
        return;
    }


    ///////////////// TRADE ///////////////////////////


    ///////////////// WALLET ///////////////////////////


    /**
     * wallet_detail
     * <p>
     * Returns wallet info. balance, etc.
     * <p>
     * Params
     * <p>
     * None
     * Example Return
     * <p>
     * {
     * "balance": 0.5623,
     * // TBD
     * }
     */
    @GET
    @Timed
    @Path("/wallet_detail")
    public WalletDetails walletDetail() {
        return bitsquareProxy.getWalletDetails();
    }

    /**
     * param	type	desc	required	values	default
     * status	string	filter by wether each address has a non-zero balance or not		funded | unfunded | both	both
     * start	int	starting index, zero based			0
     * limit	int	max number of addresses to return.			100
     *
     * @return
     */
    @GET
    @Timed
    @Path("/wallet_addresses")
    public List<WalletAddress> walletAddresses(@DefaultValue("BOTH") @QueryParam("status") String status,
                                               @DefaultValue("0") @QueryParam("start") Integer start,
                                               @DefaultValue("100") @QueryParam("limit") Integer limit) {
        return bitsquareProxy.getWalletAddresses();
    }

    /**
     * wallet_tx_list
     * <p>
     * Returns list of wallet transactions according to criteria
     * <p>
     * Param	Type	Required?	Default	Description
     * start	timestamp	no	0	start of period
     * end	timestamp	no	INT_MAX	end of period
     * limit	int	no	100	maximum records to return.
     * Example Return
     * <p>
     * {
     * "amount": 1.3453,
     * "type": "send",
     * "address": "14w4mZx4b6JjtEd9BZPnLCSXzbHjKH3Pn3",
     * "time": <timestamp>,
     * "confirmations": 5
     * // TBD
     * }
     */
    @GET
    @Timed
    @Path("/wallet_tx_list")
    public WalletTransactions walletTransactionList(@DefaultValue("0") @QueryParam("start") Integer start,
                                                    @DefaultValue(STRING_END_INT_MAX_VALUE) @QueryParam("end") Integer end,
                                                    @DefaultValue("100") @QueryParam("start") Integer limit
    ) {
//        return bitsquareProxy.getWalletTransactions(start, end, limit);
        return null;
    }


}
