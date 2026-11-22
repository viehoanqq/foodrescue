package com.foodrescue.finance.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

/** Persistence mapping for this service's own table; cross-service IDs stay scalar. */
@Entity
@Table(name = "payouts")
public class PayoutEntity {
    public PayoutEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "payout_code", nullable = false, length = 30)
    private String payoutCode;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "order_count", nullable = false)
    private Integer orderCount;

    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal grossAmount;

    @Column(name = "commission_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal commissionAmount;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal netAmount;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "bank_account_no", nullable = false, length = 30)
    private String bankAccountNo;

    @Column(name = "bank_account_name", nullable = false, length = 100)
    private String bankAccountName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "transfer_ref", length = 100)
    private String transferRef;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_by")
    private Long paidBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPayoutCode() { return payoutCode; }
    public void setPayoutCode(String payoutCode) { this.payoutCode = payoutCode; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public LocalDate getPeriodFrom() { return periodFrom; }
    public void setPeriodFrom(LocalDate periodFrom) { this.periodFrom = periodFrom; }

    public LocalDate getPeriodTo() { return periodTo; }
    public void setPeriodTo(LocalDate periodTo) { this.periodTo = periodTo; }

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }

    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }

    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }

    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBankAccountNo() { return bankAccountNo; }
    public void setBankAccountNo(String bankAccountNo) { this.bankAccountNo = bankAccountNo; }

    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String bankAccountName) { this.bankAccountName = bankAccountName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTransferRef() { return transferRef; }
    public void setTransferRef(String transferRef) { this.transferRef = transferRef; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getPaidBy() { return paidBy; }
    public void setPaidBy(Long paidBy) { this.paidBy = paidBy; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
