package com.ticketing.billing.service;

import com.ticketing.billing.domain.Cobranca;
import com.ticketing.billing.domain.CobrancaMetodoEnum;
import com.ticketing.billing.domain.CobrancaStatusEnum;
import com.ticketing.billing.domain.CobrancaTipoEnum;
import com.ticketing.billing.dto.CobrancaBasicoResponseDTO;
import com.ticketing.billing.dto.CobrancaRequestDTO;
import com.ticketing.billing.exception.CobrancaCriacaoException;
import com.ticketing.billing.exception.CobrancaEmAndamentoException;
import com.ticketing.billing.integration.UserContext;
import com.ticketing.billing.lock.LockExecutor;
import com.ticketing.billing.repository.CobrancaRepository;
import com.ticketing.billing.service.strategy.CobrancaCriacaoStrategyRegistry;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class CobrancaService {

    private static final String LOCK_KEY_PREFIX = "cobrancas:";
    private static final long LOCK_TTL_SECONDS = 5;
    private static final ZoneId ZONE_SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final CobrancaRepository cobrancaRepository;
    private final LockExecutor lockExecutor;
    private final UserContext userContext;
    private final CobrancaCriacaoStrategyRegistry strategyRegistry;

    public CobrancaService(CobrancaRepository cobrancaRepository,
                           LockExecutor lockExecutor,
                           UserContext userContext,
                           CobrancaCriacaoStrategyRegistry strategyRegistry) {
        this.cobrancaRepository = cobrancaRepository;
        this.lockExecutor = lockExecutor;
        this.userContext = userContext;
        this.strategyRegistry = strategyRegistry;
    }

    public CobrancaBasicoResponseDTO criarCobranca(CobrancaRequestDTO request) {
        String idUsuario = userContext.getIdUsuario();
        String lockKey = LOCK_KEY_PREFIX + idUsuario;

        try {
            return lockExecutor.executeWithLock(lockKey, LOCK_TTL_SECONDS, () -> {
                Cobranca cobranca = buildCobranca(request, idUsuario);

                strategyRegistry.getStrategy(cobranca.getMetodo()).aplicar(cobranca);

                Cobranca saved = cobrancaRepository.save(cobranca);
                return toResponse(saved);
            });
        } catch (CobrancaEmAndamentoException e) {
            throw e;
        } catch (Exception e) {
            throw new CobrancaCriacaoException(e);
        }
    }

    private Cobranca buildCobranca(CobrancaRequestDTO request, String idUsuario) {
        Cobranca cobranca = new Cobranca();
        cobranca.setIdUsuario(idUsuario);
        cobranca.setNomeSolicitante(userContext.getGivenName() + " " + userContext.getFamilyName());
        cobranca.setTipo(request.tipo() != null ? request.tipo() : CobrancaTipoEnum.RECARGA);
        cobranca.setMetodo(request.metodo() != null ? request.metodo() : CobrancaMetodoEnum.PIX);
        cobranca.setStatus(CobrancaStatusEnum.SOLICITADA);
        cobranca.setValorSolicitacao(request.valor());
        cobranca.setDataCriacao(LocalDateTime.now(ZONE_SAO_PAULO));
        return cobranca;
    }

    private CobrancaBasicoResponseDTO toResponse(Cobranca cobranca) {
        return new CobrancaBasicoResponseDTO(
                cobranca.getId(),
                cobranca.getTxid(),
                cobranca.getCopiaECola(),
                cobranca.getDataExpiracao(),
                cobranca.getTransactionId(),
                cobranca.getAcsUrl(),
                cobranca.getThreeDsPayload()
        );
    }
}
