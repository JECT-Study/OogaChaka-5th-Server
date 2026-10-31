/**
 * 여러 도메인에서 사용하는 AES 암호화·복호화와 용도별 HMAC 처리를 관리한다.
 *
 * <p>EncryptionService는 AES-256-GCM 암호화·복호화를 담당하고,
 * EncryptionConverter는 해당 서비스를 사용하여 JPA 저장·조회 값을 변환한다.
 * HmacService는 원본 문자열을 정규화하지 않고 용도별 HMAC-SHA256을 생성한다.
 * EncryptionException은 암호화·복호화·해시 실패를 표현한다.
 * 암호화 키 설정 클래스는 global.config에서 관리한다.
 */
package com.ject.oogachaka.global.crypto;
