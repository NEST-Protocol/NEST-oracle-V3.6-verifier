package com.nest.ib.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author wll
 * @date 2020/12/28 15:48
 */
@Component
public class VerifyState {
    private static final Logger log = LoggerFactory.getLogger(VerifyState.class);

    /**
     * Verify status, off by default
     */
    private volatile boolean open;

    /**
     * Token validates the price deviation threshold:The default 1%
     */
    public volatile BigDecimal tokenBiteThreshold = new BigDecimal(0.01);

    /**
     * Ntoken validates the price deviation threshold:The default 1%
     */
    public volatile BigDecimal nTokenBiteThreshold = new BigDecimal(0.01);

    /**
     * Whether to validate the Token quotation
     */
    private volatile boolean biteToken;

    /**
     * Whether to validate the NToken quotation
     */
    private volatile boolean biteNtoken;

    /**
     * Whether the current token offer must have the post2 method
     */
    private volatile boolean mustPost2 = false;

    /**
     * Minimum quantity of each batch defrost quotation
     */
    private volatile int closeMinNum = 1;

    /**
     * Number of queries each time the contract quotation list is called: Default 50
     */
    private volatile BigInteger maxFindNum = new BigInteger("50");

    public boolean isMustPost2() {
        return mustPost2;
    }

    public void setMustPost2(boolean mustPost2) {
        this.mustPost2 = mustPost2;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isBiteToken() {
        return biteToken;
    }

    public void setBiteToken(boolean biteToken) {
        this.biteToken = biteToken;
    }

    public void close() {
        this.open = false;
        log.info("Verify closed");
    }

    public void open() {
        this.open = true;
        log.info("Verify enabled");
    }

    public boolean isBiteNtoken() {
        return biteNtoken;
    }

    public void setBiteNtoken(boolean biteNtoken) {
        this.biteNtoken = biteNtoken;
    }

    public int getCloseMinNum() {
        return closeMinNum;
    }

    public void setCloseMinNum(int closeMinNum) {
        this.closeMinNum = closeMinNum;
    }

    public BigInteger getMaxFindNum() {
        return maxFindNum;
    }

    public void setMaxFindNum(BigInteger maxFindNum) {
        this.maxFindNum = maxFindNum;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public BigDecimal getTokenBiteThreshold() {
        return tokenBiteThreshold;
    }

    public void setTokenBiteThreshold(BigDecimal tokenBiteThreshold) {
        this.tokenBiteThreshold = tokenBiteThreshold;
    }

    public BigDecimal getnTokenBiteThreshold() {
        return nTokenBiteThreshold;
    }

    public void setnTokenBiteThreshold(BigDecimal nTokenBiteThreshold) {
        this.nTokenBiteThreshold = nTokenBiteThreshold;
    }
}
