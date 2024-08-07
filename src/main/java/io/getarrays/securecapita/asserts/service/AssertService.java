package io.getarrays.securecapita.asserts.service;

import io.getarrays.securecapita.asserts.model.AssertEntity;
import io.getarrays.securecapita.asserts.model.Inspection;
import io.getarrays.securecapita.asserts.model.Station;
import io.getarrays.securecapita.asserts.repo.AssertEntityRepository;
import io.getarrays.securecapita.asserts.repo.AssertsJpaRepository;
import io.getarrays.securecapita.asserts.repo.InspectionRepository;
import io.getarrays.securecapita.asserts.repo.StationRepository;
import io.getarrays.securecapita.domain.User;
import io.getarrays.securecapita.dto.AssetItemStat;
import io.getarrays.securecapita.dto.AssetsStats;
import io.getarrays.securecapita.dto.UserDTO;
import io.getarrays.securecapita.exception.CustomMessage;
import io.getarrays.securecapita.jasper.downloadtoken.DownloadToken;
import io.getarrays.securecapita.jasper.downloadtoken.DownloadTokenRepository;
import io.getarrays.securecapita.jasper.downloadtoken.DownloadTokenService;
import io.getarrays.securecapita.officelocations.OfficeLocation;
import io.getarrays.securecapita.officelocations.OfficeLocationRepository;
import io.getarrays.securecapita.repository.implementation.UserRepository1;
import io.getarrays.securecapita.roles.prerunner.ROLE_AUTH;
import io.getarrays.securecapita.userlogs.ActionType;
import io.getarrays.securecapita.userlogs.UserLogService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class AssertService implements AssertServiceInterface {

    private final AssertEntityRepository assertRepository;
    private final InspectionRepository inspectionRepository;
    private final StationRepository stationRepository;
    private final AssertEntityRepository assertEntityRepository;
    private final AssertsJpaRepository assertsJpaRepository;
    private final UserLogService userLogService;
    private final UserRepository1 userRepository1;
    private final OfficeLocationRepository officeLocationRepository;

    /* updating the user */
    public AssertEntity updateAssertEntity(AssertEntity assertEntity) {

        AssertEntity updatedAssertEntity = assertRepository.save(assertEntity);
        userLogService.addLog(ActionType.UPDATED, "updated assert successfully.");
        return updatedAssertEntity;
    }


    /* to create user */
    public ResponseEntity<?> createAssert(AssertEntity newAssert) throws Exception {
        //check duplicate
        Optional<AssertEntity> duplicatedAssert = assertsJpaRepository.findByAssetAndStation(newAssert.getAssetNumber(), newAssert.getSelectedStationID());
        if (duplicatedAssert.isPresent()) {
            return ResponseEntity.status(422).body(new CustomMessage("Found Duplicate Entry. Please check again."));
        }
        Optional<Station> optionalStation = stationRepository.findById(newAssert.getSelectedStationID());
        if (optionalStation.isEmpty()) {
            throw new Exception("Station not found");
        }
        Optional<OfficeLocation> optionalOfficeLocation = officeLocationRepository.findByStationAndName(optionalStation.get().getStation_id(), newAssert.getLocation());
        if (optionalStation.isEmpty()) {
            throw new Exception("Office Location not found");
        }
        newAssert.setOfficeLocation(optionalOfficeLocation.get());
        newAssert.setStation(optionalStation.get());
        User user = userRepository1.findById(((UserDTO) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId()).get();
        newAssert.setPreparedBy(user);
        AssertEntity createdAssert = assertRepository.save(newAssert);
        userLogService.addLog(ActionType.CREATED, "created assert successfully. Assert: " + newAssert.getAssetDisc());
        return ResponseEntity.ok(createdAssert);
    }


//    @Override
//    public void addInspectionToAseertEnity(Long id, Inspection inspection) {
//        AssertEntity assertEntity = assertRepository.findById(id).orElse(null);
//
//        if (assertEntity != null) {
//            inspection.getAssertEntity(assertEntity);
//            inspectionRepository.save(inspection);
//        }
//    }

    @Override
    public void addInspectionToAssertEntity(Long id, Inspection inspection) {
        Optional<AssertEntity> assertEntityOptional = assertRepository.findById(id);

        if (assertEntityOptional.isPresent()) {
            AssertEntity assertEntity = assertEntityOptional.get();
            inspection.setAssertEntity(assertEntity);
            inspectionRepository.save(inspection);
        }
        userLogService.addLog(ActionType.UPDATED, "added inspection to assert.");

    }

    public Inspection getInspection(Long id) {
        return inspectionRepository.findById(id).get();
    }

    @Override
    public AssertEntity getAssertEntityById(Long assertEntityId) {
        return null;
    }


    public Page<AssertEntity> getAsserts(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("lastModifiedDate").descending());

        return assertRepository.findAll(pageRequest);
    }

    @Override
    public ResponseEntity<?> getAllAssertsByStation(Long userId, Long stationId, String query,PageRequest pageRequest) {
        return ResponseEntity.ok(assertRepository.getAllAssertsByStationPage(stationId, pageRequest));
    }

    @Override
    public ResponseEntity<?> getAllAssertsByUserStation(Long userId, Long stationId, String query, PageRequest pageRequest) {
        return ResponseEntity.ok(assertRepository.getAssertsByUserStationPaged(userId, stationId, query,pageRequest));
    }

    @Override
    public ResponseEntity<?> getAllAssertsByUserStation(Long userId, String query,PageRequest pageRequest) {
        User user = userRepository1.findById(userId).get();
        if (!user.isStationAssigned()) {
            return ResponseEntity.badRequest().body(new CustomMessage("You are not authorized to get asserts for any station."));
        }
        return ResponseEntity.ok(assertRepository.getAssertsByUserStationPaged(userId, user.getStations().stream().findFirst().get().getId(), query,pageRequest));
    }

    public List<AssertEntity> getAllAssertsByUserStation(Long stationId,String query) {
        User user = userRepository1.findById(((UserDTO) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId()).get();
        if (!user.isStationAssigned(stationId)) {
            throw new RuntimeException("You are not authorized to get asserts for any station.");
        }
        return assertRepository.getAssertsByUserStation(user.getId(), stationId,query);
    }

    @Override
    public ResponseEntity<?> getStats() {
        User user = userRepository1.findById(((UserDTO) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId()).get();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getAuthorities().stream().anyMatch((r) -> r.getAuthority().contains(ROLE_AUTH.ALL_STATION.name()))) {
            // Fetch the total fixed asserts and total current asserts
            int totalFixedAsserts = assertsJpaRepository.countFixedAsserts();
            int totalCurrentAsserts = assertsJpaRepository.countCurrentAsserts();

            // Fetch asset statistics
            ArrayList<AssetItemStat> assetsStats = assertsJpaRepository.findAssertItemStatsByAssetDisc();
            userLogService.addLog(ActionType.VIEW, "checked stats of asserts.");

            // Return a new Stats object with the calculated totals and fetched asset statistics
            return ResponseEntity.ok(AssetsStats.builder().totalAsserts(assertsJpaRepository.count()).totalFixedAsserts(totalFixedAsserts).totalCurrentAsserts(totalCurrentAsserts).assetsStats(assetsStats).build());
        } else {
            if (user.isStationAssigned()) {
                return ResponseEntity.ok(getStats(user.getId()));
            }
            return ResponseEntity.badRequest().body(new CustomMessage("You are not authorized to get stats for any station."));
        }
    }

    public ResponseEntity<?> getStats(User user) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getAuthorities().stream().anyMatch((r) -> r.getAuthority().contains(ROLE_AUTH.ALL_STATION.name()))) {
            // Fetch the total fixed asserts and total current asserts
            int totalFixedAsserts = assertsJpaRepository.countFixedAsserts();
            int totalCurrentAsserts = assertsJpaRepository.countCurrentAsserts();

            // Fetch asset statistics
            ArrayList<AssetItemStat> assetsStats = assertsJpaRepository.findAssertItemStatsByAssetDisc();
            userLogService.addLog(ActionType.VIEW, "checked stats of asserts.");

            // Return a new Stats object with the calculated totals and fetched asset statistics
            return ResponseEntity.ok(AssetsStats.builder().totalAsserts(assertsJpaRepository.count()).totalFixedAsserts(totalFixedAsserts).totalCurrentAsserts(totalCurrentAsserts).assetsStats(assetsStats).build());
        } else {
            if (user.isStationAssigned()) {
                return ResponseEntity.ok(getStats(user.getId()));
            }
            return ResponseEntity.badRequest().body(new CustomMessage("You are not authorized to get stats for any station."));
        }
    }

    public AssetsStats getStats(Long userId) {
        // Fetch the total fixed asserts and total current asserts
        int totalFixedAsserts = assertsJpaRepository.countFixedAssertsForUser(userId);
        int totalCurrentAsserts = assertsJpaRepository.countCurrentAssertsForUser(userId);

        // Fetch asset statistics
        List<AssetItemStat> assetsStats = assertsJpaRepository.findAssertItemStatsByAssetDiscForUser(userId);
//        userLogService.addLog(ActionType.VIEW, "checked stats of asserts.");

        // Return a new Stats object with the calculated totals and fetched asset statistics
        return AssetsStats.builder().totalAsserts(assertsJpaRepository.countAssertsForUserStations(userId)).totalFixedAsserts(totalFixedAsserts).totalCurrentAsserts(totalCurrentAsserts).assetsStats(assetsStats).build();
    }

//    public ResponseEntity<?> getAssertsForOwnStation(int page, int size) {
//        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("lastModifiedDate").descending());
//        User user = userRepository1.findById(((UserDTO) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId()).get();
//        if (user.isStationAssigned()) {
//            return getAllAssertsByStation(user.getId(), pageRequest);
//        }
//        return ResponseEntity.ok(new ArrayList<AssertEntity>());
//    }

    public List<AssertEntity> getAsserts() {
        return assertsJpaRepository.findAll();
    }

    public ResponseEntity<?> getAllAssertsByStationMin(Long stationId) {
        return ResponseEntity.ok(assertRepository.getAllAssertsByStation(stationId));
    }

    private DownloadTokenService downloadTokenService;

    public ResponseEntity<?> getStatsToken() {
        User user = userRepository1.findById(((UserDTO) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId()).get();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getAuthorities().stream().anyMatch((r) -> r.getAuthority().contains(ROLE_AUTH.ALL_STATION.name()))) {
            return downloadTokenService.createAssertDownloadToken();
        } else {
            if (user.isStationAssigned()) {
                return downloadTokenService.createStationAssertDownloadToken();
            }
            return ResponseEntity.badRequest().body(new CustomMessage("You are not authorized to get stats for any station."));
        }
    }

    private final DownloadTokenRepository downloadTokenRepository;

    public AssetsStats getStatsUsingToken(DownloadToken token) {
        return getStats(token.getCreator().getId());
    }

    public List<AssertEntity> getAssetPDFStation(Long stationId,String query) {
        return getAllAssertsByUserStation(stationId,query);
    }
}
