package fun.rooe.admin.model;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Represents the union of department-based and self-only data access.
 */
public record UserDataScope(
        Mode mode, List<Long> deptIds, Long selfUserId, Long selfDeptId)
        implements Serializable {

    public UserDataScope(Mode mode, List<Long> deptIds) {
        this(mode, deptIds, null, null);
    }

    public UserDataScope(Mode mode, List<Long> deptIds, Long selfUserId) {
        this(mode, deptIds, selfUserId, null);
    }

    public UserDataScope {
        mode = Objects.requireNonNullElse(mode, Mode.NONE);
        LinkedHashSet<Long> normalizedIds = new LinkedHashSet<>();
        if (deptIds != null) {
            for (Object deptId : deptIds) {
                if (deptId instanceof Number number) {
                    normalizedIds.add(number.longValue());
                }
            }
        }
        deptIds = List.copyOf(normalizedIds);
        switch (mode) {
            case ALL -> {
                deptIds = List.of();
                selfUserId = null;
                selfDeptId = null;
            }
            case LIMITED -> {
                if (deptIds.isEmpty()) {
                    mode = selfUserId == null ? Mode.NONE : Mode.SELF;
                }
                if (selfUserId == null) {
                    selfDeptId = null;
                }
            }
            case SELF -> {
                deptIds = List.of();
                if (selfUserId == null) {
                    mode = Mode.NONE;
                    selfDeptId = null;
                }
            }
            case NONE -> {
                deptIds = List.of();
                selfUserId = null;
                selfDeptId = null;
            }
        }
    }

    public static UserDataScope all() {
        return new UserDataScope(Mode.ALL, List.of(), null, null);
    }

    public static UserDataScope limited(Collection<Long> deptIds) {
        return limited(deptIds, null);
    }

    public static UserDataScope limited(Collection<Long> deptIds, Long selfUserId) {
        return limited(deptIds, selfUserId, null);
    }

    public static UserDataScope limited(
            Collection<Long> deptIds, Long selfUserId, Long selfDeptId) {
        return new UserDataScope(
                Mode.LIMITED, List.copyOf(deptIds), selfUserId, selfDeptId);
    }

    public static UserDataScope self(Long userId) {
        return self(userId, null);
    }

    public static UserDataScope self(Long userId, Long deptId) {
        return new UserDataScope(
                Mode.SELF, List.of(), Objects.requireNonNull(userId), deptId);
    }

    public static UserDataScope none() {
        return new UserDataScope(Mode.NONE, List.of(), null, null);
    }

    public boolean isAll() {
        return mode == Mode.ALL;
    }

    public boolean isLimited() {
        return mode == Mode.LIMITED;
    }

    public boolean isSelf() {
        return mode == Mode.SELF;
    }

    public boolean isNone() {
        return mode == Mode.NONE;
    }

    public boolean permits(Long deptId) {
        return isAll() || (deptId != null && isLimited() && deptIds.contains(deptId));
    }

    public boolean permitsUser(Long userId, Long deptId) {
        return permits(deptId) || (selfUserId != null && selfUserId.equals(userId));
    }

    public boolean permitsDeptRead(Long deptId) {
        return permits(deptId)
                || (selfDeptId != null && selfDeptId.equals(deptId));
    }

    public enum Mode {
        ALL,
        LIMITED,
        SELF,
        NONE
    }
}
