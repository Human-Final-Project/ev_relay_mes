package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.LoginRequestDto;
import com.human.ev_relay_mes.Dto.Request.PasswordChangeRequestDto;
import com.human.ev_relay_mes.Dto.Response.LoginResponseDto;
import com.human.ev_relay_mes.Security.CustomUserDetails;
import com.human.ev_relay_mes.Service.LoginService;
import com.human.ev_relay_mes.Service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;
    private final MemberService memberService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;

    // 로그인 전에 POST 요청에 사용할 CSRF 토큰 정보를 발급한다.
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
        );
    }

    // 사용자 인증에 성공하면 인증 정보를 HTTP 세션에 저장한다.
    @PostMapping("/login")
    public LoginResponseDto login(
            @Valid @RequestBody LoginRequestDto dto,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = loginService.authenticate(dto);
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return loginService.toResponse((CustomUserDetails) authentication.getPrincipal());
    }

    // 현재 세션과 인증 정보를 제거해 로그아웃 처리한다.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody PasswordChangeRequestDto dto,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        memberService.changePassword(userDetails.getMemberId(), dto);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    // 현재 세션에 로그인된 사용자 정보를 반환한다.
    @GetMapping("/me")
    public LoginResponseDto me(Authentication authentication) {
        return loginService.toResponse((CustomUserDetails) authentication.getPrincipal());
    }

    // 계정 정보가 일치하지 않거나 사용 불가능한 계정이면 동일한 JSON 401 응답을 반환한다.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "status", HttpStatus.UNAUTHORIZED.value(),
                "error", "UNAUTHORIZED",
                "message", "로그인 정보를 확인해 주세요."
        ));
    }
}
