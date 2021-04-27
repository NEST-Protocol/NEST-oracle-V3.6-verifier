package com.nest.ib.service.serviceImpl;

import com.huobi.client.AccountClient;
import com.huobi.client.req.account.AccountBalanceRequest;
import com.huobi.constant.HuobiOptions;
import com.huobi.model.account.AccountBalance;
import com.huobi.model.account.Balance;
import com.nest.ib.service.HedgeService;
import com.nest.ib.utils.HuobiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author wll
 * @date 2021/1/13 19:36
 */
@Service("huobiHedgeServiceIml")
public class HuobiHedgeServiceIml implements HedgeService {

    private static final Logger log = LoggerFactory.getLogger(HuobiHedgeServiceIml.class);

    private volatile String API_KEY = "";
    private volatile String API_SECRET = "";

    @Override
    public boolean makeBuyOrder(String tradingPair, BigDecimal amount, String tokenSymbol) {
        BigDecimal balanceByToken = HuobiClient.getBalanceByToken(API_KEY, API_SECRET, tokenSymbol);
        if (balanceByToken.compareTo(amount) < 0) return false;
        return HuobiClient.makeOrderMarket(API_KEY, API_SECRET, tradingPair, amount, true);
    }

    @Override
    public boolean makeSellOrder(String tradingPair, BigDecimal amount, String tokenSymbol) {
        BigDecimal balanceByToken = HuobiClient.getBalanceByToken(API_KEY, API_SECRET, tokenSymbol);
        if (balanceByToken.compareTo(amount) < 0) return false;
        return HuobiClient.makeOrderMarket(API_KEY, API_SECRET, tradingPair, amount, false);
    }

    @Override
    public boolean isReady() {
        if (StringUtils.isEmpty(API_KEY) || StringUtils.isEmpty(API_SECRET)) return false;
        return true;
    }

    @Override
    public boolean updateApi(String apiKey, String apiSecret) throws Exception {
        List<Balance> balances;
        try {
            AccountClient accountClient = AccountClient.create(HuobiOptions.builder()
                    .apiKey(apiKey)
                    .secretKey(apiSecret)
                    .build());
            Long accountId = accountClient.getAccounts().get(0).getId();

            AccountBalance accountBalance = accountClient.getAccountBalance(AccountBalanceRequest.builder()
                    .accountId(accountId)
                    .build());
            balances = accountBalance.getList();
        } catch (Exception e) {
            throw new Exception("The API KEY is unavailable：" + e.getMessage());
        }
        if (!CollectionUtils.isEmpty(balances)) {
            for (Balance balance : balances) {
                log.info(balance.getCurrency() + ":" + balance.getBalance());
            }
        }
        log.info("API key update completed");

        this.API_KEY = apiKey;
        this.API_SECRET = apiSecret;
        return true;
    }


}
