package com.service.internalWallet.service;

import java.util.List;

import com.service.internalWallet.model.Account;
import com.service.internalWallet.repository.AccountRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public List<Account> testByRepositoryLayer() {
        return accountRepository.findAll();
    }
}
