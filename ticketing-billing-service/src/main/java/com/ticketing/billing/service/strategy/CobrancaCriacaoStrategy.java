package com.ticketing.billing.service.strategy;

import com.ticketing.billing.domain.Cobranca;
import com.ticketing.billing.domain.CobrancaMetodoEnum;

public interface CobrancaCriacaoStrategy {

    CobrancaMetodoEnum getMetodo();

    void aplicar(Cobranca cobranca);
}
