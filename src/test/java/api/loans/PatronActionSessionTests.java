package api.loans;

import static api.support.fixtures.TemplateContextMatchers.getLoanPolicyContextMatchersForUnlimitedRenewals;
import static api.support.fixtures.TemplateContextMatchers.getMultipleLoansContextMatcher;
import static api.support.matchers.JsonObjectMatcher.toStringMatcher;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Awaitility;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;

public class PatronActionSessionTests extends APITests {

  private static final UUID CHECK_OUT_NOTICE_TEMPLATE_ID = UUID.fromString("72e7683b-76c2-4ee2-85c2-2fbca8fbcfd8");
  private static final UUID CHECK_IN_NOTICE_TEMPLATE_ID = UUID.fromString("72e7683b-76c2-4ee2-85c2-2fbca8fbcfd9");

  @Before
  public void before() {

    JsonObject checkOutNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(CHECK_OUT_NOTICE_TEMPLATE_ID)
      .withCheckOutEvent()
      .create();

    JsonObject checkInNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(CHECK_IN_NOTICE_TEMPLATE_ID)
      .withCheckInEvent()
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with check-out notice")
      .withLoanNotices(Arrays.asList(checkOutNoticeConfig, checkInNoticeConfig));
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  @Test
  public void cannotEndSessionWhenPatronIdIsNotSpecified() {
    JsonObject body = new JsonObject()
      .put("actionType", "Check-out");
    Response response = endPatronSessionClient.attemptEndPatronSession(wrapInObjectWithArray(body));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("End patron session request must have patron id"))));
  }

  @Test
  public void cannotEndSessionWhenActionTypeIsNotSpecified() {
    JsonObject body = new JsonObject()
      .put("patronId", UUID.randomUUID().toString());
    Response response = endPatronSessionClient.attemptEndPatronSession(wrapInObjectWithArray(body));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("End patron session request must have action type"))));
  }

  @Test
  public void cannotEndSessionWhenActionTypeIsNotValid() {
    String invalidActionType = "invalidActionType";

    JsonObject body = new JsonObject()
      .put("patronId", UUID.randomUUID().toString())
      .put("actionType", invalidActionType);
    Response response = endPatronSessionClient.attemptEndPatronSession(wrapInObjectWithArray(body));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Invalid patron action type value"),
      hasParameter("actionType", invalidActionType))));
  }

  @Test
  public void checkOutNoticeWithMultipleItemsIsSentWhenCorrespondingSessionIsEnded() {

    IndividualResource james = usersFixture.james();
    InventoryItemResource nod = itemsFixture.basedUponNod();
    InventoryItemResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    IndividualResource nodToJamesLoan = loansFixture.checkOutByBarcode(nod, james);
    IndividualResource interestingTimesToJamesLoan = loansFixture.checkOutByBarcode(interestingTimes, james);

    assertThat(patronSessionRecordsClient.getAll(), Matchers.hasSize(2));

    endPatronSessionClient.endCheckOutSession(james.getId());

    //Wait until session records are deleted
    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronSessionRecordsClient::getAll, empty());

    List<JsonObject> sentNotices = patronNoticesClient.getAll();
    assertThat(sentNotices, hasSize(1));

    Matcher<? super String> multipleLoansToJamesContextMatcher = getMultipleLoansContextMatcher(james,
      Arrays.asList(Pair.of(nodToJamesLoan, nod), Pair.of(interestingTimesToJamesLoan, interestingTimes)),
      toStringMatcher(getLoanPolicyContextMatchersForUnlimitedRenewals()));

    MatcherAssert.assertThat(sentNotices, hasItems(
      hasEmailNoticeProperties(james.getId(), CHECK_OUT_NOTICE_TEMPLATE_ID, multipleLoansToJamesContextMatcher)));
  }

  @Test
  public void checkOutSessionIsNotEndedSentWhenSessionEndsForDifferentUser()
    throws InterruptedException {

    IndividualResource patronForCheckOut = usersFixture.james();
    IndividualResource otherPatron = usersFixture.jessica();

    loansFixture.checkOutByBarcode(itemsFixture.basedUponNod(), patronForCheckOut);
    endPatronSessionClient.endCheckOutSession(otherPatron.getId());

    //Waits to ensure check-out session records are not deleted and no notices are sent
    TimeUnit.SECONDS.sleep(1);
    assertThat(patronSessionRecordsClient.getAll(), hasSize(1));
    assertThat(patronNoticesClient.getAll(), empty());
  }

  @Test
  public void checkOutSessionIsNotEndedWhenCheckInSessionEnds()
    throws InterruptedException {

    IndividualResource james = usersFixture.james();

    loansFixture.checkOutByBarcode(itemsFixture.basedUponNod(), james);
    assertThat(patronSessionRecordsClient.getAll(), hasSize(1));

    endPatronSessionClient.endCheckInSession(james.getId());

    //Waits to ensure check-out session records are not deleted and no notices are sent
    TimeUnit.SECONDS.sleep(1);
    assertThat(patronSessionRecordsClient.getAll(), hasSize(1));
    assertThat(patronNoticesClient.getAll(), empty());
  }

  @Test
  public void checkInSessionShouldBeCreatedWhenLoanedItemIsCheckedInByBarcode() {

    IndividualResource james = usersFixture.james();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    InventoryItemResource nod = itemsFixture.basedUponNod();

    IndividualResource loan = loansFixture.checkOutByBarcode(nod, james);
    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId));

    assertThat(patronSessionRecordsClient.getAll(), Matchers.hasSize(2));

    List<JsonObject> checkInSessions = getCheckInSessions();
    assertThat(checkInSessions, Matchers.hasSize(1));

    JsonObject checkInSession = checkInSessions.get(0);
    assertThat(checkInSession.getString("patronId"), is(james.getId().toString()));
    assertThat(checkInSession.getString("loanId"), is(loan.getId().toString()));
  }

  @Test
  public void checkInSessionShouldNotBeCreatedWhenItemWithoutOpenLoanIsCheckedInByBarcode() {

    UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    InventoryItemResource nod = itemsFixture.basedUponNod();

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId));

    assertThat(patronSessionRecordsClient.getAll(), empty());
  }

  @Test
  public void patronNoticesShouldBeSentWhenCheckInSessionIsEnded() {

    IndividualResource steve = usersFixture.steve();
    UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    InventoryItemResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod, steve);
    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId));

    List<JsonObject> checkInSessions = getCheckInSessions();
    assertThat(checkInSessions, Matchers.hasSize(1));

    assertThat(patronNoticesClient.getAll(), empty());
    endPatronSessionClient.endCheckInSession(steve.getId());

    //Wait until session records are deleted
    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(this::getCheckInSessions, empty());

    assertThat(patronNoticesClient.getAll(), hasSize(1));
  }

  private List<JsonObject> getCheckInSessions() {

    Predicate<JsonObject> isCheckInSession = json -> json.getString("actionType").equals("Check-in");

    return patronSessionRecordsClient.getAll().stream()
      .filter(isCheckInSession)
      .collect(Collectors.toList());
  }

  private JsonObject wrapInObjectWithArray(JsonObject body) {
    JsonArray jsonArray = new JsonArray().add(body);
    return new JsonObject().put("endSessions", jsonArray);
  }
}
