package hello.hackathon.web;

import hello.hackathon.dto.LoginResponse;
import hello.hackathon.service.AuthService;
import hello.hackathon.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

//로그인 관련 컨트롤러
//해커톤에서 구현의 편리함을 위해, 프런트에서 인증 서버에서 Access Token을 받아서 백엔드로 넘겨준다.
//토큰의 경우에는 프런트엔드가 Kotlin이기 때문에 JSON 형식으로 넘겨준다.
//두 토큰과 함께, 프로필 사진과 이름을 넘겨준다.
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;

    //AuthController에서만 쓰이는 간단한 Dto라 굳이 Dto 클래스로 만들지 않고 record로 구현했습니다.

    //로그인 - 카카오 액세스 토큰을 받아 LoginResponse에 AccessToken, RefreshToken, 프로필 정보 반환
    public record KakaoLoginRequest(@NotBlank String kakaoAccessToken){}
    @PostMapping("/kakao/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody KakaoLoginRequest kakaoLoginRequest){
        LoginResponse response = authService.loginWithKakao(kakaoLoginRequest.kakaoAccessToken);
        return ResponseEntity.ok(response);
    }

    //Refresh Token 재발급(회전): 새 AT + 새 RT 발급 (기존 RT는 회전 처리)
    public record RefreshRequest(@NotBlank String refreshToken) {}
    @PostMapping("/token/refresh")
    public ResponseEntity<Map<String,Object>> refresh(@Valid @RequestBody AuthController.RefreshRequest body){
        Map<String,Object> pair = authService.refresh(body.refreshToken());
        // pair = { accessToken, refreshToken, accessTtlSec, refreshTtlSec }
        return ResponseEntity.ok(pair);
    }

    //Access Token만 재발급
    //AT만 재발급: RT는 그대로 유지 (무회전)
    public record AccessRequest(@NotBlank String refreshToken) {}
    @PostMapping("/token/access")
    public ResponseEntity<Map<String,Object>> reissue(@Valid @RequestBody AccessRequest body){
        Map<String,Object> out = authService.reissueAccessToken(body.refreshToken());
        // out = { accessToken, accessTtlSec }
        return ResponseEntity.ok(out);
    }

    //로그아웃 - 액세스 토큰을 받은 뒤, 가장 최근에 발급된 리프레시 토큰도 폐기
    @PostMapping("/logout")
    public ResponseEntity<Map<String,String>> logout(@RequestHeader(HttpHeaders.AUTHORIZATION)String accessToken){
        Long uid = uidFromAccessToken(accessToken);
        return ResponseEntity.ok(authService.logout(uid));
    }

    private Long uidFromAccessToken(String accessToken) {
        if(accessToken == null || !accessToken.startsWith("Bearer ")){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token");
        }
        String at =  accessToken.substring(7);
        Claims claims = tokenService.parseAndValidate(at);
        return claims.get("uid", Long.class);
    }
}
