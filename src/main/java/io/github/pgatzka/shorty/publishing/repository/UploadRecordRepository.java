package io.github.pgatzka.shorty.publishing.repository;

import io.github.pgatzka.shorty.publishing.domain.UploadRecord;
import io.github.pgatzka.shorty.publishing.domain.UploadStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Upload history. */
public interface UploadRecordRepository extends JpaRepository<UploadRecord, Long> {

    Optional<UploadRecord> findFirstByOrderByIdDesc();

    List<UploadRecord> findAllByOrderByIdDesc(Pageable pageable);

    List<UploadRecord> findByStatusOrderByIdDesc(UploadStatus status, Pageable pageable);

    List<UploadRecord> findByVideoFile(String videoFile);

    List<UploadRecord> findByStatusIn(List<UploadStatus> statuses);
}
