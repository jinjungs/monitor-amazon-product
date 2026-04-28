package com.monitor.amazon.checker;

import com.monitor.amazon.config.MonitorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class PriceCheckerTest {

    private PriceChecker checker;

    @BeforeEach
    void setUp() {
        MonitorProperties props = new MonitorProperties();
        props.getThreshold().setAbsolute(new BigDecimal("1.00"));
        props.getThreshold().setPercentage(new BigDecimal("2.0"));
        checker = new PriceChecker(props);
    }

    @Test
    void detectsDropMeetingAbsoluteThreshold() {
        assertThat(checker.isSignificantDrop(new BigDecimal("10.00"), new BigDecimal("8.00"))).isTrue();
    }

    @Test
    void detectsDropMeetingPercentageThreshold() {
        // 2% of 100.00 = 2.00, drop is exactly 2.00 → meets percentage but not absolute (< 1.00 is false here, but 2.00 >= 1.00 so absolute also triggers)
        // Use a case where only percentage triggers: old=200, new=196 → drop=4.00 (>= $1 absolute AND >= 2%)
        // To isolate percentage only: old=0.10, new=0.097 → drop=0.003 (< $1 absolute, but >= 3% percentage)
        assertThat(checker.isSignificantDrop(new BigDecimal("0.10"), new BigDecimal("0.097"))).isTrue();
    }

    @Test
    void noAlertWhenDropBelowBothThresholds() {
        // drop = $0.50 (< $1.00), percentage = 0.5% (< 2%)
        assertThat(checker.isSignificantDrop(new BigDecimal("100.00"), new BigDecimal("99.50"))).isFalse();
    }

    @Test
    void noAlertWhenPriceIncreases() {
        assertThat(checker.isSignificantDrop(new BigDecimal("10.00"), new BigDecimal("12.00"))).isFalse();
    }

    @Test
    void noAlertWhenPriceUnchanged() {
        assertThat(checker.isSignificantDrop(new BigDecimal("10.00"), new BigDecimal("10.00"))).isFalse();
    }

    @Test
    void noAlertWhenEitherPriceIsNull() {
        assertThat(checker.isSignificantDrop(null, new BigDecimal("5.00"))).isFalse();
        assertThat(checker.isSignificantDrop(new BigDecimal("5.00"), null)).isFalse();
    }
}
