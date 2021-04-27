package com.nest.ib.utils;


import com.huobi.client.AccountClient;
import com.huobi.client.TradeClient;
import com.huobi.client.req.account.AccountBalanceRequest;
import com.huobi.client.req.trade.CreateOrderRequest;
import com.huobi.constant.HuobiOptions;
import com.huobi.model.account.AccountBalance;
import com.huobi.model.account.Balance;
import com.huobi.model.trade.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;

@Component
public class HuobiClient {

    private static final Logger log = LoggerFactory.getLogger(HuobiClient.class);

    public static BigDecimal getBalanceByToken(String accessKeyId, String screctKey, String tokenSymbol) {
        BigDecimal max = BigDecimal.ZERO;
        List<Balance> balances = listBalance(accessKeyId, screctKey);
        if (!CollectionUtils.isEmpty(balances)) {

            for (Balance balance : balances) {
                if (balance.getCurrency().equalsIgnoreCase(tokenSymbol)) {
                    if (balance.getBalance().compareTo(max) > 0) {
                        max = balance.getBalance();
                    }
                }
            }
            return max;
        }
        return max;
    }

    public static List<Balance> listBalance(String accessKeyId, String screctKey) {
        List<Balance> list = null;
        try {
            AccountClient accountClient = AccountClient.create(HuobiOptions.builder()
                    .apiKey(accessKeyId)
                    .secretKey(screctKey)
                    .build());
            Long accountId = accountClient.getAccounts().get(0).getId();

            AccountBalance accountBalance = accountClient.getAccountBalance(AccountBalanceRequest.builder()
                    .accountId(accountId)
                    .build());
            list = accountBalance.getList();
        } catch (Exception e) {
            log.error("listBalance error：{}", e.getMessage());
        }

        return list;
    }

    public static Order checkOrderStatus(long orderId, String accessKeyId, String screctKey) {
        Order order = null;
        try {
            TradeClient tradeService = TradeClient.create(HuobiOptions.builder()
                    .apiKey(accessKeyId)
                    .secretKey(screctKey)
                    .build());

            order = tradeService.getOrder(orderId);
        } catch (Exception e) {
            log.error("Check for abnormal order status：{}", e.getMessage());
            return order;
        }

        return order;
    }

    public static boolean cancelOrder(long orderId, String accessKeyId, String screctKey) {
        Order order = null;
        try {
            TradeClient tradeService = TradeClient.create(HuobiOptions.builder()
                    .apiKey(accessKeyId)
                    .secretKey(screctKey)
                    .build());

            long cancelResult = tradeService.cancelOrder(orderId);
            order = checkOrderStatus(orderId, accessKeyId, screctKey);
        } catch (Exception e) {
            log.error("Cancel order exception：{}", e.getMessage());
            return false;
        }
        if (order != null) {
            if ("canceled".equals(order.getState())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The market price
     *
     * @param accessKeyId
     * @param screctKey
     * @param symbol
     * @param amount
     * @param buy
     * @return
     */
    public static boolean makeOrderMarket(String accessKeyId,
                                          String screctKey,
                                          String symbol,
                                          BigDecimal amount,
                                          boolean buy) {
        Order order = null;
        try {
            TradeClient tradeService = TradeClient.create(HuobiOptions.builder()
                    .apiKey(accessKeyId)
                    .secretKey(screctKey)
                    .build());

            long accountId = getAccountId(accessKeyId, screctKey);

            CreateOrderRequest orderRequest = null;
            if (buy) {
                orderRequest = CreateOrderRequest.spotBuyMarket(accountId, symbol, amount);
            } else {
                orderRequest = CreateOrderRequest.spotSellMarket(accountId, symbol, amount);
            }

            long orderId = tradeService.createOrder(orderRequest);
            log.info("The order ID ：{}", orderId);
            Thread.sleep(3000);
            order = checkOrderStatus(orderId, accessKeyId, screctKey);
        } catch (Exception e) {
            log.error("Send order exception：{}", e.getMessage());
            return false;
        }

        if (order == null) return false;

        if (!"filled".equalsIgnoreCase(order.getState())) {
            return false;
        }

        return false;
    }

    public static long getAccountId(String accessKeyId, String screctKey) {
        AccountClient accountClient = AccountClient.create(HuobiOptions.builder()
                .apiKey(accessKeyId)
                .secretKey(screctKey)
                .build());

        return accountClient.getAccounts().get(0).getId();
    }
}
