package com.ject.oogachaka.auth.repository;

import com.ject.oogachaka.auth.entity.SocialAccount;
import com.ject.oogachaka.auth.enums.AuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 소셜 계정의 JPA 저장·조회 기능을 사용한다. Long은 SocialAccount의 DB 기본 키 타입이다.
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    // 제공자와 ID HMAC을 함께 비교하는 파생 쿼리다. 최초 로그인에는 계정이 없을 수 있어 Optional을 반환한다. SQL의 같은 복합 UNIQUE와 대응한다.
    Optional<SocialAccount> findByAuthProviderAndProviderUserIdHash(AuthProvider provider, String providerUserIdHash);
}
