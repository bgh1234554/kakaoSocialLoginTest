package hello.hackathon.dto;

import lombok.Data;

@Data
public class KakaoProfileDto {
    private String id;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String thumbnailImageUrl;
}
