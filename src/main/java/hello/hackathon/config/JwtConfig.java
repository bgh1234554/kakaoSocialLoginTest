package hello.hackathon.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt") //application.yml에서 관련 값 들고 와 매핑
//토큰 발급을 위한 Jwt 관련 정보 불러오기
public class JwtConfig {
    private String issuer;
    private String secret;
    private long accessTtlSeconds;
    private long refreshTtlSeconds;
}
