package org.example.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryAnalysisCalculatorTest {

    private final InventoryAnalysisCalculator calculator = new InventoryAnalysisCalculator();

    @Test
    void calculatesInventoryPlanningMetrics() {
        var result = calculator.calculate(3, 2, 7, 31, 30);

        assertThat(result.averageDailySales()).isEqualByComparingTo("1.033333");
        assertThat(result.leadTimeDemand()).isEqualByComparingTo("7.233331");
        assertThat(result.reorderPoint()).isEqualByComparingTo("9.233331");
        assertThat(result.stockCoverDays()).isEqualByComparingTo("2.903227");
        assertThat(result.lowStock()).isTrue();
        assertThat(result.outOfStock()).isFalse();
        assertThat(result.recommendedReorderQuantity()).isEqualTo(7);
    }

    @Test
    void normalizesMissingThresholdsAndLeavesStockCoverUndefinedWithoutSales() {
        var result = calculator.calculate(0, null, null, 0, 30);

        assertThat(result.minimumStock()).isZero();
        assertThat(result.leadTimeDays()).isZero();
        assertThat(result.stockCoverDays()).isNull();
        assertThat(result.lowStock()).isFalse();
        assertThat(result.outOfStock()).isTrue();
        assertThat(result.recommendedReorderQuantity()).isZero();
    }

    @Test
    void rejectsNonPositiveAnalysisPeriods() {
        assertThatThrownBy(() -> calculator.calculate(1, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Analysis period must be positive");
    }
}
