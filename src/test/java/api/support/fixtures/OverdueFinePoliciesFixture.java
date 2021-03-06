package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.NoticePolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class OverdueFinePoliciesFixture {
  private final RecordCreator overdueFinePolicyRecordCreator;

  public OverdueFinePoliciesFixture(ResourceClient overdueFinePoliciesClient) {
    overdueFinePolicyRecordCreator = new RecordCreator(overdueFinePoliciesClient,
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource facultyStandard() {

    JsonObject overdueFine = new JsonObject();
    overdueFine.put("quantity", 5.0);
    overdueFine.put("intervalId", "day");

    JsonObject overdueRecallFine = new JsonObject();
    overdueRecallFine.put("quantity", 1.0);
    overdueRecallFine.put("intervalId", "hour");


    final OverdueFinePolicyBuilder facultyStandard = new OverdueFinePolicyBuilder()
      .withName("Faculty standard")
      .withDescription("This is description for Faculty standard")
      .withOverdueFine(overdueFine)
      .withCountClosed(true)
      .withMaxOverdueFine(50.00)
      .withForgiveOverdueFine(false)
      .withOverdueRecallFine(overdueRecallFine)
      .withGracePeriodRecall(false)
      .withMaxOverdueRecallFine(50.00);

    return overdueFinePolicyRecordCreator.createIfAbsent(facultyStandard);
  }

  public IndividualResource facultyStandardShouldForgiveFine() {

    JsonObject overdueFine = new JsonObject();
    overdueFine.put("quantity", 5.0);
    overdueFine.put("intervalId", "day");

    JsonObject overdueRecallFine = new JsonObject();
    overdueRecallFine.put("quantity", 1.0);
    overdueRecallFine.put("intervalId", "hour");


    final OverdueFinePolicyBuilder facultyStandard = new OverdueFinePolicyBuilder()
        .withName("Faculty standard (should forgive overdue fine for renewals)")
        .withDescription("This is description for Faculty standard (should forgive overdue fine for renewals)")
        .withOverdueFine(overdueFine)
        .withCountClosed(true)
        .withMaxOverdueFine(50.00)
        .withForgiveOverdueFine(true)
        .withOverdueRecallFine(overdueRecallFine)
        .withGracePeriodRecall(false)
        .withMaxOverdueRecallFine(50.00);

    return overdueFinePolicyRecordCreator.createIfAbsent(facultyStandard);
  }

  public IndividualResource facultyStandardDoNotCountClosed() {
    JsonObject overdueFine = new JsonObject();
    overdueFine.put("quantity", 5.0);
    overdueFine.put("intervalId", "day");

    JsonObject overdueRecallFine = new JsonObject();
    overdueRecallFine.put("quantity", 1.0);
    overdueRecallFine.put("intervalId", "hour");

    final OverdueFinePolicyBuilder overdueFinePolicyBuilder = new OverdueFinePolicyBuilder()
      .withName("Faculty standard (don't count closed)")
      .withDescription("This is description for Faculty standard (don't count closed)")
      .withOverdueFine(overdueFine)
      .withCountClosed(false)
      .withMaxOverdueFine(50.00)
      .withForgiveOverdueFine(false)
      .withOverdueRecallFine(overdueRecallFine)
      .withGracePeriodRecall(false)
      .withMaxOverdueRecallFine(50.00);

    return overdueFinePolicyRecordCreator.createIfAbsent(overdueFinePolicyBuilder);
  }

  public IndividualResource  noOverdueFine() {
    JsonObject overdueFinePolicy = new JsonObject();
    overdueFinePolicy.put("id", UUID.randomUUID().toString());
    overdueFinePolicy.put("name", "No overdue fine policy");
    return overdueFinePolicyRecordCreator.createIfAbsent(overdueFinePolicy);
  }

  public IndividualResource create(NoticePolicyBuilder noticePolicy) {
    return overdueFinePolicyRecordCreator.createIfAbsent(noticePolicy);
  }

  public IndividualResource create(OverdueFinePolicyBuilder overdueFinePolicyBuilder) {
    return overdueFinePolicyRecordCreator.createIfAbsent(overdueFinePolicyBuilder);
  }

  public void cleanUp() {
    overdueFinePolicyRecordCreator.cleanUp();
  }
}
