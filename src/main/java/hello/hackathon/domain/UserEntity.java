package hello.hackathon.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity extends BaseTime{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String nickname;
    //카카오 소셜 로그인만 활성화할거라 password 필요 없음.

    // [추가] 프로필 이미지(링크로 저장 권장; BLOB 비권장)
    @Column(length = 1024)
    private String profileImageUrl;

    // [추가] 썸네일(옵션)
    @Column(length = 1024)
    private String thumbnailImageUrl;
}
