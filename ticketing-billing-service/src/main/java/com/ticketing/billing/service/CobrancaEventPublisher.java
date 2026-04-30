package com.ticketing.billing.service;

import com.ticketing.billing.domain.Cobranca;
import com.ticketing.billing.dto.CobrancaCriadaEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CobrancaEventPublisher {

    private static final String TOPIC = "cobrancas.criada";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CobrancaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicarCobrancaCriada(Cobranca cobranca) {
        CobrancaCriadaEvent event = new CobrancaCriadaEvent(
                cobranca.getId(),
                cobranca.getIdUsuario(),
                cobranca.getTipo().name(),
                cobranca.getMetodo().name(),
                cobranca.getStatus().name(),
                cobranca.getValorSolicitacao(),
                cobranca.getDataCriacao()
        );
        kafkaTemplate.send(TOPIC, cobranca.getIdUsuario(), event);
    }
}
