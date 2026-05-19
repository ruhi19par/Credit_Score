package com.credbridge.backend.application;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateApplicationStatusRequestDto {

    @NotNull
    private ApplicationStatus status;

    private String reviewNotes;
}
