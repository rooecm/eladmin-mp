package fun.rooe.admin.modules.security.service;

import fun.rooe.admin.framework.security.model.UserSnapshot;

public interface UserCacheService {
    UserSnapshot getUserForSession(long userId, long sessionAuthVersion);
}
