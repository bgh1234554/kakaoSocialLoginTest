package hello.hackathon.repository;

import hello.hackathon.domain.RefreshToken;
import hello.hackathon.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {
    Optional<RefreshToken> findTopByUserEntityOrderByIssuedAtDesc(UserEntity u);

    Optional<RefreshToken> findByUserEntityAndToken(UserEntity userEntity, String token);
}
