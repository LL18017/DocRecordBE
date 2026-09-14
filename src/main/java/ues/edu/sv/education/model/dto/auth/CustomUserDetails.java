package ues.edu.sv.education.model.dto.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.entity.User;

import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {
    private User user;
    public CustomUserDetails(User user) {
        this.user=user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.user.getRoles()
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_"+role.getName()))
                .toList();
    }
    public List<String> getRoles() {
        return this.user.getRoles()
                .stream()
                .map(Role::getName)
                .toList();
    }

    @Override
    public @Nullable String getPassword() {
        return this.user.getPassword();
    }

    //para este caso userName es el identificador de User
    @Override
    public String getUsername() {
        return this.user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    /**
     * Una cuenta desactivada por un administrador esta "bloqueada", no "sin
     * confirmar" (HU-05 criterio 3).
     *
     * Va aqui y no en isEnabled() porque PasswordAuthProvider ya da mensajes
     * distintos para cada uno: "Usuario bloqueado" frente a "Usuario no ha
     * confirmado su cuenta aun". Con las dos cosas en isEnabled(), a quien
     * acaban de desactivar se le pediria confirmar un correo que confirmo hace
     * meses, y se quedaria dandole vueltas a un enlace que no existe.
     */
    @Override
    public boolean isAccountNonLocked() {
        return user.isActivo();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    public User getUser(){
        return user;
    }
}
