package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.VectorDashboardOverviewDTO;

public interface VectorDashboardService {

    VectorDashboardOverviewDTO getOverview(String range, String selectedHostId);
}
