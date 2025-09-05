package hello.hackathon.repository;

import hello.hackathon.domain.SocialAccount;
import hello.hackathon.domain.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    Optional<SocialAccount> findByProviderAndProviderId(SocialProvider provider, String providerId);
}
