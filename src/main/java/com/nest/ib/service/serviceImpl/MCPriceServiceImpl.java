package com.nest.ib.service.serviceImpl;

import com.alibaba.fastjson.JSONArray;
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
 * @date 2020/12/29 11:19
 */
@Service
public class MCPriceServiceImpl implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(MCPriceServiceImpl.class);

    private static final String MOCHA_API1 = "https://www.mxcio.co/open/api/v2/market/ticker?api_key=mx0uum9iDSeWzSiXSj&symbol=";
    private static final String MOCHA_API2 = "https://www.mxc.ceo/open/api/v2/market/ticker?api_key=mx0uum9iDSeWzSiXSj&symbol=";
    private static final String MOCHA_API3 = "https://www.mxc.co/open/api/v2/market/ticker?api_key=mx0uum9iDSeWzSiXSj&symbol=";


    @Override
    public BigDecimal getPriceByTrandingPair(String tradingPair) {
        BigDecimal exchangePrice = getPrice(MOCHA_API1, tradingPair);
        if (exchangePrice == null) {
            // Try different API prices
            exchangePrice = getPrice(MOCHA_API2, tradingPair);
            if (exchangePrice == null) {
                exchangePrice = getPrice(MOCHA_API3, tradingPair);
                if (exchangePrice == null) {
                    log.error("{} Price acquisition failed",tradingPair);
                    return null;
                }
            }
        }
        return exchangePrice;
    }

    private BigDecimal getPrice(String api, String tradingPair) {

        String s = null;
        try {
            s = HttpClientUtil.sendHttpGet(api + tradingPair.toUpperCase());
        } catch (Exception e) {
            log.error("Matcha interface call failed：{}", e.getMessage());
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

        BigDecimal n = new BigDecimal("0");
        if (data.size() == 0) {
            return null;
        }

        JSONObject jsonObject2 = JSONObject.parseObject(String.valueOf(data.get(0)));
        BigDecimal price = jsonObject2.getBigDecimal("last");

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        if (tradingPair.toLowerCase().endsWith("usdt") && !tradingPair.toLowerCase().contains("eth")) {
            BigDecimal ethUsdtPrice = getPriceByTrandingPair("ETH_USDT");
            if (ethUsdtPrice == null) {
                return null;
            }
            BigDecimal etherc20 = ethUsdtPrice.divide(price, 10, RoundingMode.HALF_UP);
            return etherc20;
        }

        return price;
    }


}
