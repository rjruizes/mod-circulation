package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.folio.circulation.support.HttpResult.failed;

public class FixedDueDateSchedules {
  private final List<JsonObject> schedules;
  private final DateTimeZone timeZone;

  FixedDueDateSchedules(List<JsonObject> schedules, DateTimeZone timeZone) {
    this.schedules = schedules;
    this.timeZone = timeZone;
  }

  static FixedDueDateSchedules from(JsonObject representation, DateTimeZone timeZone) {
    //TODO: Replace this with better check
    if (representation == null) {
      return new NoFixedDueDateSchedules();
    } else {
      return new FixedDueDateSchedules(JsonArrayHelper.toList(
        representation.getJsonArray("schedules")), timeZone);
    }
  }

  // for test only
  static FixedDueDateSchedules from(JsonObject representation) {
    //TODO: Replace this with better check
    if (representation == null) {
      return new NoFixedDueDateSchedules();
    } else {
      return new FixedDueDateSchedules(JsonArrayHelper.toList(
        representation.getJsonArray("schedules")), DateTimeZone.UTC);
    }
  }

  public Optional<DateTime> findDueDateFor(DateTime date) {
    return findScheduleFor(date)
      .map(this::getDueDate);
  }

  private Optional<JsonObject> findScheduleFor(DateTime date) {
    return schedules
      .stream()
      .filter(isWithin(date))
      .findFirst();
  }

  private Predicate<? super JsonObject> isWithin(DateTime date) {
    return schedule -> {
      DateTime from = DateTime.parse(schedule.getString("from")).withZoneRetainFields(timeZone);
      DateTime to = DateTime.parse(schedule.getString("to")).withZoneRetainFields(timeZone);

      return date.isAfter(from) && date.isBefore(to);
    };
  }

  private DateTime getDueDate(JsonObject schedule) {
    return DateTime.parse(schedule.getString("due")).withZoneRetainFields(timeZone);
  }

  List<DateTime> getDueDates() {
    return schedules.stream()
      .map(schedule ->
        new DateTime(schedule.getString("due"))
          .millisOfDay()
          .withMaximumValue()
          .withZone(DateTimeZone.UTC))
      .collect(Collectors.toList());
  }

  public boolean isEmpty() {
    return schedules.isEmpty();
  }

  HttpResult<DateTime> truncateDueDate(
    DateTime dueDate,
    DateTime loanDate,
    Supplier<ValidationErrorFailure> noApplicableScheduleError) {

    return findDueDateFor(loanDate)
      .map(limit -> earliest(dueDate, limit))
      .map(HttpResult::succeeded)
      .orElseGet(() -> failed(noApplicableScheduleError.get()));
  }

  private DateTime earliest(DateTime rollingDueDate, DateTime limit) {
    return limit.isBefore(rollingDueDate)
      ? limit
      : rollingDueDate;
  }
}
