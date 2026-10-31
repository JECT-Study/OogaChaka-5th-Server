package com.ject.oogachaka.notification.repository;

import com.ject.oogachaka.notification.service.ParentPushRecipientPort;
import com.ject.oogachaka.notification.service.ParentPushRecipients;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** User/FamilyMember 엔티티 소유권을 침범하지 않는 임시 가족 조회 어댑터 */
@Repository
@RequiredArgsConstructor
public class FamilyParentPushRecipientQuery implements ParentPushRecipientPort {

    private static final String PARENT_QUERY = """
        SELECT parent.id AS parent_user_id, child.nickname AS child_nickname
        FROM users child
        JOIN family_member child_member ON child_member.id = child.family_member_id
            AND child_member.family_role = 'CHILD'
        JOIN family_member parent_member ON parent_member.family_id = child_member.family_id
            AND parent_member.family_role = 'PARENT'
        JOIN users parent ON parent.family_member_id = parent_member.id
        WHERE child.id = ?
        """;

    private final JdbcTemplate jdbcTemplate;
    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ParentPushRecipients> findByChildUserId(Long childUserId) {
        List<ParentRow> parents = jdbcTemplate.query(PARENT_QUERY,
            (row, rowNumber) -> new ParentRow(row.getLong("parent_user_id"), row.getString("child_nickname")),
            childUserId);
        if (parents.isEmpty()) {
            return Optional.empty();
        }
        // Native/JDBC로 token 컬럼을 읽지 않는다. 엔티티 조회로 EncryptionConverter를 거친다.
        var devices = deviceTokenRepository.findAllByUserIdIn(parents.stream().map(ParentRow::userId).toList());
        return Optional.of(new ParentPushRecipients(parents.get(0).childNickname(), devices));
    }

    private record ParentRow(Long userId, String childNickname) {
    }
}
