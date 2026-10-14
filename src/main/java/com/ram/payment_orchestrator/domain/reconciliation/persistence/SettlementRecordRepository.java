package com.ram.payment_orchestrator.domain.reconciliation.persistence;

import com.ram.payment_orchestrator.domain.reconciliation.domain.SettlementMatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface SettlementRecordRepository extends JpaRepository<SettlementRecordEntity, Long> {

    List<SettlementRecordEntity> findByProviderCodeAndSettlementDate(
            String providerCode,
            LocalDate settlementDate
    );

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO settlement_records
                (provider_code, provider_ref, amount_minor, settlement_date, match_status)
            VALUES
                (:providerCode, :providerRef, :amountMinor, :settlementDate, :matchStatus)
            ON DUPLICATE KEY UPDATE
                amount_minor = VALUES(amount_minor),
                match_status = VALUES(match_status)
            """, nativeQuery = true)
    int insertOrUpdate(
            @Param("providerCode") String providerCode,
            @Param("providerRef") String providerRef,
            @Param("amountMinor") long amountMinor,
            @Param("settlementDate") LocalDate settlementDate,
            @Param("matchStatus") String matchStatus
    );
}
