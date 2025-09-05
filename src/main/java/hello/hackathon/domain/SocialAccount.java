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
public class SocialAccount extends BaseTime{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private SocialProvider provider;

    @Column(unique = true)
    private String providerId;

    //확장 대비 ManyToOne으로 해놓긴 했는데, 일단 카카오만 연동 가능하게 구현할 예정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="userEntity_id")
    private UserEntity userEntity;

}
