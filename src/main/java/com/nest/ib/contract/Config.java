package com.nest.ib.contract;

import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.generated.Uint8;

import java.math.BigInteger;

public class Config extends StaticStruct {

    public Config(BigInteger postEthUnit,
                  BigInteger postFeeUnit,
                  BigInteger minerNestReward,
                  BigInteger minerNTokenReward,
                  BigInteger doublePostThreshold,
                  BigInteger ntokenMinedBlockLimit,
                  BigInteger maxBiteNestedLevel,
                  BigInteger priceEffectSpan,
                  BigInteger nestPledgeNest) {
        super(new Uint32(postEthUnit),
                new Uint16(postFeeUnit),
                new Uint16(minerNestReward),
                new Uint16(minerNTokenReward),
                new Uint32(doublePostThreshold),
                new Uint16(ntokenMinedBlockLimit),
                new Uint8(maxBiteNestedLevel),
                new Uint16(priceEffectSpan),
                new Uint16(nestPledgeNest));

        this.miningEthUnit = postEthUnit;
        this.nestStakedNum1k = nestPledgeNest;
        this.miningFee = postFeeUnit;
        this.priceDurationBlock = priceEffectSpan;
        this.maxBiteNestedLevel = maxBiteNestedLevel;
        this.minerNestReward = minerNestReward;
        this.minerNTokenReward = minerNTokenReward;

    }

    public Config(Uint32 postEthUnit,
                  Uint16 postFeeUnit,
                  Uint16 minerNestReward,
                  Uint16 minerNTokenReward,
                  Uint32 doublePostThreshold,
                  Uint16 ntokenMinedBlockLimit,
                  Uint8 maxBiteNestedLevel,
                  Uint16 priceEffectSpan,
                  Uint16 nestPledgeNest) {
        super(postEthUnit,
                postFeeUnit,
                minerNestReward,
                minerNTokenReward,
                doublePostThreshold,
                ntokenMinedBlockLimit,
                maxBiteNestedLevel,
                priceEffectSpan,
                nestPledgeNest);

        this.miningEthUnit = postEthUnit.getValue();
        this.nestStakedNum1k = nestPledgeNest.getValue();
        this.miningFee = postFeeUnit.getValue();
        this.priceDurationBlock = priceEffectSpan.getValue();
        this.maxBiteNestedLevel = maxBiteNestedLevel.getValue();
        this.minerNestReward = minerNestReward.getValue();
        this.minerNTokenReward = minerNTokenReward.getValue();
    }

    // Base quotation size
    public BigInteger miningEthUnit;
    // Freezing the number of NEST related factors
    public BigInteger nestStakedNum1k;
    //  Mining takes a percentage
    public BigInteger miningFee;
    // The number of blocks in the price stability zone
    public BigInteger priceDurationBlock;
    // This parameter is used to divide the amount of money that needs to be frozen when eating the order
    public BigInteger maxBiteNestedLevel;
    // Percentage of miners who reach nest (ten thousand point scale). 8000
    public BigInteger minerNestReward;// MINER_NEST_REWARD_PERCENTAGE
    // The percentage of Ntokens mined by miners is only valid for Ntokens created with version 3.0 (ten thousand). 9500
    public BigInteger minerNTokenReward;

    public BigInteger getMiningEthUnit() {
        return miningEthUnit;
    }

    public void setMiningEthUnit(BigInteger miningEthUnit) {
        this.miningEthUnit = miningEthUnit;
    }

    public BigInteger getNestStakedNum1k() {
        return nestStakedNum1k;
    }

    public void setNestStakedNum1k(BigInteger nestStakedNum1k) {
        this.nestStakedNum1k = nestStakedNum1k;
    }

    public BigInteger getMiningFee() {
        return miningFee;
    }

    public void setMiningFee(BigInteger miningFee) {
        this.miningFee = miningFee;
    }

    public BigInteger getPriceDurationBlock() {
        return priceDurationBlock;
    }

    public void setPriceDurationBlock(BigInteger priceDurationBlock) {
        this.priceDurationBlock = priceDurationBlock;
    }

    public BigInteger getMaxBiteNestedLevel() {
        return maxBiteNestedLevel;
    }

    public void setMaxBiteNestedLevel(BigInteger maxBiteNestedLevel) {
        this.maxBiteNestedLevel = maxBiteNestedLevel;
    }

    public BigInteger getMinerNestReward() {
        return minerNestReward;
    }

    public void setMinerNestReward(BigInteger minerNestReward) {
        this.minerNestReward = minerNestReward;
    }

    public BigInteger getMinerNTokenReward() {
        return minerNTokenReward;
    }

    public void setMinerNTokenReward(BigInteger minerNTokenReward) {
        this.minerNTokenReward = minerNTokenReward;
    }
}
