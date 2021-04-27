package com.nest.ib.service;

import java.math.BigDecimal;

/**
 * @author wll
 * @date 2020/12/28 14:07
 * Exchange price acquisition
 */
public interface PriceService {

    /**
     * Get the corresponding price based on the transaction pair
     * @return
     */
    BigDecimal getPriceByTrandingPair(String tradingPair);
}
