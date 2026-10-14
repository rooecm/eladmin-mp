package fun.rooe.admin.modules.security.security;

import fun.rooe.admin.config.security.security.TokenFilter;
import fun.rooe.admin.config.security.security.TokenProvider;
import jakarta.servlet.FilterChain;
import fun.rooe.admin.config.security.config.SecurityProperties;
import fun.rooe.admin.config.security.service.OnlineSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenFilterTest {

    private final TokenProvider tokenProvider = mock(TokenProvider.class);
    private final OnlineSessionService sessions = mock(OnlineSessionService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private TokenFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        SecurityProperties properties = new SecurityProperties();
        properties.setHeader("Authorization");
        properties.setTokenStartWith("Bearer");
        filter = new TokenFilter(tokenProvider, properties, sessions);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAndRenewsAnOnlineSession() throws Exception {
        request.addHeader("Authorization", "Bearer signed-token");
        when(tokenProvider.loginKey("signed-token")).thenReturn("online:admin:session");
        when(sessions.isOnline("online:admin:session")).thenReturn(true);
        Authentication authentication = new UsernamePasswordAuthenticationToken("admin", "signed-token", Collections.emptyList());
        when(tokenProvider.getAuthentication("signed-token")).thenReturn(authentication);

        filter.doFilter(request, response, chain);

        assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
        verify(tokenProvider).checkRenewal("signed-token");
        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesLoggedOutSessionsUnauthenticated() throws Exception {
        request.addHeader("Authorization", "Bearer signed-token");
        when(tokenProvider.loginKey("signed-token")).thenReturn("online:admin:session");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(sessions).isOnline("online:admin:session");
        verify(tokenProvider, never()).checkRenewal(anyString());
        verify(tokenProvider, never()).getAuthentication(anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void skipsSessionLookupWhenTokenIsAbsent() throws Exception {
        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(tokenProvider, sessions);
        verify(chain).doFilter(request, response);
    }

    @Test
    void skipsSessionLookupForAnotherAuthorizationScheme() throws Exception {
        request.addHeader("Authorization", "Basic credentials");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(tokenProvider, sessions);
        verify(chain).doFilter(request, response);
    }
}
