package com.ject.oogachaka.family.service;

import com.ject.oogachaka.family.repository.FamilyRepository;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InviteCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 5;

    private final FamilyRepository familyRepository;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            StringBuilder candidate = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                candidate.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = candidate.toString();
            if (!familyRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("사용 가능한 가족 초대코드를 생성하지 못했습니다.");
    }
}
