package hello.hackathon.service;

import hello.hackathon.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

//우리 앱 자체 액세스 토큰과 리프레시 토큰 발급
@Service
public class TokenService {
    private final JwtConfig jwtConfig;
    private SecretKey key;
    public TokenService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    //JWT 서명/검증에 쓸 SecretKey를 앱 시작 시 생성해 캐싱
    @PostConstruct //스프링이 빈을 만들고 설정 주입이 끝난 직후 자동으로 호출
    public void init() {
        //시크릿 키가 16진수 문자열이므로 32비트 문자열이 되도록 수정
        byte[] keyBytes = HexFormat.of().parseHex(jwtConfig.getSecret());
        key = Keys.hmacShaKeyFor(keyBytes);
    }

    //액세스 토큰 발급
    public String createAccess(Long userId, String email, String nickname){
        Instant now = Instant.now();
        //Jwt로 액세스 토큰 생성
        return Jwts.builder()
                .setIssuer(jwtConfig.getIssuer())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(jwtConfig.getAccessTtlSeconds())))
                .claim("uid",userId).claim("email",email).claim("name",nickname)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    //리프레시 토큰 발급 (이것도 JWT로)
    public String createRefresh(Long userId){
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(jwtConfig.getIssuer())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(jwtConfig.getRefreshTtlSeconds())))
                .claim("uid",userId)
                .signWith(key,SignatureAlgorithm.HS256)
                .compact();
    }

    //Jwt 검증 로직
    public Claims parseAndValidate(String jwt){
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)                         // HS256 대칭키
                    .requireIssuer(jwtConfig.getIssuer())            // 우리 발급자만 허용
                    .setAllowedClockSkewSeconds(60)             // 60초 스큐 허용
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();
        } catch (JwtException e) { //invalid한 JWT일 경우 401 반환
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
        }
    }
}
