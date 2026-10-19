package fun.rooe.admin.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserDataScopeTest {

    @Test
    void emptyLimitedScopeBecomesNone() {
        UserDataScope scope = UserDataScope.limited(List.of());

        assertTrue(scope.isNone());
        assertFalse(scope.permits(1L));
    }

    @Test
    void limitedScopeDeduplicatesDepartments() {
        UserDataScope scope = UserDataScope.limited(List.of(2L, 2L, 3L));

        assertEquals(List.of(2L, 3L), scope.getDeptIds());
        assertTrue(scope.permits(2L));
        assertFalse(scope.permits(4L));
    }

    @Test
    void allScopeExplicitlyPermitsAnyDepartment() {
        UserDataScope scope = UserDataScope.all();

        assertTrue(scope.isAll());
        assertTrue(scope.permits(99L));
    }

    @Test
    void selfScopeOnlyPermitsTheCurrentUser() {
        UserDataScope scope = UserDataScope.self(7L, 2L);

        assertTrue(scope.isSelf());
        assertTrue(scope.permitsUser(7L, 99L));
        assertFalse(scope.permitsUser(8L, 99L));
        assertFalse(scope.permits(99L));
        assertTrue(scope.permitsDeptRead(2L));
    }

    @Test
    void limitedScopeCanAlsoIncludeTheCurrentUser() {
        UserDataScope scope = UserDataScope.limited(List.of(2L), 7L, 3L);

        assertTrue(scope.permitsUser(8L, 2L));
        assertTrue(scope.permitsUser(7L, 3L));
        assertFalse(scope.permitsUser(8L, 3L));
        assertTrue(scope.permitsDeptRead(3L));
    }
}
