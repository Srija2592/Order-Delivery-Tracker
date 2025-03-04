package kafka.KafkaFullStack.Service;

import kafka.KafkaFullStack.Security.JwtProvider;
import kafka.KafkaFullStack.Model.User;
import kafka.KafkaFullStack.Repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userrepository;

    private final JwtProvider jwtProvider;
    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> userOptional=userrepository.findByUsername(username);
        User user=userOptional.orElseThrow(()->new UsernameNotFoundException("No user " +
                "Found with username : " + username));
        Optional<User> userOptional1 = userrepository.findByUsername(username);
        log.info("Attempting to find user with username: {}", username);

        try {
            return new org.springframework.security.core.userdetails.User(
                    user.getUsername(),
                    user.getPassword(),
                    true, true, true, true,
                    parseScopes(user.getRole().name())
            );
        } catch (Exception e) {
            log.error("Error during authentication", e);
            throw e;
        }

    }


//    @Override
//    public Collection<GrantedAuthority> convert(Jwt jwt) {
//        Collection<GrantedAuthority> authorities = defaultConverter.convert(jwt);
//        Map<String, Object> claims = jwt.getClaims();
//        if (claims.containsKey("scope") && claims.get("scope") instanceof String) {
//            String scopes = (String) claims.get("scope");
//            authorities.addAll(parseScopes(scopes));
//        }
//
//        return authorities;
//    }

    private Collection<GrantedAuthority> parseScopes(String scopes) {
        if (scopes.startsWith("ROLE_")) {
            return Collections.singleton(new SimpleGrantedAuthority(scopes));
        }
        log.info("User roles being parsed: {}", scopes);

        return Collections.singleton(new SimpleGrantedAuthority("ROLE_" + scopes.toUpperCase()));
    }

}
