package com.ticketing.billing.service;

import com.ticketing.billing.domain.Cobranca;
import com.ticketing.billing.domain.CobrancaMetodoEnum;
import com.ticketing.billing.domain.CobrancaStatusEnum;
import com.ticketing.billing.domain.CobrancaTipoEnum;
import com.ticketing.billing.dto.*;
import com.ticketing.billing.exception.CobrancaCriacaoException;
import com.ticketing.billing.exception.CobrancaEmAndamentoException;
import com.ticketing.billing.exception.CobrancaNaoEncontradaException;
import com.ticketing.billing.integration.CheckoutValidationClient;
import com.ticketing.billing.integration.StatusConsultaExternaClient;
import com.ticketing.billing.integration.UserContext;
import com.ticketing.billing.lock.LockExecutor;
import com.ticketing.billing.repository.CobrancaRepository;
import com.ticketing.billing.service.strategy.CobrancaCriacaoStrategyRegistry;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@Service
public class CobrancaService {

    private static final String LOCK_KEY_PREFIX = "cobrancas:";
    private static final long LOCK_TTL_SECONDS = 5;
    private static final ZoneId ZONE_SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private static final Set<CobrancaStatusEnum> STATUS_CONSULTAVEIS = Set.of(
            CobrancaStatusEnum.SOLICITADA,
            CobrancaStatusEnum.EXPIRADA,
            CobrancaStatusEnum.ERRO_APROVACAO_PEDIDO,
            CobrancaStatusEnum.EM_REPROCESSAMENTO,
            CobrancaStatusEnum.ERRO_ANALISE_PENDENTE
    );

    private final CobrancaRepository cobrancaRepository;
    private final LockExecutor lockExecutor;
    private final UserContext userContext;
    private final CobrancaCriacaoStrategyRegistry strategyRegistry;
    private final StatusConsultaExternaClient statusConsultaExternaClient;
    private final CheckoutValidationClient checkoutValidationClient;
    private final CobrancaEventPublisher cobrancaEventPublisher;

    public CobrancaService(CobrancaRepository cobrancaRepository,
                           LockExecutor lockExecutor,
                           UserContext userContext,
                           CobrancaCriacaoStrategyRegistry strategyRegistry,
                           StatusConsultaExternaClient statusConsultaExternaClient,
                           CheckoutValidationClient checkoutValidationClient,
                           CobrancaEventPublisher cobrancaEventPublisher) {
        this.cobrancaRepository = cobrancaRepository;
        this.lockExecutor = lockExecutor;
        this.userContext = userContext;
        this.strategyRegistry = strategyRegistry;
        this.statusConsultaExternaClient = statusConsultaExternaClient;
        this.checkoutValidationClient = checkoutValidationClient;
        this.cobrancaEventPublisher = cobrancaEventPublisher;
    }

    public CobrancaBasicoResponseDTO criarCobranca(CobrancaRequestDTO request) {
        String idUsuario = userContext.getIdUsuario();
        String lockKey = LOCK_KEY_PREFIX + idUsuario;

        try {
            return lockExecutor.executeWithLock(lockKey, LOCK_TTL_SECONDS, () -> {
                Cobranca cobranca = buildCobranca(request, idUsuario);

                strategyRegistry.getStrategy(cobranca.getMetodo()).aplicar(cobranca);

                Cobranca saved = cobrancaRepository.save(cobranca);
                cobrancaEventPublisher.publicarCobrancaCriada(saved);
                return toResponse(saved);
            });
        } catch (CobrancaEmAndamentoException e) {
            throw e;
        } catch (Exception e) {
            throw new CobrancaCriacaoException(e);
        }
    }

    public CobrancaCompletoResponseDTO consultarCobrancaPorId(Long id) {
        Cobranca cobranca = cobrancaRepository.findById(id)
                .orElseThrow(() -> new CobrancaNaoEncontradaException(id));

        Cobranca versaoAtual = cobrancaRepository.findTopByCobrancaPaiIdOrderByIdDesc(cobranca.getId())
                .orElse(cobranca);

        if (deveConsultarStatusExterno(versaoAtual)) {
            CobrancaStatusEnum novoStatus = statusConsultaExternaClient.consultarStatus(versaoAtual.getTxid());

            if (novoStatus != null && novoStatus != versaoAtual.getStatus()) {
                versaoAtual = criarNovaVersao(versaoAtual, novoStatus);
            }
        }

        return toCompletoResponse(versaoAtual);
    }

    public void processarNotificacaoWebhookPix(PixWebhookDTO dto) {
        if (dto == null || dto.pix() == null || dto.pix().isEmpty()) {
            return;
        }

        for (PixWebhookDTO.PixItemDTO item : dto.pix()) {
            if (item.txid() == null || item.txid().isBlank()) {
                continue;
            }

            cobrancaRepository.findTopByTxidOrderByIdDesc(item.txid())
                    .filter(cobranca -> cobranca.getStatus() != CobrancaStatusEnum.FINALIZADA)
                    .ifPresent(cobranca -> finalizarCobrancaPix(cobranca, item));
        }
    }

    public CobrancaCompletoResponseDTO validarCheckout(String transactionId, CheckoutValidationRequestDTO request) {
        Cobranca cobranca = cobrancaRepository.findTopByTransactionIdOrderByIdDesc(transactionId)
                .orElseThrow(() -> new CobrancaNaoEncontradaException(transactionId));

        checkoutValidationClient.validarCheckout(
                transactionId, request.cavv(), request.xid(), request.eci());

        cobranca.setValorPago(cobranca.getValorSolicitacao());
        cobranca.setDataFinalizada(LocalDateTime.now(ZONE_SAO_PAULO));

        Cobranca saved = criarNovaVersao(cobranca, CobrancaStatusEnum.FINALIZADA);
        return toCompletoResponse(saved);
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

    private void finalizarCobrancaPix(Cobranca original, PixWebhookDTO.PixItemDTO item) {
        Cobranca novaVersao = new Cobranca();
        novaVersao.setIdUsuario(original.getIdUsuario());
        novaVersao.setNomeSolicitante(original.getNomeSolicitante());
        novaVersao.setTipo(original.getTipo());
        novaVersao.setMetodo(original.getMetodo());
        novaVersao.setStatus(CobrancaStatusEnum.FINALIZADA);
        novaVersao.setValorSolicitacao(original.getValorSolicitacao());
        novaVersao.setValorPago(item.valor());
        novaVersao.setTxid(original.getTxid());
        novaVersao.setCopiaECola(original.getCopiaECola());
        novaVersao.setTransactionId(original.getTransactionId());
        novaVersao.setAcsUrl(original.getAcsUrl());
        novaVersao.setThreeDsPayload(original.getThreeDsPayload());
        novaVersao.setDataCriacao(original.getDataCriacao());
        novaVersao.setDataExpiracao(original.getDataExpiracao());
        novaVersao.setDataFinalizada(LocalDateTime.now(ZONE_SAO_PAULO));
        novaVersao.setCobrancaPaiId(original.getCobrancaPaiId() != null ? original.getCobrancaPaiId() : original.getId());
        cobrancaRepository.save(novaVersao);
    }

    private boolean deveConsultarStatusExterno(Cobranca cobranca) {
        return cobranca.getMetodo() == CobrancaMetodoEnum.PIX
                && STATUS_CONSULTAVEIS.contains(cobranca.getStatus());
    }

    private Cobranca criarNovaVersao(Cobranca original, CobrancaStatusEnum novoStatus) {
        Cobranca novaVersao = new Cobranca();
        novaVersao.setIdUsuario(original.getIdUsuario());
        novaVersao.setNomeSolicitante(original.getNomeSolicitante());
        novaVersao.setTipo(original.getTipo());
        novaVersao.setMetodo(original.getMetodo());
        novaVersao.setStatus(novoStatus);
        novaVersao.setValorSolicitacao(original.getValorSolicitacao());
        novaVersao.setValorPago(original.getValorPago());
        novaVersao.setTxid(original.getTxid());
        novaVersao.setCopiaECola(original.getCopiaECola());
        novaVersao.setTransactionId(original.getTransactionId());
        novaVersao.setAcsUrl(original.getAcsUrl());
        novaVersao.setThreeDsPayload(original.getThreeDsPayload());
        novaVersao.setDataCriacao(original.getDataCriacao());
        novaVersao.setDataExpiracao(original.getDataExpiracao());
        novaVersao.setDataFinalizada(original.getDataFinalizada());
        novaVersao.setCobrancaPaiId(original.getCobrancaPaiId() != null ? original.getCobrancaPaiId() : original.getId());
        return cobrancaRepository.save(novaVersao);
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

    private CobrancaCompletoResponseDTO toCompletoResponse(Cobranca cobranca) {
        return new CobrancaCompletoResponseDTO(
                cobranca.getId(),
                cobranca.getTxid(),
                cobranca.getIdUsuario(),
                cobranca.getTipo().name(),
                cobranca.getMetodo().name(),
                cobranca.getStatus().name(),
                cobranca.getValorSolicitacao(),
                cobranca.getValorPago(),
                cobranca.getDataCriacao(),
                cobranca.getDataExpiracao(),
                cobranca.getDataFinalizada()
        );
    }
}
