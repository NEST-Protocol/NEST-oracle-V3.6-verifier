package com.nest.ib.service.serviceImpl;

import com.nest.ib.config.NestProperties;
import com.nest.ib.constant.Constant;
import com.nest.ib.contract.PriceSheetView;
import com.nest.ib.helper.WalletHelper;
import com.nest.ib.state.GasPriceState;
import com.nest.ib.utils.EthClient;
import com.nest.ib.model.*;
import com.nest.ib.service.*;
import com.nest.ib.state.Erc20State;
import com.nest.ib.state.VerifyState;
import com.nest.ib.utils.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

@Service
public class BiteServiceImpl implements BiteService {
    private static final Logger log = LoggerFactory.getLogger(BiteServiceImpl.class);

    private ExecutorService executorService = new ThreadPoolExecutor(5, 5,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(5));

    private static final String BITE_ETH = "takeEth";
    private static final String BITE_TOKEN = "takeToken";

    @Autowired
    private EthClient ethClient;
    @Autowired
    private Erc20State erc20State;
    @Autowired
    private VerifyState verifyState;
    @Autowired
    private WalletHelper walletHelper;
    @Autowired
    private GasPriceState gasPriceState;
    @Autowired
    private NestProperties nestProperties;

    @Autowired
    @Qualifier("huobiHedgeServiceIml")
    private HedgeService hedgeService;

    // Batch orders, account balance judgment need to subtract each order after the transaction sent out, here assume that each order after the transaction is successful
    private volatile BigInteger ethUsed = BigInteger.ZERO;// Total usage of ETH
    private volatile BigInteger accountEthUsed = BigInteger.ZERO;// Account ETH usage amount
    private volatile BigInteger closeEthUsed = BigInteger.ZERO;// Defrosted ETH usage
    private volatile BigInteger tokenUsed = BigInteger.ZERO;
    private volatile BigInteger nTokenUsed = BigInteger.ZERO;
    private volatile BigInteger nestUsed = BigInteger.ZERO;

    // Current wallet account assets
    private Wallet.Asset account = null;
    // Current wallet unfreezing assets
    private Wallet.Asset closed = null;
    // Current wallet assets available
    private Wallet.Asset useable = null;

    @Override
    public void bite(Wallet wallet) {
        // Check that validation is enabled
        if (!verifyState.isOpen()) {
            log.info("Validation is not enabled");
            return;
        }

        // Empty the assets of the last round of statistics
        clearUsed();

        String address = wallet.getCredentials().getAddress();
        BigInteger nonce = ethClient.ethGetTransactionCount(address);
        if (nonce == null) {
            log.error("Failed to get nonce during validation");
            return;
        }

        List<PriceSheetView> tokenSheetPubList = null;
        List<PriceSheetView> nTokenSheetPubList = null;

        if (verifyState.isBiteToken()) {
            // Gets the token quotation that is awaiting validation
            tokenSheetPubList = ethClient.unVerifiedSheetList(erc20State.token.getAddress());
        }

        if (verifyState.isBiteNtoken()) {
            // Gets the quotation of the NToken awaiting validation
            nTokenSheetPubList = ethClient.unVerifiedSheetList(erc20State.nToken.getAddress());
        }

        if (CollectionUtils.isEmpty(tokenSheetPubList) && CollectionUtils.isEmpty(nTokenSheetPubList)) {
            log.info("No quotation pending verification");
            return;
        }

        // Get the current block number and verify again that the validation period has expired
        BigInteger nowBlockNumber = ethClient.ethBlockNumber();
        if (nowBlockNumber == null) {
            log.info("Failed to get the latest block number during validation");
            return;
        }

        // Update the balance
        if (!walletHelper.updateBalance(wallet, false)) return;
        // Current Available Assets
        if (!fillAsset(wallet)) return;

        boolean bite = false;
        // Perform token quotation verification
        if (!CollectionUtils.isEmpty(tokenSheetPubList)) {
            Erc20State.Item erc20 = erc20State.token;
            bite = bite(wallet, nonce, tokenSheetPubList, nowBlockNumber, erc20, verifyState.getTokenBiteThreshold());
        }

        // Verify the NToken quotation
        if (!CollectionUtils.isEmpty(nTokenSheetPubList)) {
            Erc20State.Item erc20 = erc20State.nToken;
            bite = bite(wallet, nonce, nTokenSheetPubList, nowBlockNumber, erc20, verifyState.getnTokenBiteThreshold());
        }

        if (bite) {
            // An order eating transaction is initiated, where it sleeps and waits for the transaction to be packaged before the next round of validation
            try {
                Thread.sleep(60 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private boolean bite(Wallet wallet,
                         BigInteger nonce,
                         List<PriceSheetView> sheetPubList,
                         BigInteger nowBlockNumber,
                         Erc20State.Item erc20,
                         BigDecimal biteRate) {
        boolean bite = false;
        for (PriceSheetView priceSheetPub : sheetPubList) {
            if (erc20.haveEaten(priceSheetPub.index)) {
                log.info("[{}]:This quotation has been verified in the last round", priceSheetPub.index);
                continue;
            }

            // Based on remainNum, how many ETH/TOKEN remainsto be eaten, and once it's eaten, it's going to go down to 0, and then the offer can't be eaten
            if (priceSheetPub.remainNum.compareTo(BigInteger.ZERO) == 0) continue;
            // The validation period was judged again
            BigInteger subtract = nowBlockNumber.subtract(priceSheetPub.height);
            if (subtract.compareTo(nestProperties.getPriceDurationBlock()) > 0) continue;

            // Get this quotation when eating order double ETH
            BigInteger ethMultiple = priceSheetPub.level.intValue() <= nestProperties.getMaxBiteNestedLevel() ? nestProperties.getBiteInflateFactor() : BigInteger.ONE;
            // The price of this quotation list
            BigDecimal orderPrice = MathUtils.intDivDec(priceSheetPub.price, erc20.getDecPowTen(), erc20.getDecimals());
            // Quantity of Remainable ETH: remainNum * Base Quote Size
            BigInteger dealEthAmount = priceSheetPub.remainNum;
            // Number of remaining ERC20 transactions:dealEthAmount*tokenAmountPerEth
            BigInteger dealErc20Amount = dealEthAmount.multiply(priceSheetPub.price);
            // Number of remaining transactionable ETH: WEI
            BigInteger dealWeiAmount = dealEthAmount.multiply(Constant.UNIT_INT18);
            log.info("{}[{}]  The remaining ETH that can be traded：{} ,The remaining ERC20 that can be traded：{}", erc20.getSymbol(), priceSheetPub.index, dealWeiAmount, dealErc20Amount);

            if (!erc20State.updateEthErc20Price(erc20)) continue;

            // Judge whether the quotation contract meets the conditions of eating orders: there is surplus, profitable
            boolean meetBiteCondition = meetBiteCondition(orderPrice, erc20.getEthErc20Price(), biteRate);
            if (!meetBiteCondition) continue;
            // The balance currently available minus the balance already used
            updateAsset();

            // Determine the type of eating order
            boolean biteEth = false;
            // The exchange price is greater than the price of the order to be eaten: pay ERC20, eat ETH, and then the exchange sells ETH at a higher price
            if (erc20.getEthErc20Price().compareTo(orderPrice) > 0) {
                biteEth = true;
            } else { // The exchange price is less than the price of the eaten order: pay the EHT, eat the ERC20, and then the exchange buys the ETH at a lower price
                biteEth = false;
            }

            // Determine if you can eat it all
            BigInteger biteAllFee = MathUtils.toDecimal(dealEthAmount).multiply(nestProperties.getBiteFeeRate()).multiply(Constant.UNIT_ETH).toBigInteger();
            boolean canEatAll = canEatAll(ethMultiple, erc20, biteEth, dealWeiAmount, dealErc20Amount, biteAllFee, priceSheetPub.nestNum1k);
            BigInteger copies = null;
            // You can eat all of them
            BigInteger biteFee = null;
            if (canEatAll) {
                copies = dealEthAmount.divide(nestProperties.getMiningEthUnit().toBigInteger());
                biteFee = biteAllFee;
            } else {// You can't eat it all
                // Number of single serving: the base quotation size is one serving
                BigInteger biteOneFee = nestProperties.getMiningEthUnit().multiply(nestProperties.getBiteFeeRate()).multiply(Constant.UNIT_ETH).toBigInteger();
                biteFee = biteOneFee;
                BigInteger beginNum = (priceSheetPub.ethNumBal.add(priceSheetPub.tokenNumBal)).divide(Constant.BIG_INTEGER_TWO);
                copies = getCopies(useable, biteEth, erc20, orderPrice, ethMultiple, biteOneFee, priceSheetPub.nestNum1k, beginNum);
                if (copies.compareTo(BigInteger.ZERO) <= 0) {
                    log.error("The balance is not enough to eat the order");
                    return false;
                }
            }

            // By eating order
            String hash = sendBiteOffer(priceSheetPub, erc20, ethMultiple, biteEth, copies, nonce, biteFee, wallet);
            if (!StringUtils.isEmpty(hash)) {
                nonce = nonce.add(BigInteger.ONE);
                erc20.addBiteIndex(priceSheetPub.index);
                bite = true;
            }

        }
        return bite;
    }

    private String sendBiteOffer(PriceSheetView priceSheetPub,
                                 Erc20State.Item erc20,
                                 BigInteger multiple,
                                 boolean eatEth,
                                 BigInteger copies,
                                 BigInteger nonce,
                                 BigInteger biteFee,
                                 Wallet wallet) {

        BigInteger remainNum = (priceSheetPub.ethNumBal.add(priceSheetPub.tokenNumBal)).divide(Constant.BIG_INTEGER_TWO);

        // Base quotation scale
        BigInteger miningEthUnit = nestProperties.getMiningEthUnit().toBigInteger();
        BigInteger biteNum = copies.multiply(miningEthUnit);
        BigInteger newTokenAmountPerEth = erc20.getEthErc20Price().multiply(erc20.getDecPowTen()).toBigInteger();
        BigInteger index = priceSheetPub.index;

        // Amount of ETH to be entered into the contract: service fee + to be transferred from the account
        BigInteger payEthAmount = BigInteger.ZERO;
        // The amount of NEST you need to pledge for a meal
        BigInteger newNestNum1k = priceSheetPub.nestNum1k.multiply(nestProperties.getBiteNestInflateFactor().multiply(biteNum)).divide(remainNum);
        BigInteger needNest = newNestNum1k.multiply(Constant.BIG_INTEGER_1K);
        // The number of ETH required to eat the order
        BigInteger needEth = null;
        // The number of ERC20 required to eat the order
        BigInteger needErc20 = null;
        // Number of ERC20 that can be traded
        BigInteger tranErc20Amount = priceSheetPub.price.multiply(biteNum);

        String msg = null;
        String method = null;

        if (eatEth) {
            msg = "biteEth Eat order (enter {} to get ETH) , Hash ： {}";
            method = BITE_ETH;

            needEth = biteNum.multiply(multiple).subtract(biteNum);
            needErc20 = newTokenAmountPerEth.multiply(biteNum).add(tranErc20Amount);
        } else {
            msg = "biteToken Eat order (enter ETH to get {}) Hash ： {}";
            method = BITE_TOKEN;

            needEth = biteNum.multiply(multiple).add(biteNum);
            needErc20 = newTokenAmountPerEth.multiply(biteNum).subtract(tranErc20Amount);
        }

        if (erc20.getSymbol().equalsIgnoreCase("NEST")) {
            // The number of Nest eating order Ntoken needs to be plus the number of Nest mortgaged
            needErc20 = needErc20.add(needNest);
            needNest = needErc20;
        }

        List<Type> typeList = Arrays.<Type>asList(
                new Address(erc20.getAddress()),
                new Uint256(index),
                new Uint256(biteNum),
                new Uint256(newTokenAmountPerEth)
        );
        // Calculate how much ETH needs to be pumped in
        BigInteger needAccountEth = BigInteger.ZERO, needCloseEth = BigInteger.ZERO;
        needEth = needEth.multiply(Constant.UNIT_INT18);
        if (closed.getEthAmount().compareTo(needEth) > 0) {
            needCloseEth = needEth;
            needAccountEth = biteFee;
            payEthAmount = payEthAmount.add(biteFee);
        } else {
            needCloseEth = closed.getEthAmount();
            payEthAmount = needEth.subtract(closed.getEthAmount()).add(biteFee);
            needAccountEth = payEthAmount;
        }

        log.info("{} Eat order asset：needEth={} needAccountEth={}  needCloseEth={} needErc20={}  needNest={}  payEthAmount={}",
                erc20.getSymbol(), needEth, needAccountEth, needCloseEth, needErc20, needNest, payEthAmount);

        log.info("{} Eat order parameter：index={}  biteNum={}  newTokenAmountPerEth={}",
                erc20.getSymbol(), index, biteNum, newTokenAmountPerEth);


        BigInteger gasPrice = ethClient.ethGasPrice(gasPriceState.baseBiteType);
        String minerAddress = priceSheetPub.miner.getValue();
        String transactionHash = ethClient.bite(method, wallet, gasPrice, nonce, typeList, payEthAmount);

        log.info(msg, erc20.getSymbol(), transactionHash);

        // Here will eat order quote consumption of assets saved
        if (!StringUtils.isEmpty(transactionHash)) {
            ethUsed = ethUsed.add(needEth);
            accountEthUsed = accountEthUsed.add(needAccountEth);
            closeEthUsed = closeEthUsed.add(needCloseEth);
            nestUsed = nestUsed.add(needEth);
            if (erc20.getSymbol().equals(erc20State.token.getSymbol())) {
                tokenUsed = tokenUsed.add(needErc20);
            } else {
                nTokenUsed = nTokenUsed.add(needErc20);
            }
        } else {
            return transactionHash;
        }

        // hedge
        if (!StringUtils.isEmpty(transactionHash) && erc20.isHedge() && hedgeService.isReady()) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    if (!ethClient.checkTxStatus(transactionHash, nonce, wallet.getCredentials().getAddress())) {
                        return;
                    }
                    if (minerAddress.equalsIgnoreCase(wallet.getCredentials().getAddress())) {
                        log.info("Eat your own quotation and don't hedge");
                        return;
                    }
                    BigDecimal erc20Unit = MathUtils.intDivDec(tranErc20Amount, erc20.getDecPowTen(), erc20.getDecimals());
                    log.info("To hedge");
                    // If you eat ERC20
                    if (!eatEth) {
                        // ETH goes down, you need to buy ETH
                        // If it is an etherc20 transaction pair, the transaction number indicates how many tokens will be used to buy the ETH
                        String tradingPair = erc20.getTradingPair().toLowerCase();
                        if (tradingPair.startsWith("eth")) {
                            log.info("Hedging trade pair [{}] : Costs {} ETH, Huobi exchange hang buy order, sells {} {}", tradingPair, biteNum, erc20Unit, erc20.getSymbol());
                            if (hedgeService.makeBuyOrder(tradingPair, erc20Unit, erc20.getSymbol())) {
                                log.info("The hedge to complete");
                            }
                        } else {
                            // If it is an erc20eth transaction pair, the sell order needs to be placed, indicating how many tokens to sell
                            log.info("Hedging trade pair [{}] :Costs {} ETH, Huobi exchange hang sell order, sells {} {}", tradingPair, biteNum, erc20Unit, erc20.getSymbol());
                            if (hedgeService.makeSellOrder(tradingPair, erc20Unit, erc20.getSymbol())) {
                                log.info("The hedge to complete");
                            }
                        }
                    } else {
                        // If you eat more ETH, you sell the increment of ETH
                        // If it is an etherc20 transaction pair, you need to hang the sell order and pass how much ETH is sold
                        String tradingPair = erc20.getTradingPair().toLowerCase();
                        if (tradingPair.startsWith("eth")) {
                            log.info("Hedging trade pair [{}] :ETH increase {}, Huobi exchange hang sell order, sell ETH increment", tradingPair, biteNum);
                            if (hedgeService.makeSellOrder(tradingPair, MathUtils.toDecimal(biteNum), erc20.getSymbol())) {
                                log.info("The hedge to complete");
                            }
                        } else {
                            // If it is an erc20eth transaction pair ,Then you need to hang up the buy order and transfer how much ETH to buy the TOKEN
                            log.info("Hedging trade pair [{}] :ETH increase {}, Huobi exchange hang buy order, buy {} with ETH ", tradingPair, biteNum, erc20.getSymbol());
                            if (hedgeService.makeBuyOrder(tradingPair, MathUtils.toDecimal(biteNum), erc20.getSymbol())) {
                                log.info("The hedge to complete");
                            }
                        }
                    }
                }
            });
        }

        return transactionHash;
    }

    private boolean fillAsset(Wallet wallet) {
        Wallet.Asset useable = wallet.getUseable();
        if (aseetIsNull(useable)) return false;
        Wallet.Asset account = wallet.getAccount();
        if (aseetIsNull(account)) return false;
        Wallet.Asset closed = wallet.getClosed();
        if (aseetIsNull(closed)) return false;

        this.account = new Wallet.Asset(account);
        this.closed = new Wallet.Asset(closed);
        this.useable = new Wallet.Asset(useable);
        return true;
    }

    private boolean aseetIsNull(Wallet.Asset asset) {
        if (asset.isNull(verifyState.isBiteNtoken() && verifyState.isMustPost2())) return true;
        return false;
    }

    private BigInteger getCopies(Wallet.Asset asset,
                                 boolean eatEth,
                                 Erc20State.Item erc20,
                                 BigDecimal orderPrice,
                                 BigInteger multiple,
                                 BigInteger biteFee,
                                 BigInteger nestNum1k,
                                 BigInteger ethNum) {


        BigDecimal exchangePrice = erc20.getEthErc20Price();
        // The amount of ETH required to eat a serving size
        BigInteger offerEth = multiple.multiply(nestProperties.getMiningEthUnit().toBigInteger());
        // Eat the number of ERC20 required for a serving size
        BigInteger offerErc20 = MathUtils.toBigInt(MathUtils.decMulInt(exchangePrice, offerEth).multiply(erc20.getDecPowTen())); // 报价
        // The amount of ERC20 paid to the other party in a single order
        BigInteger eatErc20 = MathUtils.toBigInt(MathUtils.decMulInt(orderPrice, nestProperties.getMiningEthUnit().toBigInteger()).multiply(erc20.getDecPowTen())); // 吃单

        // It takes the total number of ETH to eat one serving
        BigInteger eatOneEth = null;
        // It takes a total of ERC20 to eat one serving
        BigInteger eatOneErc20 = null;
        // Eat a portion of Nest that requires collateral
        BigInteger biteEthNum = nestProperties.getMiningEthUnit().toBigInteger();// 吃一份
        BigInteger newNestNum1k = nestNum1k.multiply(nestProperties.getBiteNestInflateFactor().multiply(biteEthNum)).divide(ethNum);
        BigInteger needNest = newNestNum1k.multiply(Constant.BIG_INTEGER_1K).multiply(Constant.UNIT_INT18);

        // Base quotation scale
        BigInteger miningEthUnit = nestProperties.getMiningEthUnit().toBigInteger();
        if (eatEth) {
            eatOneEth = offerEth.subtract(miningEthUnit);
            eatOneErc20 = offerErc20.add(eatErc20);
        } else {
            eatOneEth = offerEth.add(miningEthUnit);
            eatOneErc20 = offerErc20.subtract(eatErc20);
        }

        if (erc20.getSymbol().equalsIgnoreCase("NEST")) {
            // The number of Nest eating order, Ntoken needs to be plus the number of Nest mortgaged
            eatOneErc20 = eatOneErc20.add(needNest);
            needNest = eatOneErc20;
        }
        // The units are converted to WEI
        eatOneEth = eatOneEth.multiply(Constant.UNIT_INT18);

        // The balance of ETH can be eaten in a single serving
        BigInteger balance = asset.getEthAmount().subtract(Constant.PACKAGING_COSTS).subtract(biteFee);
        BigInteger copiesEth = MathUtils.intDivInt(balance, eatOneEth, 0).toBigInteger();
        // ERC20 balance can be eaten in single servings
        BigInteger erc20Balance = null;
        if (erc20.getSymbol().equals(erc20State.token.getSymbol())) {
            erc20Balance = asset.getTokenAmount();
        } else {
            erc20Balance = asset.getnTokenAmount();
        }
        BigInteger copiesErc20 = MathUtils.intDivInt(erc20Balance, eatOneErc20, 0).toBigInteger();
        if (eatOneErc20.compareTo(BigInteger.ZERO) < 0) {
            // ERC20 is not required for the order and will return ERC20, so ERC20 is sufficient and is set to 10 by default
            copiesErc20 = BigInteger.TEN;
        }

        BigInteger copiseNest = MathUtils.intDivInt(asset.getNestAmount(), needNest, 0).toBigInteger();

        log.info("ETH servings available: {}", copiesEth);
        log.info("{} servings available: {}", erc20.getSymbol(), copiesErc20);
        log.info("Mortgaged Nest can be eaten in a single serving: {}", copiseNest);

        if (copiesErc20.compareTo(copiesEth) < 0) {
            if (copiesErc20.compareTo(copiseNest) < 0) {
                return copiesErc20;
            }
            return copiseNest;
        }
        return copiesEth;
    }

    private boolean canEatAll(BigInteger multiple,
                              Erc20State.Item erc20,
                              boolean biteEth,
                              BigInteger dealEthAmount,
                              BigInteger dealErc20Amount,
                              BigInteger biteFee,
                              BigInteger nestNum1k) {
        BigDecimal ethErc20Price = erc20.getEthErc20Price();
        // Eat all quoted ETH quantity
        BigInteger ethAmount = multiple.multiply(dealEthAmount);
        // Eat the full quotation ERC20 quantity
        BigInteger eth = MathUtils.toBigInt(MathUtils.toDecimal(ethAmount).divide(Constant.UNIT_ETH, 0, BigDecimal.ROUND_DOWN));
        BigInteger erc20Amount = MathUtils.decMulInt(ethErc20Price, eth).multiply(erc20.getDecPowTen()).toBigInteger();
        // Eat all the minimum amount of ETH required
        BigInteger minEthAmount = null;
        // Eat all the minimum required ERC20
        BigInteger minErc20Amount = null;

        // Calculate the number of NEST mortgage required for this order
        BigInteger biteEthNum = dealEthAmount.divide(Constant.UNIT_INT18);// Eat all
        BigInteger newNestNum1k = nestNum1k.multiply(nestProperties.getBiteNestInflateFactor().multiply(biteEthNum)).divide(biteEthNum);
        BigInteger needNest = newNestNum1k.multiply(Constant.BIG_INTEGER_1K);

        if (biteEth) {
            // The minimum amount of ETH required to eat all: the quoted amount + the charge for eating the order + the miner packing charge - the amount of ETH obtained by eating the order
            minEthAmount = ethAmount.add(biteFee).add(Constant.PACKAGING_COSTS).subtract(dealEthAmount);
            // Minimum number of ERC20 required to eat all: QUOTE ERC20+ ERC20 required to eat order
            minErc20Amount = erc20Amount.add(dealErc20Amount);
        } else {
            // The minimum amount of ETH required to eat all: the quoted amount + the cost of eating the order + the miner packing fee + the amount of ETH required to eat the order
            minEthAmount = ethAmount.add(biteFee).add(Constant.PACKAGING_COSTS).add(dealEthAmount);
            // Minimum number of ERC20 required to eat all: ETH* exchange price - number of ERC20 obtained by eating order
            minErc20Amount = erc20Amount.subtract(dealErc20Amount);
        }

        if (erc20.getSymbol().equalsIgnoreCase("NEST")) {
            // The number of Nest eating single Ntoken needs to be plus the number of Nest mortgaged
            minErc20Amount = minErc20Amount.add(needNest);
            needNest = minErc20Amount;
        }

        // You can't eat it all
        if (useable.getEthAmount().compareTo(minEthAmount) < 0) {
            log.info("Available ETH balance = {}, eat all need at least {}, can not eat all", useable.getEthAmount(), minEthAmount);
            return false;
        }
        if (erc20.getSymbol().equals(erc20State.token.getSymbol())) {
            if (useable.getTokenAmount().compareTo(minErc20Amount) < 0) {
                log.info("Available {} balance = {}, eat all need at least {}, can not eat all", erc20.getSymbol(), useable.getTokenAmount(), minErc20Amount);
                return false;
            }
        } else if (useable.getnTokenAmount().compareTo(minErc20Amount) < 0) {
            log.info("Available {} balance = {}, eat all need at least {}, can not eat all", erc20.getSymbol(), useable.getnTokenAmount(), minErc20Amount);
            return false;
        }

        // Determine if the NEST balance is sufficient
        if (useable.getNestAmount().compareTo(needNest) < 0) {
            log.info("Available NEST balance = {}, eat all at least {} NEST, can not eat all", useable.getNestAmount(), needNest);
            return false;
        }
        // Can eat all
        return true;
    }

    private void updateAsset() {
        closed.setEthAmount(closed.getEthAmount().subtract(closeEthUsed));
        account.setEthAmount(account.getEthAmount().subtract(accountEthUsed));
        useable.setEthAmount(closed.getEthAmount().add(account.getEthAmount()));

        useable.setTokenAmount(useable.getTokenAmount().subtract(tokenUsed));

        if (verifyState.isBiteNtoken() && verifyState.isMustPost2()) {
            // If the NToken is Nest
            if (erc20State.nToken.getSymbol().equalsIgnoreCase("NEST")) {
                BigInteger nest = useable.getnTokenAmount().subtract(nTokenUsed).subtract(nestUsed);
                useable.setnTokenAmount(nest);
                useable.setNestAmount(nest);
            } else {
                useable.setnTokenAmount(useable.getnTokenAmount().subtract(nTokenUsed));
            }
        } else {
            useable.setNestAmount(useable.getNestAmount().subtract(nestUsed));
        }
    }

    private void clearUsed() {
        ethUsed = BigInteger.ZERO;
        accountEthUsed = BigInteger.ZERO;
        closeEthUsed = BigInteger.ZERO;
        tokenUsed = BigInteger.ZERO;
        nTokenUsed = BigInteger.ZERO;
        nestUsed = BigInteger.ZERO;
    }

    // Determine whether it meets the conditions for eating the order
    private boolean meetBiteCondition(BigDecimal orderPrice,
                                      BigDecimal exchangePrice,
                                      BigDecimal biteRate) {
        if (exchangePrice == null) {
            log.error("Exchange price acquisition failed, unable to eat orders");
            return false;
        }
        // Calculate price deviation
        BigDecimal priceDeviation = (orderPrice.subtract(exchangePrice)).divide(exchangePrice, 10, BigDecimal.ROUND_DOWN).abs();

        log.info("Quotation deviation: {}, eat order threshold: {}", priceDeviation, biteRate);
        // Less than the threshold value of eating order, do not eat order
        if (priceDeviation.compareTo(biteRate) < 0) {
            return false;
        }

        return true;
    }

    /**
     * Unfreeze assets in bulk
     *
     * @param wallet
     */
    @Override
    public void closePriceSheets(Wallet wallet) {
        String address = wallet.getCredentials().getAddress();
        Erc20State.Item token = erc20State.token;
        BigInteger nonce = ethClient.ethGetTransactionCount(address);
        if (nonce == null) {
            log.error("{} ：closeList Failed to get nonce", address);
            return;
        }

        walletHelper.updateBalance(wallet, true);

        String closePriceHash = null;
        if (verifyState.isBiteToken()) {
            closePriceHash = closePriceSheetList(wallet, nonce, token);
        }
        if (!StringUtils.isEmpty(closePriceHash)) {
            nonce = nonce.add(BigInteger.ONE);
            log.info("Token batch unfreezing asset hash:{}", closePriceHash);

        }

        if (verifyState.isBiteNtoken()) {
            Erc20State.Item ntoken = erc20State.nToken;
            String closeNtokenPriceHash = closePriceSheetList(wallet, nonce, ntoken);
            if (closeNtokenPriceHash == null) return;
            log.info("NToken batch unfreezing asset hash:{}", closeNtokenPriceHash);
        }
    }

    private String closePriceSheetList(Wallet wallet, BigInteger nonce, Erc20State.Item erc20) {
        // Get quotes that can be defrosted
        List<Uint256> indices = ethClient.canClosedSheetIndexs(wallet.getCredentials().getAddress(), erc20.getAddress(), verifyState.getMaxFindNum());

        boolean empty = CollectionUtils.isEmpty(indices);
        if (empty) return null;

        // Whether to wait for the next thaw
        boolean wait = true;
        int size = empty ? 0 : indices.size();
        if (empty || size < verifyState.getCloseMinNum()) {
            log.info("{} Quantity of quotation sheets that can be thawed :{}, the minimum thawed quantity is not reached :{}", erc20.getSymbol(), size, verifyState.getCloseMinNum());

            // Gets the largest index of the current quotation list
            BigInteger nowMaxIndex = ethClient.lastIndex(erc20.getAddress());
            if (nowMaxIndex == null) {
                wait = false;
                log.info("Quotation list length retrieval failed. Must be defrosted");
            }

            if (size > 0 && wait) {
                BigInteger farthestIndex = indices.get(size - 1).getValue();
                // Maximum number of queries -10
                BigInteger subtract = verifyState.getMaxFindNum().subtract(BigInteger.TEN);
                // The largest index of the current quotation list - the index of the last quotation that can be defrosted
                BigInteger subtract1 = nowMaxIndex.subtract(farthestIndex);
                if (subtract1.compareTo(subtract) >= 0) {
                    // If the difference between the largest index of the current quotation list and the index of the last quotation list is greater than the maximum number of queries -10,
                    // it must be defrosted at this time to avoid that the quotation list cannot be defrosted by subsequent queries
                    wait = false;
                    log.info("This quotation is so old that it must be defrosted");
                }
            }

            if (wait) {
                // Check whether the remaining assets can be eaten
                boolean biteEth = true;
                BigInteger copies1 = getCopies(wallet.getUseable(), biteEth, erc20, erc20.getEthErc20Price(), Constant.BIG_INTEGER_TWO, BigInteger.ZERO, Constant.BIG_INTEGER_100, nestProperties.getMiningEthUnit().toBigInteger());
                if (copies1.compareTo(BigInteger.ONE) < 0) {
                    wait = false;
                    log.info("The available assets of the account are insufficient, so it is no longer possible to offer orders. At this time, assets must be unfrozen");
                }
            }
        }

        if (size >= verifyState.getCloseMinNum()) wait = false;

        if (wait) return null;

        if (size == 1) {
            String closeHash = ethClient.close(wallet, nonce, erc20.getAddress(), new Uint256(indices.get(0).getValue()));
            return closeHash;
        }

        String hash = ethClient.closeList(wallet, nonce, erc20.getAddress(), indices);
        return hash;
    }

    /**
     * Update the exchange price to calculate the number of ERC20 and NTokens needed to specify the ETH quote
     */
    public boolean updatePrice() {
        boolean result;

        boolean ok = erc20State.updateEthTokenPrice();

        // When the post2 method must be called, the corresponding NToken price is queried
        if (ok && verifyState.isMustPost2() && verifyState.isBiteNtoken()) {
            result = erc20State.updateEthNtokenPrice();
        } else {
            result = ok;
        }
        return result;
    }

}
