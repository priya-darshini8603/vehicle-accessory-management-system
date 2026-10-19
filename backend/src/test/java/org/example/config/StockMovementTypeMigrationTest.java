package org.example.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockMovementTypeMigrationTest {

    @Test
    void migratesLegacyMovementRowsToSupportedTypes() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
        StockMovementTypeMigration migration = new StockMovementTypeMigration(jdbcTemplate);

        migration.run(new DefaultApplicationArguments());

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).update(statements.capture(), parameters.capture());
        assertThat(statements.getAllValues()).containsOnly(
                "update stock_movements set movement_type = ? where movement_type = ?");
        assertThat(parameters.getAllValues()).hasSize(2);
        assertThat(parameters.getAllValues().get(0)).containsExactly("RESTORATION", "RETURN");
        assertThat(parameters.getAllValues().get(1)).containsExactly("ADJUSTMENT", "PURCHASE");
    }
}
