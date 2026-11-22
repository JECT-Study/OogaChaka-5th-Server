package com.ject.oogachaka.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ject.oogachaka.family.repository.FamilyRepository;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 초대코드 형식과 중복 시 재시도를 검증한다. 저장소의 코드 존재 여부는 목 객체로 대체한다. */
@ExtendWith(MockitoExtension.class)
class InviteCodeGeneratorTest {

    @Mock
    private FamilyRepository familyRepository;

    @InjectMocks
    private InviteCodeGenerator generator;

    // 사용 중인 코드가 없으면 0/O/1/I를 제외한 허용 문자 8자리로 발급한다.
    @Test
    void 허용_문자로_이루어진_8자리_코드를_반환한다() {
        assertThat(generator.generate()).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
    }

    // 처음 뽑은 코드가 이미 등록되어 있으면 다른 미사용 코드를 확인한 뒤 반환한다.
    @Test
    void 중복된_코드를_다시_뽑는다() {
        Set<String> usedCodes = new HashSet<>();
        when(familyRepository.existsByInviteCode(anyString())).thenAnswer(invocation -> {
            String candidate = invocation.getArgument(0);
            if (usedCodes.isEmpty()) {
                usedCodes.add(candidate);
            }
            return usedCodes.contains(candidate);
        });

        String code = generator.generate();

        assertThat(usedCodes).doesNotContain(code);
        verify(familyRepository).existsByInviteCode(code);
    }

    // 다섯 번 뽑은 후보가 모두 사용 중이면 중복 코드를 반환하지 않고 예외로 중단한다.
    @Test
    void 다섯_번_중복되면_실패한다() {
        when(familyRepository.existsByInviteCode(anyString())).thenReturn(true);

        assertThatIllegalStateException().isThrownBy(generator::generate);
        verify(familyRepository, times(5)).existsByInviteCode(anyString());
    }
}
