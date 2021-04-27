package com.nest.ib.constant;


import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author wll
 * @date 2020/7/16 13:22
 */
public interface Constant {

    BigInteger PACKAGING_COSTS = new BigInteger("200000000000000000");

    BigDecimal UNIT_ETH = new BigDecimal("1000000000000000000");

    BigDecimal UNIT_DEC18 = new BigDecimal("1000000000000000000");

    BigInteger UNIT_INT18 = new BigInteger("1000000000000000000");

    BigInteger BIG_INTEGER_1K = BigInteger.valueOf(1000);

    BigDecimal BIG_DECIMAL_1K = BigDecimal.valueOf(1000);

    BigInteger BIG_INTEGER_100 = BigInteger.valueOf(100);

    BigInteger BIG_INTEGER_200K = BigInteger.valueOf(200000);

    BigInteger BIG_INTEGER_TWO = BigInteger.valueOf(2);

}
