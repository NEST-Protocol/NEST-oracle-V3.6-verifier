package com.nest.ib.service.serviceImpl;

import com.alibaba.fastjson.JSONObject;
import com.nest.ib.service.PriceService;
import com.nest.ib.utils.HttpClientUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author wll
 * @date 2020/12/28 14:16
 */
@Service
public class HbtcPriceServiceImpl implements PriceService {
    private static final Logger log = LoggerFactory.getLogger(HbtcPriceServiceImpl.class);

    private static final String HBTC_API = "https://api.hbtc.com/openapi/quote/v1/ticker/price?symbol=";

    @Override
    public BigDecimal getPriceByTrandingPair(String tradingPair) {
        String s = null;
        try {
            s = HttpClientUtil.sendHttpGet(HBTC_API + tradingPair.toUpperCase());
        } catch (Exception e) {
            log.error("The hbtc price query interface call failed:{}", e);
            return null;
        }

        if (s == null) {
            return null;
        }

        JSONObject jsonObject = JSONObject.parseObject(s);
        BigDecimal price = jsonObject.getBigDecimal("price");

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // The price of ERC20USDT needs to be converted to the price of ETHERC20
        if (tradingPair.toLowerCase().endsWith("usdt") && !tradingPair.toLowerCase().contains("eth")) {
            BigDecimal ethUsdtPrice = getPriceByTrandingPair("ETHUSDT");
            if (ethUsdtPrice == null) {
                return null;
            }
            BigDecimal etherc20 = ethUsdtPrice.divide(price, 10, RoundingMode.HALF_UP);
            return etherc20;
        }

        return price;
    }
}
