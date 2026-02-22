package com.service.internalWallet.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.service.internalWallet.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT DISTINCT t
        FROM Transaction t
        JOIN t.ledgerEntries le
        JOIN le.account a
        JOIN a.user u
        WHERE u.email = :email
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findByUserEmail(String email);
}
