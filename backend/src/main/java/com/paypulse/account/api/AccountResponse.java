package com.paypulse.account.api;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountResponse {
    private String id;
    private String label;
    private String accountNumber;
    private String currency;
    private String status;
}