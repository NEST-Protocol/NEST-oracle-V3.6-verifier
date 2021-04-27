package com.nest.ib.state;

import com.nest.ib.contract.ContractBuilder;
import com.nest.ib.contract.ERC20;
import com.nest.ib.contract.NTokenControllerContract;
import com.nest.ib.helper.ExchangeHelper;
import com.nest.ib.helper.Web3jHelper;
import com.nest.ib.service.PriceService;
import com.nest.ib.utils.EthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author wll
 * @date 2020/12/28 11:39
 */
@Component
public class Erc20State {

    private static final Logger log = LoggerFactory.getLogger(Erc20State.class);

    private static final int MAX_SIZE = 50;

    @Autowired
    private EthClient ethClient;
    @Autowired
    private VerifyState verifyState;

    /**
     * Token information, such as USDT, YFI, etc
     */
    public volatile Item token = new Item();

    /**
     * The corresponding NToken information, such as NEST, NHBTC, etc
     */
    public volatile Item nToken = new Item();


    public static class Item {

        /**
         * Tokens address
         */
        private volatile String address;

        /**
         * Type of exchange
         */
        private String exchangeType;

        /**
         * Exchange traded pair
         */
        private volatile String tradingPair;

        private volatile String symbol = "";

        private volatile Integer decimals;

        /**
         * 10 to the power of decimals
         */
        private volatile BigDecimal decPowTen;

        /**
         * Current exchange prices include: ETHNEST, ETHUSDT, etc
         */
        private volatile BigDecimal ethErc20Price;

        /**
         * Whether to open a hedge
         */
        private boolean hedge = false;

        /**
         * The index of the quotation that has been validated
         */
        private LinkedBlockingDeque<BigInteger> biteIndex = new LinkedBlockingDeque<>();

        public void addBiteIndex(BigInteger index) {
            log.info("{} validation index: {}", symbol, index);
            if (biteIndex.size() > MAX_SIZE) {
                biteIndex.poll();
            }
            biteIndex.offer(index);
        }

        public boolean haveEaten(BigInteger index) {
            return biteIndex.contains(index);
        }

        public boolean isHedge() {
            return hedge;
        }

        public void setHedge(boolean hedge) {
            this.hedge = hedge;
        }

        public String getExchangeType() {
            return exchangeType;
        }

        public void setExchangeType(String exchangeType) {
            this.exchangeType = exchangeType;
        }

        public Integer getDecimals() {
            return decimals;
        }

        public void setDecimals(Integer decimals) {
            this.decimals = decimals;
            log.info("****{} decimals update：{}****", symbol, decimals);
        }

        public BigDecimal getDecPowTen() {
            return decPowTen;
        }

        public void setDecPowTen(BigDecimal decPowTen) {
            this.decPowTen = decPowTen;
            log.info("****{} decPowTen update：{}****", symbol, decPowTen);
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
            log.info("****{} address update：{}****", symbol, address);
        }

        public String getTradingPair() {
            return tradingPair;
        }

        public void setTradingPair(String tradingPair) {
            this.tradingPair = tradingPair;
            log.info("****{} tradingPair update：{}****", symbol, tradingPair);
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
            log.info("**** symbol update：{}****", symbol);
        }

        public BigDecimal getEthErc20Price() {
            return ethErc20Price;
        }

        public void setEthErc20Price(BigDecimal ethErc20Price) {
            this.ethErc20Price = ethErc20Price;
        }
    }


    /**
     * Update token/ntoken information
     *
     * @param tokenAddress       Quote Token address, such as USDT, YFI address
     * @param tokenExchangeType
     * @param nTokenExchangeType
     * @param tokenTradingPair   Token exchanges trade on pairs:
     *                           1. Huobi trading pairs: ETHUSDT, BTCETH, YFIETH, etc
     *                           2、Mc  trading pairs: COFI_USDT
     * @param nTokenTradingPair  Ntoken trading pairs can be empty,post2 cannot be empty:
     *                           1. Huobi trading pair: NHBTCETH
     *                           2、Hbtc trading pair：NYFIUSDT
     * @param nTokenfixedPrice   Ntoken fixed price
     * @return
     */
    public boolean updateErc20State(String tokenAddress,
                                    String tokenExchangeType,
                                    String nTokenExchangeType,
                                    String tokenTradingPair,
                                    String nTokenTradingPair,
                                    BigDecimal nTokenfixedPrice) throws Exception {

        try {
            // Gets and configures token information
            token.ethErc20Price = null;
            updateErc20BaseState(token, tokenAddress, tokenExchangeType, tokenTradingPair);
        } catch (Exception e) {
            log.error("Failed to obtain token information. Unable to quote. Please check whether the token address is correct or the node is normal:{}", e.getMessage());
            throw new Exception("Failed to obtain token information. Unable to quote. Please check whether the token address is correct or the node is normal");
        }

        try {
            // Gets the NToken address
            NTokenControllerContract nTokenControllerContract = ContractBuilder.nTokenControllerContract(Web3jHelper.getWeb3j());
            String nTokenAddress = nTokenControllerContract.getNTokenAddress(tokenAddress).send();

            // Get and configure the NToken information
            nToken.ethErc20Price = null;
            updateErc20BaseState(nToken, nTokenAddress, nTokenExchangeType, nTokenTradingPair);
        } catch (Exception e) {
            log.error("Failed to get NToken token information. Unable to quote. Please check whether the token address is correct or the node is normal:{}", e.getMessage());
            throw new Exception("Failed to get NToken token information. Unable to quote. Please check whether the token address is correct or the node is normal");
        }

        // Check whether the transaction is correct
        String newTokenTrandingPair = tokenTradingPair;
        if (tokenTradingPair.toLowerCase().endsWith("usdt") && !tokenTradingPair.toLowerCase().contains("eth")) {
            newTokenTrandingPair = "ETH" + token.getSymbol();
        }
        try {
            PriceService tokenPriceService = ExchangeHelper.getExchangeService(tokenExchangeType);
            BigDecimal priceByTrandingPair = tokenPriceService.getPriceByTrandingPair(tokenTradingPair);
            log.info("***{}  price：{}***", newTokenTrandingPair, priceByTrandingPair);
            if (priceByTrandingPair == null) {
                return false;
            }
        } catch (Exception e) {
            log.error("{} Price acquisition failed：{}", newTokenTrandingPair, e.getMessage());
            throw new Exception(newTokenTrandingPair + "The price cannot be obtained, please check if the transaction is correct");
        }

        if (!StringUtils.isEmpty(nTokenExchangeType)
                && !StringUtils.isEmpty(nTokenTradingPair)
                && !nTokenExchangeType.equalsIgnoreCase("fixed")) {
            String newNtokenTrandingPair = nTokenTradingPair;
            if (nTokenTradingPair.toLowerCase().endsWith("usdt") && !nTokenTradingPair.toLowerCase().contains("eth")) {
                newNtokenTrandingPair = "ETH" + nToken.getSymbol();
            }

            try {
                PriceService priceService = ExchangeHelper.getExchangeService(nTokenExchangeType);
                BigDecimal priceByTrandingPair = priceService.getPriceByTrandingPair(nTokenTradingPair);
                log.info("***{} price：{}***", newNtokenTrandingPair, priceByTrandingPair);
                if (priceByTrandingPair == null) {
                    return false;
                }
            } catch (Exception e) {
                log.error("{} Price acquisition failed：{}", newNtokenTrandingPair, e.getMessage());
                throw new Exception(newNtokenTrandingPair + " The price cannot be obtained, please check if the transaction is correct");
            }
        }

        if ("fixed".equals(nTokenExchangeType)) {
            nToken.setEthErc20Price(nTokenfixedPrice);
        }

        // Check the Ntoken totalSupply
        if (ethClient.needOpenPost2()) {
            verifyState.setMustPost2(true);
        } else {
            verifyState.setMustPost2(false);
        }

        return true;
    }

    private void updateErc20BaseState(Item item, String tokenAddress, String exchangeType, String tradingPair) throws Exception {
        ERC20 erc20 = ContractBuilder.erc20Readonly(tokenAddress, Web3jHelper.getWeb3j());

        String symbol = erc20.symbol().send();
        if (StringUtils.isEmpty(symbol)) symbol = erc20.name().send();
        item.setSymbol(symbol);

        int decimals = erc20.decimals().send().intValue();
        item.setDecimals(decimals);

        long decPowTen = (long) Math.pow(10, decimals);
        item.setDecPowTen(new BigDecimal(decPowTen));

        item.setAddress(tokenAddress);
        item.setTradingPair(tradingPair);
        item.setExchangeType(exchangeType);

    }

    /**
     * Update the ETH/Token price
     *
     * @return
     */
    public boolean updateEthTokenPrice() {
        return updateEthErc20Price(token);
    }

    /**
     * Update the ETH/NToken price
     *
     * @return
     */
    public boolean updateEthNtokenPrice() {
        return updateEthErc20Price(nToken);
    }

    /**
     * Update Etherc20 prices
     */
    public boolean updateEthErc20Price(Item erc20) {

        if (erc20.decimals == null) {
            log.error("{} Token DECIMAL has not been initialized", erc20.symbol);
            return false;
        }

        if (StringUtils.isEmpty(erc20.exchangeType)) {
            log.error("{} The exchange type is not configured. The price cannot be obtained", erc20.symbol);
            return false;
        }

        // Fixed price
        if (erc20.exchangeType.equals("fixed")) {
            return true;
        }

        BigDecimal price = ExchangeHelper.getExchangeService(erc20.exchangeType).getPriceByTrandingPair(erc20.tradingPair);

        if (price == null) {
            log.error("{} Price acquisition failed. Trading pair price set to NULL. Quote trading has been suspended", erc20.tradingPair);
            erc20.ethErc20Price = null;
            return false;
        }

        // It needs to be handled differently depending on the transaction pair. If it ends in ETH, it will be converted to ETHXXX
        if (erc20.tradingPair.toLowerCase().endsWith("eth")) {
            price = BigDecimal.ONE.divide(price, erc20.decimals, BigDecimal.ROUND_DOWN);
        }

        // Set the precision
        price = price.setScale(erc20.decimals - 3, RoundingMode.HALF_UP);

        // Update the price
        erc20.ethErc20Price = price;
        log.info("Update ETH{} price: {}", erc20.symbol, price);
        return true;
    }
}
