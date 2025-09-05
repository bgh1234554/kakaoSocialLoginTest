package hello.hackathon.service;

import com.fasterxml.jackson.databind.JsonNode;
import hello.hackathon.config.JwtConfig;
import hello.hackathon.domain.RefreshToken;
import hello.hackathon.domain.SocialAccount;
import hello.hackathon.domain.SocialProvider;
import hello.hackathon.domain.UserEntity;
import hello.hackathon.dto.KakaoProfileDto;
import hello.hackathon.dto.LoginResponse;
import hello.hackathon.repository.RefreshTokenRepository;
import hello.hackathon.repository.SocialAccountRepository;
import hello.hackathon.repository.UserEntityRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

//카카오 소셜 로그인 관리
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserEntityRepository users;
    private final SocialAccountRepository socialAccounts;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokenService;
    private final JwtConfig jwtConfig;

    // 카카오 사용자 정보 조회 API 엔드포인트
    @Value("${kakao.userinfo-uri}")
    private String kakaoUserinfoUri;

    //스프링부트가 자동 제공해주는 WebClient.Builder
    private final WebClient.Builder webClientBuilder;

    //로그인 - 신규 게정이면 카카오 프로필을 불러와 DB에 채우고,
    //기존 회원이면 DB에서 관련 프로필을 불러와서 응답
    @Transactional
    public LoginResponse loginWithKakao(String kakaoAccessToken) {
        KakaoProfileDto info = fetchKakaoProfile(kakaoAccessToken);
        if(info.getId()==null){
            throw new ResponseStatusException(UNAUTHORIZED,"invalid kakao token");
        }

        //DB에 계정 있는지 조회
        Optional<SocialAccount> optional = socialAccounts.findByProviderAndProviderId(SocialProvider.KAKAO,info.getId());
        boolean newUser = optional.isEmpty();

        //없으면 카카오 프로필 기반으로 신규 user 생성, 아니면 기존 DB에서 로드
        UserEntity userEntity;
        if(newUser){
            //생성하면서 기본 닉네임과 프로필을 카카오에서 가져온다.
            userEntity = users.save(UserEntity.builder()
                    .email(info.getEmail())
                    .nickname(info.getNickname())
                    .profileImageUrl(info.getProfileImageUrl())
                    .thumbnailImageUrl(info.getThumbnailImageUrl())
                    .build());
            socialAccounts.save(SocialAccount.builder()
                    .provider(SocialProvider.KAKAO)
                    .providerId(info.getId())
                    .userEntity(userEntity)
                    .build());
        }
        else{
            userEntity = optional.get().getUserEntity();
        }

        String accessToken = tokenService.createAccess(userEntity.getId(), userEntity.getEmail(), userEntity.getNickname());
        String refreshToken = tokenService.createRefresh(userEntity.getId());
        Instant now = Instant.now();
        refreshTokens.save(RefreshToken.builder()
                .userEntity(userEntity)
                .token(sha256(refreshToken))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtConfig.getRefreshTtlSeconds()))
                .build());
        LoginResponse.KakaoDto kakaoDto = new LoginResponse.KakaoDto();
        //신규 유저일 경우 kakaoDto 포함
        if(newUser){
            kakaoDto = new LoginResponse.KakaoDto(
                    info.getId(), info.getNickname(),
                    info.getProfileImageUrl(), info.getThumbnailImageUrl()
            );
        }
        return new LoginResponse(
                accessToken,refreshToken,
                jwtConfig.getAccessTtlSeconds(), jwtConfig.getRefreshTtlSeconds(),
                new LoginResponse.UserDto(userEntity.getId(), userEntity.getNickname(), userEntity.getProfileImageUrl()),
                kakaoDto, newUser
        );
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e){ throw new RuntimeException(e); }
    }

    //액세스 토큰만 재발급 하는 로직
    public Map<String,Object> reissueAccessToken(String refreshToken){
        if(refreshToken==null || refreshToken.isBlank()){
            throw new ResponseStatusException(UNAUTHORIZED,"invalid refresh token");
        }
        Claims claims = tokenService.parseAndValidate(refreshToken);
        Long userId = claims.get("uid", Long.class);

        //예외처리
        UserEntity userEntity = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED,"user not found"));
        RefreshToken rt = refreshTokens.findByUserEntityAndToken(userEntity,sha256(refreshToken))
                .orElseThrow(()-> new ResponseStatusException(UNAUTHORIZED, "refreshToken not found"));
        if (rt.getExpiresAt().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh expired");
        if (rt.getRevokedAt() != null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh revoked");

        //새 Access Token 발급
        String newAccessToken = tokenService.createAccess(userEntity.getId(), userEntity.getEmail(), userEntity.getNickname());

        return Map.of(
                "accessToken",newAccessToken,
                "accessTtlSec",jwtConfig.getAccessTtlSeconds()
        );
    }

    //리프레시 토큰 재발급 로직 -> 동시에 액세스 토큰도 재발급
    public Map<String,Object> refresh(String refreshToken){
        if(refreshToken==null || refreshToken.isBlank()){
            throw new ResponseStatusException(UNAUTHORIZED,"invalid refresh token");
        }
        Claims claims = tokenService.parseAndValidate(refreshToken);
        Long userId = claims.get("uid", Long.class);

        //예외처리
        UserEntity userEntity = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED,"user not found"));
        RefreshToken rt = refreshTokens.findByUserEntityAndToken(userEntity,sha256(refreshToken))
                .orElseThrow(()-> new ResponseStatusException(UNAUTHORIZED, "refreshToken not found"));
        if (rt.getRotatedAt()!=null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh already rotated");
        if (rt.getExpiresAt().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh already expired");

        rt.rotate(Instant.now());
        refreshTokens.save(rt);

        String newAccessToken =  tokenService.createAccess(userEntity.getId(), userEntity.getEmail(), userEntity.getNickname());
        String newRefreshToken = tokenService.createRefresh(userEntity.getId());
        refreshTokens.save(RefreshToken.builder()
                .userEntity(userEntity)
                .token(sha256(newRefreshToken))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(jwtConfig.getRefreshTtlSeconds()))
                .build());
        return Map.of(
                "accessToken",newAccessToken,
                "refreshToken",newRefreshToken,
                "accessTtlSec",jwtConfig.getAccessTtlSeconds(),
                "refreshTtlSec",jwtConfig.getRefreshTtlSeconds()
        );
    }
    //로그아웃 시 리프레시 토큰 만료
    public Map<String,String> logout(Long userId){
        UserEntity u = users.findById(userId).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "user not found"));
        refreshTokens.findTopByUserEntityOrderByIssuedAtDesc(u)
                .ifPresent(refreshToken -> {refreshToken.revoke(Instant.now());refreshTokens.save(refreshToken);});
        return Map.of("status","ok");
    }

    //JsonNode - DTO 없이도 중첩 JSON을 안전하게 한 줄로 뽑아내는 도구.
    //액세스 토큰을 기반으로 유저 정보를 뽑아내는 토큰
    private KakaoProfileDto fetchKakaoProfile(String kakaoAccessToken) {
       JsonNode n = webClientBuilder.build()
               .get().uri(kakaoUserinfoUri) // HTTP GET 요청 설정, 요청 URL 설정
               .headers(h -> h.setBearerAuth(kakaoAccessToken)) // Bearer 토큰 인증 헤더 설정
               .accept(MediaType.APPLICATION_JSON) // Accept 헤더를 application/json으로 설정
               .retrieve() // 응답 받기 시작
               .onStatus(HttpStatusCode::isError, resp -> // 에러 상태 코드(4xx, 5xx) 처리
                       resp.bodyToMono(String.class)
                               .defaultIfEmpty("")
                               .flatMap(body -> Mono.error(new ResponseStatusException(
                                       resp.statusCode(), "kakao userinfo error: " + body))))
               .bodyToMono(JsonNode.class) // 응답 본문을 JsonNode로 변환
               .block(); // 비동기 결과를 동기적으로 기다림
       KakaoProfileDto dto = new KakaoProfileDto();
       //자세한 사항은 예제 참조 - https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api#req-user-info-sample
       dto.setId(n.path("id").asText(null));
       dto.setEmail(n.at("/kakao_account/email").asText(null));
       dto.setNickname(n.at("/kakao_account/profile/nickname").asText(null));
       dto.setProfileImageUrl(n.at("/kakao_account/profile/profile_image_url").asText(null));
       dto.setThumbnailImageUrl(n.at("/kakao_account/profile/thumbnail_image_url").asText(null));
       return dto;
   }
}
