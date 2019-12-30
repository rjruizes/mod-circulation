package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.CqlQuery.noQuery;
import static api.support.http.InterfaceUrls.requestQueueUrl;
import static api.support.http.InterfaceUrls.requestsUrl;
import static api.support.http.Limit.noLimit;
import static api.support.http.Offset.noOffset;
import static java.util.function.Function.identity;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;

import api.requests.scenarios.CancelRequestTests;
import api.support.RestAssuredClient;
import api.support.builders.MoveRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class RequestsFixture {
  private final ResourceClient requestsClient;
  private final CancellationReasonsFixture cancellationReasonsFixture;
  private final ServicePointsFixture servicePointsFixture;
  private final RestAssuredClient restAssuredClient;

  public RequestsFixture(ResourceClient requestsClient,
      CancellationReasonsFixture cancellationReasonsFixture,
      ServicePointsFixture servicePointsFixture) {

    this.requestsClient = requestsClient;
    this.cancellationReasonsFixture = cancellationReasonsFixture;
    this.servicePointsFixture = servicePointsFixture;
    restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public IndividualResource place(RequestBuilder requestToBuild) {

    return requestsClient.create(requestToBuild);
  }

  public Response attemptPlace(RequestBuilder requestToBuild) {

    return requestsClient.attemptCreate(requestToBuild);
  }

  public IndividualResource placeHoldShelfRequest(IndividualResource item,
      IndividualResource by, DateTime on) {

    return place(new RequestBuilder()
      .hold()
      .fulfilToHoldShelf()
      .withItemId(item.getId())
      .withRequestDate(on)
      .withRequesterId(by.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));
  }

  public IndividualResource placeDeliveryRequest(IndividualResource item,
      IndividualResource by, DateTime on) {

    return place(new RequestBuilder()
      .hold()
      .deliverToAddress(UUID.randomUUID())
      .withRequestDate(on)
      .withItemId(item.getId())
      .withRequesterId(by.getId()));
  }

  public IndividualResource placeHoldShelfRequest(IndividualResource item,
      IndividualResource by, DateTime on, UUID pickupServicePointId) {

    return place(new RequestBuilder()
        .hold()
        .fulfilToHoldShelf()
        .withItemId(item.getId())
        .withRequestDate(on)
        .withRequesterId(by.getId())
        .withPickupServicePointId(pickupServicePointId));
  }

  public IndividualResource placeHoldShelfRequest(IndividualResource item,
      IndividualResource by, DateTime on, UUID pickupServicePointId, String type) {

    return place(new RequestBuilder()
        .hold()
        .withRequestType(type)
        .fulfilToHoldShelf()
        .withItemId(item.getId())
        .withRequestDate(on)
        .withRequesterId(by.getId())
        .withPickupServicePointId(pickupServicePointId));
  }

  public IndividualResource placeHoldShelfRequest(IndividualResource item,
      IndividualResource by, DateTime on, String type) {

    return place(new RequestBuilder()
      .withRequestType(type)
      .deliverToAddress(UUID.randomUUID())
      .withRequestDate(on)
      .withItemId(item.getId())
      .withRequesterId(by.getId()));
  }

  public Response attemptPlaceHoldShelfRequest(IndividualResource item,
      IndividualResource by, DateTime on, UUID pickupServicePointId, String type) {

    return attemptPlace(new RequestBuilder()
        .hold()
        .withRequestType(type)
        .fulfilToHoldShelf()
        .withItemId(item.getId())
        .withRequestDate(on)
        .withRequesterId(by.getId())
        .withPickupServicePointId(pickupServicePointId));
  }

  public void cancelRequest(IndividualResource request) {

    final IndividualResource courseReservesCancellationReason
      = cancellationReasonsFixture.courseReserves();

    final RequestBuilder cancelledRequestBySteve = RequestBuilder.from(request)
      .cancelled()
      .withCancellationReasonId(courseReservesCancellationReason.getId());

    requestsClient.replace(request.getId(), cancelledRequestBySteve);
  }

  public IndividualResource move(MoveRequestBuilder requestToBuild) {
    final JsonObject representation = requestToBuild.create();

    return new IndividualResource(restAssuredClient.post(representation,
        requestsUrl(pathToMoveRequest(representation)), 200, "move-request"));
  }

  public Response attemptMove(MoveRequestBuilder requestToBuild) {
    final JsonObject representation = requestToBuild.create();

    return restAssuredClient.post(representation,
      requestsUrl(pathToMoveRequest(representation)), "move-request");
  }

  public Response getAllRequests() {
    return restAssuredClient.get(requestsUrl(), noQuery(), noLimit(),
      noOffset(), 200, "get-all-requests");
  }

  //TODO: Replace return type with MultipleJsonRecords
  public MultipleRecords<JsonObject> getQueueFor(IndividualResource item) {
    return MultipleRecords.from(restAssuredClient.get(
        requestQueueUrl(item.getId()), 200, "request-queue-request"),
        identity() ,"requests").value();
  }

  private String pathToMoveRequest(JsonObject representation) {
    return String.format("/%s/move", representation.getString("id"));
  }
}
