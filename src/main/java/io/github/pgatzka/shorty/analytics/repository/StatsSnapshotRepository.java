package io.github.pgatzka.shorty.analytics.repository;

import io.github.pgatzka.shorty.analytics.domain.StatsSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Statistics snapshots. */
public interface StatsSnapshotRepository extends JpaRepository<StatsSnapshot, Long> {

    /** The newest snapshot of each of the given videos. */
    @Query("""
            select s from StatsSnapshot s
            where s.videoId in :videoIds
              and s.takenAt = (select max(s2.takenAt) from StatsSnapshot s2 where s2.videoId = s.videoId)
            """)
    List<StatsSnapshot> findLatest(@Param("videoIds") Collection<String> videoIds);
}
