package com.service.internalWallet.service;

import java.util.List;
import java.util.Optional;

import com.service.internalWallet.dto.request.TransactionRequestDto;
import com.service.internalWallet.dto.response.BalanceResponseDto;
import com.service.internalWallet.dto.response.ListTransactionResponseDto;
import com.service.internalWallet.dto.response.TransactionResponseDto;
import com.service.internalWallet.enums.AccountType;
import com.service.internalWallet.enums.LedgerEntryType;
import com.service.internalWallet.enums.Status;
import com.service.internalWallet.enums.TransactionType;
import com.service.internalWallet.exception.InsufficientFundsException;
import com.service.internalWallet.exception.ResourceNotFoundException;
import com.service.internalWallet.model.Account;
import com.service.internalWallet.model.Asset;
import com.service.internalWallet.model.LedgerEntry;
import com.service.internalWallet.model.Transaction;
import com.service.internalWallet.model.User;
import com.service.internalWallet.repository.AccountRepository;
import com.service.internalWallet.repository.AssetRepository;
import com.service.internalWallet.repository.LedgerEntryRepository;
import com.service.internalWallet.repository.TransactionRepository;
import com.service.internalWallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public TransactionResponseDto processTransaction(TransactionRequestDto transactionRequestDto) {
        Optional<Transaction> transactionExists = transactionRepository.findByIdempotencyKey(transactionRequestDto.idempotencyKey());

        return transactionExists.map(transaction -> {
            List<LedgerEntry> ledgerEntries = ledgerEntryRepository.findByTransactionId(transaction.getId());

            long userAmount = ledgerEntries.stream()
                    .filter(e -> AccountType.USER == e.getAccount().getType())
                    .mapToLong(LedgerEntry::getAmount)
                    .sum();

            return new TransactionResponseDto(
                    transaction.getId(),
                    transaction.getType(),
                    Math.abs(userAmount),
                    Math.abs(userAmount),
                    transaction.getStatus(),
                    transaction.getCreatedAt()
            );
        }).orElseGet(() -> {
            User user = userRepository.findByEmail(transactionRequestDto.email())
                                      .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            Asset asset = assetRepository.findByCode(transactionRequestDto.assetCode())
                                         .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

            Account userAccount = accountRepository.findByUserIdAndAssetId(user.getId(), asset.getId())
                                                   .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

            Account systemAccount = accountRepository.findByTypeAndAssetId(AccountType.SYSTEM, asset.getId())
                                                     .orElseThrow(() -> new ResourceNotFoundException("System Account not found"));

            if (transactionRequestDto.type() == TransactionType.TOPUP) {
                return topUpTransaction(transactionRequestDto, userAccount, systemAccount);
            } else if (transactionRequestDto.type() == TransactionType.BONUS) {
                return bonusTransaction(transactionRequestDto, userAccount, systemAccount);
            }
            return spendTransaction(transactionRequestDto, userAccount, systemAccount);
        });
    }

    @Transactional
    public TransactionResponseDto topUpTransaction(TransactionRequestDto transactionRequestDto, Account userAccount, Account systemAccount) {
        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.TOPUP);
        transaction.setIdempotencyKey(transactionRequestDto.idempotencyKey());
        transaction.setStatus(Status.SUCCESS);

        transactionRepository.save(transaction);

        LedgerEntry creditUser = new LedgerEntry();
        creditUser.setAccount(userAccount);
        creditUser.setType(LedgerEntryType.CREDIT);
        creditUser.setAmount(transactionRequestDto.amount());
        creditUser.setTransaction(transaction);

        LedgerEntry debitSystem = new LedgerEntry();
        debitSystem.setAccount(systemAccount);
        debitSystem.setType(LedgerEntryType.DEBIT);
        debitSystem.setAmount(transactionRequestDto.amount());
        debitSystem.setTransaction(transaction);

        ledgerEntryRepository.saveAll(List.of(creditUser, debitSystem));

        userAccount.setBalance(userAccount.getBalance() + transactionRequestDto.amount());
        systemAccount.setBalance(systemAccount.getBalance() - transactionRequestDto.amount());

        accountRepository.saveAll(List.of(userAccount, systemAccount));

        return new TransactionResponseDto(
                transaction.getId(),
                transaction.getType(),
                transactionRequestDto.amount(),
                userAccount.getBalance(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }

    @Transactional
    public TransactionResponseDto bonusTransaction(TransactionRequestDto transactionRequestDto, Account userAccount, Account systemAccount) {
        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.BONUS);
        transaction.setIdempotencyKey(transactionRequestDto.idempotencyKey());
        transaction.setStatus(Status.SUCCESS);

        transactionRepository.save(transaction);

        LedgerEntry creditUser = new LedgerEntry();
        creditUser.setAccount(userAccount);
        creditUser.setType(LedgerEntryType.CREDIT);
        creditUser.setAmount(transactionRequestDto.amount());
        creditUser.setTransaction(transaction);

        LedgerEntry debitSystem = new LedgerEntry();
        debitSystem.setAccount(systemAccount);
        debitSystem.setType(LedgerEntryType.DEBIT);
        debitSystem.setAmount(transactionRequestDto.amount());
        debitSystem.setTransaction(transaction);

        ledgerEntryRepository.saveAll(List.of(creditUser, debitSystem));

        userAccount.setBalance(userAccount.getBalance() + transactionRequestDto.amount());
        systemAccount.setBalance(systemAccount.getBalance() - transactionRequestDto.amount());

        accountRepository.saveAll(List.of(userAccount, systemAccount));

        return new TransactionResponseDto(
                transaction.getId(),
                transaction.getType(),
                transactionRequestDto.amount(),
                userAccount.getBalance(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }

    @Transactional
    public TransactionResponseDto spendTransaction(TransactionRequestDto transactionRequestDto, Account userAccount, Account systemAccount) {
        Account first;
        Account second;

        if (userAccount.getId().compareTo(systemAccount.getId()) < 0) {
            first = accountRepository.findByAccountId(userAccount.getId())
                                     .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            second = accountRepository.findByAccountId(systemAccount.getId())
                                      .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        } else {
            first = accountRepository.findByAccountId(systemAccount.getId())
                                     .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            second = accountRepository.findByAccountId(userAccount.getId())
                                      .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        }

        Account lockedUser = userAccount.getId().equals(first.getId()) ? first : second;

        if (lockedUser.getBalance() < transactionRequestDto.amount()) {
            throw new InsufficientFundsException("Insufficient balance");
        }

        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.SPEND);
        transaction.setStatus(Status.SUCCESS);
        transaction.setIdempotencyKey(transactionRequestDto.idempotencyKey());

        transactionRepository.save(transaction);

        LedgerEntry debitUser = new LedgerEntry();
        debitUser.setAccount(lockedUser);
        debitUser.setTransaction(transaction);
        debitUser.setAmount(transactionRequestDto.amount());
        debitUser.setType(LedgerEntryType.DEBIT);

        LedgerEntry creditSystem = new LedgerEntry();
        creditSystem.setType(LedgerEntryType.CREDIT);
        creditSystem.setTransaction(transaction);
        creditSystem.setAmount(transactionRequestDto.amount());
        creditSystem.setAccount(systemAccount);

        ledgerEntryRepository.saveAll(List.of(debitUser, creditSystem));

        lockedUser.setBalance(lockedUser.getBalance() - transactionRequestDto.amount());
        systemAccount.setBalance(systemAccount.getBalance() + transactionRequestDto.amount());

        accountRepository.saveAll(List.of(lockedUser, systemAccount));

        return new TransactionResponseDto(
                transaction.getId(),
                transaction.getType(),
                transactionRequestDto.amount(),
                lockedUser.getBalance(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }

    public BalanceResponseDto getBalance(String email, String assetCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Asset asset = assetRepository.findByCode(assetCode)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        Account account = accountRepository.findByUserIdAndAssetId(user.getId(), asset.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        return new BalanceResponseDto(
                user.getId(),
                asset.getName(),
                account.getBalance()
        );
    }

    public List<ListTransactionResponseDto> getTransactionHistory(String email) {
        List<Transaction> transactions = transactionRepository.findByUserEmail(email);

        return transactions.stream()
                .map(this::mappedToResponse)
                .toList();
    }

    private ListTransactionResponseDto mappedToResponse(Transaction transaction) {
        return new ListTransactionResponseDto(
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }
}
