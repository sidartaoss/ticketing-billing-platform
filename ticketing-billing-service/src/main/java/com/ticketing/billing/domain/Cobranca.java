package com.ticketing.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cobrancas")
public class Cobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String idUsuario;

    @Column(nullable = false)
    private String nomeSolicitante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CobrancaTipoEnum tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CobrancaMetodoEnum metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CobrancaStatusEnum status;

    @Column(nullable = false)
    private BigDecimal valorSolicitacao;

    private BigDecimal valorPago;

    private String txid;

    private String copiaECola;

    private String transactionId;

    private String acsUrl;

    private String threeDsPayload;

    @Column(nullable = false)
    private LocalDateTime dataCriacao;

    private LocalDateTime dataExpiracao;

    private LocalDateTime dataFinalizada;

    public Cobranca() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(String idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNomeSolicitante() {
        return nomeSolicitante;
    }

    public void setNomeSolicitante(String nomeSolicitante) {
        this.nomeSolicitante = nomeSolicitante;
    }

    public CobrancaTipoEnum getTipo() {
        return tipo;
    }

    public void setTipo(CobrancaTipoEnum tipo) {
        this.tipo = tipo;
    }

    public CobrancaMetodoEnum getMetodo() {
        return metodo;
    }

    public void setMetodo(CobrancaMetodoEnum metodo) {
        this.metodo = metodo;
    }

    public CobrancaStatusEnum getStatus() {
        return status;
    }

    public void setStatus(CobrancaStatusEnum status) {
        this.status = status;
    }

    public BigDecimal getValorSolicitacao() {
        return valorSolicitacao;
    }

    public void setValorSolicitacao(BigDecimal valorSolicitacao) {
        this.valorSolicitacao = valorSolicitacao;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public void setValorPago(BigDecimal valorPago) {
        this.valorPago = valorPago;
    }

    public String getTxid() {
        return txid;
    }

    public void setTxid(String txid) {
        this.txid = txid;
    }

    public String getCopiaECola() {
        return copiaECola;
    }

    public void setCopiaECola(String copiaECola) {
        this.copiaECola = copiaECola;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getAcsUrl() {
        return acsUrl;
    }

    public void setAcsUrl(String acsUrl) {
        this.acsUrl = acsUrl;
    }

    public String getThreeDsPayload() {
        return threeDsPayload;
    }

    public void setThreeDsPayload(String threeDsPayload) {
        this.threeDsPayload = threeDsPayload;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public LocalDateTime getDataExpiracao() {
        return dataExpiracao;
    }

    public void setDataExpiracao(LocalDateTime dataExpiracao) {
        this.dataExpiracao = dataExpiracao;
    }

    public LocalDateTime getDataFinalizada() {
        return dataFinalizada;
    }

    public void setDataFinalizada(LocalDateTime dataFinalizada) {
        this.dataFinalizada = dataFinalizada;
    }
}
