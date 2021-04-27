package com.nest.ib.helper;

import com.nest.ib.constant.ExchangeType;
import com.nest.ib.service.PriceService;
import com.nest.ib.service.serviceImpl.BianPriceServiceImpl;
import com.nest.ib.service.serviceImpl.HbtcPriceServiceImpl;
import com.nest.ib.service.serviceImpl.HuobiPriceServiceImpl;
import com.nest.ib.service.serviceImpl.MCPriceServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author wll
 * @date 2020/12/31 16:12
 */
@Component
public class ExchangeHelper implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ExchangeHelper.class);

    @Autowired
    HuobiPriceServiceImpl huobiPriceService;
    @Autowired
    HbtcPriceServiceImpl hbtcPriceService;
    @Autowired
    MCPriceServiceImpl mcPriceService;
    @Autowired
    BianPriceServiceImpl bianPriceService;

    // Exchange price query interface MAP
    private static Map<String, PriceService> EXCHANGE_MAP = new HashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {

        EXCHANGE_MAP.put(ExchangeType.HUOBI, huobiPriceService);

        EXCHANGE_MAP.put(ExchangeType.HBTC, hbtcPriceService);

        EXCHANGE_MAP.put(ExchangeType.MC, mcPriceService);

        EXCHANGE_MAP.put(ExchangeType.BIAN, bianPriceService);
    }

    /**
     * Gets the exchange price query interface service
     *
     * @param exchangeType
     * @return
     */
    public static PriceService getExchangeService(String exchangeType) {
        PriceService priceService = EXCHANGE_MAP.get(exchangeType);
        if (priceService == null) {
            throw new RuntimeException("Unsupported type of exchange");
        }
        return priceService;
    }
}
