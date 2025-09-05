package hello.hackathon.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // kakao가 null이면 응답에서 생략
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long accessTtlSec;
    private long refreshTtlSec;
    private UserDto user;          // 항상 포함
    private KakaoDto kakao;        // 신규 가입일 때만 포함

    @JsonProperty("isNew")         // JSON 키를 "isNew"로 고정
    private boolean isNew;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private Long id;
        private String nickname;
        private String profileImageUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KakaoDto {
        private String id;
        private String nickname;
        private String profileImageUrl;
        private String thumbnailImageUrl;
    }
}
