package testsupport.crypto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** V2의 실제 소셜 해시 컬럼에 대한 스키마 검증·조회 전용 테스트 매핑 */
@Entity
@Immutable
@Table(name = "social_account")
public class CryptoTestSocialAccount {

    @Id
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "provider_user_id_hash", nullable = false, length = 64)
    private String providerUserIdHash;

    protected CryptoTestSocialAccount() {
    }
}
