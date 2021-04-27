package com.nest.ib.service.serviceImpl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nest.ib.service.PriceService;
import com.nest.ib.utils.HttpClientUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author wll
 * @date 2020/12/28 14:09
 */
@Service
public class HuobiPriceServiceImpl implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(HuobiPriceServiceImpl.class);

    private static final String HUOBI_API = "https://api.huobi.pro/market/history/trade?size=1&symbol=";

    @Autowired
    BianPriceServiceImpl bianPriceService;
    @Autowired
    HbtcPriceServiceImpl hbtcPriceService;

    @Override
    public BigDecimal getPriceByTrandingPair(String tradingPair) {

        String s = null;
        try {
            s = HttpClientUtil.sendHttpGet(HUOBI_API + tradingPair.toLowerCase());
        } catch (Exception e) {
            log.error("Huobi price query interface invocation failed:{}", e);

            // If the price is quoted by ETHUSDT, then the price is obtained from Binance
            if (tradingPair.equalsIgnoreCase("ethusdt")) {
                return bianPriceService.getPriceByTrandingPair(tradingPair);
            } else if (tradingPair.equalsIgnoreCase("nesteth")) {
                // If it's Nesteth, it gets the price from Hobbit
                return hbtcPriceService.getPriceByTrandingPair(tradingPair);
            }
            return null;
        }

        if (s == null) {
            return null;
        }
        JSONObject jsonObject = JSONObject.parseObject(s);
        JSONArray data = jsonObject.getJSONArray("data");
        if (data == null) {
            return null;
        }

        BigDecimal totalPrice = new BigDecimal("0");
        BigDecimal n = new BigDecimal("0");
        if (data.size() == 0) {
            return null;
        }

        for (int i = 0; i < data.size(); i++) {
            Object o = data.get(i);
            JSONObject jsonObject1 = JSONObject.parseObject(String.valueOf(o));
            JSONArray data1 = jsonObject1.getJSONArray("data");
            if (data1 == null) {
                continue;
            }
            if (data1.size() == 0) {
                continue;
            }
            JSONObject jsonObject2 = JSONObject.parseObject(String.valueOf(data1.get(0)));
            BigDecimal price = jsonObject2.getBigDecimal("price");
            if (price == null) {
                continue;
            }
            totalPrice = totalPrice.add(price);
            n = n.add(new BigDecimal("1"));
        }

        BigDecimal price = null;
        if (n.compareTo(new BigDecimal("0")) > 0) {
            price = totalPrice.divide(n, 18, BigDecimal.ROUND_DOWN);
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (tradingPair.toLowerCase().endsWith("usdt") && !tradingPair.toLowerCase().contains("eth")) {
            BigDecimal ethUsdtPrice = getPriceByTrandingPair("ethusdt");
            if (ethUsdtPrice == null) {
                return null;
            }
            BigDecimal etherc20 = ethUsdtPrice.divide(price, 10, RoundingMode.HALF_UP);
            return etherc20;
        }
        return price;
    }
}
