package br.unicamp.ft.si600.eventos.dto;

import java.util.List;

public record AdminDashboardResponse(
        List<InactiveExhibitorResponse> inactiveExhibitors,
        List<ProjectResponse> pendingDeadlines,
        List<PendingApprovalResponse> pendingApprovals
) {}
