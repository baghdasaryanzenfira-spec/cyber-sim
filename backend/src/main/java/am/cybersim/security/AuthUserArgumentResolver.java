package am.cybersim.security;

import am.cybersim.user.Role;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets controllers declare an {@link AuthUser} parameter instead of parsing the JWT themselves. */
@Component
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthUser.class.equals(parameter.getParameterType());
    }

    @Override
    public AuthUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
        var jwt = jwtAuth.getToken();
        return new AuthUser(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString(JwtTokenService.CLAIM_EMAIL),
                Role.valueOf(jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE)));
    }
}
