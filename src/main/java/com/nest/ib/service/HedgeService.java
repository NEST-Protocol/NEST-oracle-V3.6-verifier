package com.nest.ib.service;

import java.math.BigDecimal;

/**
 * @author wll
 * @date 2021/1/13 19:32
 */
public interface HedgeService {

    /**
     * buy
     * @param tradingPair Exchange traded pair
     * @param amount Volume of transactions
     * @return
     */
    boolean makeBuyOrder(String tradingPair, BigDecimal amount, String tokenSymbol);

    /**
     * sell
     * @param tradingPair Exchange traded pair
     * @param amount Volume of transactions
     * @return
     */
    boolean makeSellOrder(String tradingPair, BigDecimal amount, String tokenSymbol);

    /**
     * Determining whether the hedging service is ready and whether APIKEY and APISECRET are filled in
     * @return
     */
    boolean isReady();

    boolean updateApi(String apiKey, String apiSecret) throws Exception;
}
