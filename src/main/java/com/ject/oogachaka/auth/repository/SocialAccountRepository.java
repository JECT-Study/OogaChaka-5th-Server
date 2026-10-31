package com.ject.oogachaka.auth.repository;

import com.ject.oogachaka.auth.entity.SocialAccount;
import com.ject.oogachaka.auth.enums.AuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByAuthProviderAndProviderUserIdHash(AuthProvider provider, String providerUserIdHash);
}
