package io.getarrays.securecapita.asserts.repo;

import io.getarrays.securecapita.asserts.model.AssertEntity;
import io.getarrays.securecapita.dto.AssetItemStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;

@Repository
public interface AssertsJpaRepository extends JpaRepository<AssertEntity, Long> {
    @Query("SELECT COUNT(a) FROM AssertEntity a WHERE LOWER(a.assertType) = 'fixed'")
    int countFixedAsserts();


    @Query("SELECT COUNT(a) FROM AssertEntity a WHERE LOWER(a.assertType) = 'current'")
    int countCurrentAsserts();


    @Query("SELECT new io.getarrays.securecapita.dto.AssetItemStat(" +
            "a.assetDisc, " +
            "COUNT(a.id)) FROM AssertEntity a " +
            "GROUP BY a.assetDisc")
    ArrayList<AssetItemStat> findAssertItemStatsByAssetDisc();
}