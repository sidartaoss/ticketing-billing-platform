package com.ticketing.billing.service.strategy;

import com.ticketing.billing.domain.CobrancaMetodoEnum;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CobrancaCriacaoStrategyRegistry {

    private final Map<CobrancaMetodoEnum, CobrancaCriacaoStrategy> strategies;

    public CobrancaCriacaoStrategyRegistry(List<CobrancaCriacaoStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(CobrancaCriacaoStrategy::getMetodo, Function.identity()));
    }

    public CobrancaCriacaoStrategy getStrategy(CobrancaMetodoEnum metodo) {
        CobrancaCriacaoStrategy strategy = strategies.get(metodo);
        if (strategy == null) {
            throw new IllegalArgumentException("Nenhuma strategy encontrada para metodo: " + metodo);
        }
        return strategy;
    }
}
