package com.family195home.app.statistics.controller;

import com.family195home.app.family.service.FamilyService;
import com.family195home.app.security.AuthenticatedUser;
import com.family195home.app.statistics.dto.MonthlySummaryResponse;
import com.family195home.app.statistics.service.StatisticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final FamilyService familyService;

    public StatisticsController(StatisticsService statisticsService, FamilyService familyService) {
        this.statisticsService = statisticsService;
        this.familyService = familyService;
    }

    @GetMapping("/api/statistics/monthly")
    public ResponseEntity<MonthlySummaryResponse> monthly(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam Long familyGroupId,
            @RequestParam String month) {
        // FR-017：確認呼叫者屬於該家庭群組，避免跨家庭資料外洩
        familyService.assertMemberAuthorized(familyGroupId, caller.userId(), FamilyService.RequiredRole.ANY_MEMBER);
        return ResponseEntity.ok(statisticsService.summarize(familyGroupId, month));
    }
}
