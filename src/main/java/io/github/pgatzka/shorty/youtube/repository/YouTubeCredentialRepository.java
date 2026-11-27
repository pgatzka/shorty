package io.github.pgatzka.shorty.youtube.repository;

import io.github.pgatzka.shorty.youtube.domain.YouTubeCredential;
import org.springframework.data.jpa.repository.JpaRepository;

/** Stores the single YouTube credential. */
public interface YouTubeCredentialRepository extends JpaRepository<YouTubeCredential, Integer> {}
