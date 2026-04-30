package com.ticketing.billing.repository;

import com.ticketing.billing.domain.Cobranca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CobrancaRepository extends JpaRepository<Cobranca, Long> {

    Optional<Cobranca> findTopByCobrancaPaiIdOrderByIdDesc(Long cobrancaPaiId);

    Optional<Cobranca> findTopByTxidOrderByIdDesc(String txid);

    Optional<Cobranca> findTopByTransactionIdOrderByIdDesc(String transactionId);
}
