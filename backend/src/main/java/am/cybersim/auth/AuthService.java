package am.cybersim.auth;

import am.cybersim.auth.dto.AuthResponse;
import am.cybersim.auth.dto.LoginRequest;
import am.cybersim.common.ApiException;
import am.cybersim.security.JwtTokenService;
import am.cybersim.user.User;
import am.cybersim.user.UserRepository;
import am.cybersim.user.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Administrator login. There is no self-registration: admin accounts are provisioned by the operator
 * (see {@code DemoDataInitializer}).
 *
 * <p>Security considerations:
 * <ul>
 *   <li>Unknown e-mail, wrong password and disabled account all return the same 401 message, so the endpoint
 *       cannot be used to discover which e-mails are registered.</li>
 *   <li>For unknown e-mails a dummy hash is still checked so that response time does not reveal the difference.</li>
 *   <li>Passwords and tokens are never logged.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokenService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-dummy-password");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordOk || !user.isEnabled()) {
            log.info("Failed login attempt");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid e-mail or password");
        }
        user.recordLogin(clock.instant());
        JwtTokenService.IssuedToken token = tokenService.issue(user);
        log.info("User id={} logged in", user.getId());
        return AuthResponse.bearer(token.value(), token.expiresAt(), UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return users.findById(userId).map(UserDto::from)
                .orElseThrow(() -> ApiException.notFound("User"));
    }
}
