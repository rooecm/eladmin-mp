package fun.rooe.admin.modules.security.service;

import fun.rooe.admin.framework.security.model.AuthenticatedUser;

public interface UserCacheService {
    AuthenticatedUser getUserForSession(long userId, long sessionAuthVersion);
}
