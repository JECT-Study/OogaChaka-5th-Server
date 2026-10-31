package testsupport.crypto;

import com.ject.oogachaka.global.crypto.EncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 애플리케이션의 엔티티 스캔 범위 밖에 둔 통합 테스트 전용 엔티티 */
@Entity
@Table(name = "crypto_test_value")
public class CryptoTestValue {

    @Id
    private Long id;

    @Convert(converter = EncryptionConverter.class)
    @Column(columnDefinition = "TEXT")
    private String secret;

    protected CryptoTestValue() {
    }

    public CryptoTestValue(Long id, String secret) {
        this.id = id;
        this.secret = secret;
    }

    public String getSecret() {
        return secret;
    }
}
