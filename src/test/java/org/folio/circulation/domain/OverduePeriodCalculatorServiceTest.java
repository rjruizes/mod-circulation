package org.folio.circulation.domain;

import static api.support.fixtures.OpeningHourExamples.afternoon;
import static api.support.fixtures.OpeningHourExamples.allDay;
import static api.support.fixtures.OpeningHourExamples.morning;
import static org.joda.time.DateTimeConstants.MINUTES_PER_DAY;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;
import static org.joda.time.DateTimeConstants.MINUTES_PER_WEEK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class OverduePeriodCalculatorServiceTest {
  private static final OverduePeriodCalculatorService calculator =
    new OverduePeriodCalculatorService(null, null);

  @Test
  public void preconditionsCheckLoanHasNoDueDate() {
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan = new LoanBuilder().asDomainObject();

    assertFalse(calculator.preconditionsAreMet(loan, systemTime, true));
  }

  @Test
  public void preconditionsCheckLoanDueDateIsInFuture() {
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan = new LoanBuilder()
      .withDueDate(systemTime.plusDays(1))
      .asDomainObject();

    assertFalse(calculator.preconditionsAreMet(loan, systemTime, true));
  }

  @Test
  public void preconditionsCheckCountClosedIsNull() {
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan = new LoanBuilder()
      .withDueDate(systemTime.minusDays(1))
      .asDomainObject();

    assertFalse(calculator.preconditionsAreMet(loan, systemTime, null));
  }

  @Test
  public void allPreconditionsAreMet() {
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);
    Loan loan = new LoanBuilder()
      .withDueDate(systemTime.minusDays(1))
      .asDomainObject();

    assertTrue(calculator.preconditionsAreMet(loan, systemTime, true));
  }

  @Test
  public void countOverdueMinutesWithClosedDays() throws ExecutionException, InterruptedException {
    int expectedResult = MINUTES_PER_WEEK + MINUTES_PER_DAY + MINUTES_PER_HOUR + 1;
    DateTime systemTime = DateTime.now(DateTimeZone.UTC);

    Loan loan = new LoanBuilder()
      .withDueDate(systemTime.minusMinutes(expectedResult))
      .asDomainObject();

    int actualResult = calculator.getOverdueMinutes(loan, systemTime, true).get().value();

    assertEquals(expectedResult, actualResult);
  }

  @Test
  @Parameters
  public void getOpeningDayDurationTest(List<OpeningDay> openingDays, int expectedResult) {
    int actualResult = calculator.getOpeningDaysDurationMinutes(openingDays).value();
    assertEquals(expectedResult, actualResult);
  }

  private Object[] parametersForGetOpeningDayDurationTest() {
    List<OpeningDay> zeroDays = Collections.emptyList();

    List<OpeningDay> regular = Arrays.asList(
      createOpeningDay(false),
      createOpeningDay(false),
      createOpeningDay(false));

    List<OpeningDay> allDay = Arrays.asList(
      createOpeningDay(true),
      createOpeningDay(true),
      createOpeningDay(true));

    List<OpeningDay> mixed = Arrays.asList(
      createOpeningDay(false),
      createOpeningDay(true),
      createOpeningDay(false));

    LocalTime now = LocalTime.now(DateTimeZone.UTC);

    List<OpeningDay> invalid = Arrays.asList(
      OpeningDay.createOpeningDay(
        Collections.singletonList(new OpeningHour(null, null)),
        null, false, true),
      OpeningDay.createOpeningDay(
        Collections.singletonList(new OpeningHour(now, now.minusHours(1))),
        null, false, true)
    );

    return new Object[]{
      new Object[]{zeroDays, 0},
      new Object[]{regular, MINUTES_PER_HOUR * 10 * 3},
      new Object[]{allDay, MINUTES_PER_DAY * 3},
      new Object[]{mixed, MINUTES_PER_HOUR * 10 * 2 + MINUTES_PER_DAY},
      new Object[]{invalid, 0}
    };
  }

  @Test
  @Parameters
  public void gracePeriodAdjustmentTest(
    int overdueMinutes,
    String gracePeriodInterval,
    int gracePeriodDuration,
    Boolean ignoreGracePeriodForRecalls,
    boolean dueDateChangedByRecall,
    int expectedResult) {

    final Loan loan = new LoanBuilder()
      .withDueDateChangedByRecall(dueDateChangedByRecall)
      .asDomainObject()
      .withLoanPolicy(createLoanPolicy(gracePeriodDuration, gracePeriodInterval))
      .withOverdueFinePolicy(createOverdueFinePolicy(ignoreGracePeriodForRecalls, null));

    int actualResult = calculator.adjustOverdueWithGracePeriod(loan, overdueMinutes).value();
    assertEquals(expectedResult, actualResult);
  }

  private Object[] parametersForGracePeriodAdjustmentTest() {
    Stream<Object> parametersStream = Arrays.stream(new GracePeriodParams[] {
      new GracePeriodParams(10, "Minutes", 5, 12),
      new GracePeriodParams(255, "Hours", 4, 5),
      new GracePeriodParams(4350, "Days", 3, 4),
      new GracePeriodParams(20200, "Weeks", 2, 3),
      new GracePeriodParams(44700, "Months", 1, 2)
    }).map(p -> new Object[]{
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, null, false,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, null, true,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, false, false,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, true, false,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, false, true,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodLessThanOverdue, true, true,
        p.overdueMinutes},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, null, false, 0},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, null, true, 0},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, false, false, 0},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, true, false, 0},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, false, true, 0},
      new Object[] {p.overdueMinutes, p.interval, p.gracePeriodGreaterThanOverdue, true, true,
        p.overdueMinutes}
    })
      .flatMap(Arrays::stream);

    return Stream.concat(parametersStream,
      Stream.of(new Object[][] {{11, "Unknown interval", 9, false, false, 11}})).toArray();
  }

  private LoanPolicy createLoanPolicy(Integer gracePeriodDuration, String gracePeriodInterval) {
    LoanPolicyBuilder builder = new LoanPolicyBuilder();
    if (ObjectUtils.allNotNull(gracePeriodDuration, gracePeriodInterval)) {
      Period gracePeriod = Period.from(gracePeriodDuration, gracePeriodInterval);
      builder = builder.withGracePeriod(gracePeriod);
    }
    return LoanPolicy.from(builder.create());
  }

  private OverdueFinePolicy createOverdueFinePolicy(Boolean gracePeriodRecall, Boolean countClosed) {
    JsonObject overdueFineObject = new JsonObject();
    overdueFineObject.put("quantity", 1);
    overdueFineObject.put("intervalId", "minute");

    JsonObject overdueRecallFineObject = new JsonObject();
    overdueRecallFineObject.put("quantity", 1);
    overdueRecallFineObject.put("intervalId", "minute");

    JsonObject json = new OverdueFinePolicyBuilder()
      .withGracePeriodRecall(gracePeriodRecall)
      .withCountClosed(countClosed)
      .withOverdueFine(overdueFineObject)
      .withOverdueRecallFine(overdueRecallFineObject)
      .withMaxOverdueFine(5)
      .withMaxOverdueRecallFine(5)
      .create();

    return OverdueFinePolicy.from(json);
  }

  private OpeningDay createOpeningDay(boolean allDay) {
    return OpeningDay.createOpeningDay(
      allDay ? Collections.singletonList(allDay()) : Arrays.asList(morning(), afternoon()),
      null, allDay, true
      );
  }

  private static final class GracePeriodParams {
    private final int overdueMinutes;
    private final String interval;
    private final int gracePeriodLessThanOverdue;
    private final int gracePeriodGreaterThanOverdue;

    public GracePeriodParams(int overdueMinutes, String interval, int gracePeriodLessThanOverdue,
      int gracePeriodGreaterThanOverdue) {

      this.overdueMinutes = overdueMinutes;
      this.interval = interval;
      this.gracePeriodLessThanOverdue = gracePeriodLessThanOverdue;
      this.gracePeriodGreaterThanOverdue = gracePeriodGreaterThanOverdue;
    }

    public int getOverdueMinutes() {
      return overdueMinutes;
    }

    public String getInterval() {
      return interval;
    }

    public int getGracePeriodLessThanOverdue() {
      return gracePeriodLessThanOverdue;
    }

    public int getGracePeriodGreaterThanOverdue() {
      return gracePeriodGreaterThanOverdue;
    }
  }
}
