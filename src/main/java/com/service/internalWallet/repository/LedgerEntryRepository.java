package com.service.internalWallet.repository;

import java.util.List;
import java.util.UUID;

import com.service.internalWallet.model.LedgerEntry;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountId(UUID accountId);

    @Query("SELECT SUM(" +
            "   CASE " +
            "       WHEN le.type = 'CREDIT' THEN le.amount " +
            "       ELSE -le.amount " +
            "   END) AS totalAmount " +
            "FROM LedgerEntry AS le " +
            "WHERE le.account.id = :accountId")
    Long sumAmountsByAccountId(@Param("accountId") UUID accountId);

    List<LedgerEntry> findByTransactionId(@NonNull UUID transactionId);
}
