package com.ject.oogachaka.notification.repository;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 푸시 받을 가족 구성원을 찾는다. family_member 엔티티가 아직 없어 SQL로 조회한다. */
@Repository
@RequiredArgsConstructor
public class FamilyPushRecipientQuery {

    private static final String PARENTS_OF_CHILD = """
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

    public Optional<Parents> findParents(Long childUserId) {
        List<Parent> rows = jdbcTemplate.query(PARENTS_OF_CHILD,
            (row, rowNumber) -> new Parent(row.getLong("parent_user_id"), row.getString("child_nickname")), childUserId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Parents(rows.get(0).childNickname(), rows.stream().map(Parent::userId).toList()));
    }

    public record Parents(String childNickname, List<Long> parentUserIds) {
    }

    private record Parent(Long userId, String childNickname) {
    }
}
