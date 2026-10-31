package testsupport.crypto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** V2의 실제 디바이스 토큰 해시 컬럼에 대한 스키마 검증·조회 전용 테스트 매핑 */
@Entity
@Immutable
@Table(name = "device_token")
public class CryptoTestDeviceToken {

    @Id
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    protected CryptoTestDeviceToken() {
    }
}
