package ues.edu.sv.education.service.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;

@Service
public class CustomUserDetailService implements UserDetailsService {
    private final UserAuthService userAuthService;

    public CustomUserDetailService(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return new CustomUserDetails(this.userAuthService.getUser(username));
    }
}
