package com.credbridge.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequestDto {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;
}
