package com.wk.pfmis.services;

import com.wk.pfmis.models.DashboardStats;
import com.wk.pfmis.models.ReportRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardAggregationServiceTest {

    @Test
    void spendingCategoriesCalculatePercentagesAndConsolidateOverflow() {
        List<DashboardAggregationService.SpendingCategory> categories = DashboardAggregationService.spendingCategories(
                List.of(
                        new ReportRow("Rent", 100),
                        new ReportRow("Food", 50),
                        new ReportRow("Transport", 25),
                        new ReportRow("Utilities", 25)
                ),
                2
        );

        assertEquals(3, categories.size());
        assertEquals("Rent", categories.get(0).category());
        assertEquals(50.0, categories.get(0).percentage(), 0.001);
        assertEquals("Food", categories.get(1).category());
        assertEquals(25.0, categories.get(1).percentage(), 0.001);
        assertEquals("Other categories", categories.get(2).category());
        assertEquals(25.0, categories.get(2).percentage(), 0.001);
    }

    @Test
    void spendingCategoriesIgnoreEmptyOrNonPositiveActualExpenseRows() {
        List<DashboardAggregationService.SpendingCategory> categories = DashboardAggregationService.spendingCategories(
                List.of(
                        new ReportRow("No spend", 0),
                        new ReportRow("Correction", -10)
                ),
                4
        );

        assertTrue(categories.isEmpty());
    }

    @Test
    void dashboardStatsExposeNetCashFlowAsIncomeMinusExpenses() {
        DashboardStats stats = new DashboardStats(1000, 250, 400, 1, 0, 0, 0);

        assertEquals(-150, stats.getNetCashFlow(), 0.001);
    }
}
