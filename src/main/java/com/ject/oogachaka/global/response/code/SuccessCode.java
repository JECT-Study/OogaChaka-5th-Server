package com.ject.oogachaka.global.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {

    OK(HttpStatus.OK, "SUCCESS", "요청이 성공했습니다."),
    CREATED(HttpStatus.CREATED, "CREATED", "생성이 완료되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
