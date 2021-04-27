package com.nest.ib.service;

import com.nest.ib.model.Wallet;


public interface BiteService {

    void bite(Wallet wallet);

    boolean updatePrice();

    void closePriceSheets(Wallet wallet);

}
