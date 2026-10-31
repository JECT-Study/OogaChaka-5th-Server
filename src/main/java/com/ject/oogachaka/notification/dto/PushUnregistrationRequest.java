package com.ject.oogachaka.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushUnregistrationRequest(
    @NotBlank @Size(max = 4096) @Pattern(regexp = "\\S+") String token
) {
    @Override
    public String toString() {
        return "PushUnregistrationRequest[]";
    }
}
