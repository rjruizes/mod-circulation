package org.folio.circulation.domain.policy;

import static java.util.concurrent.CompletableFuture.completedFuture;

import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.CirculationRulesClient;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;

public abstract class CirculationPolicyRepository<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CirculationRulesClient circulationRulesClient;
  final CollectionResourceClient policyStorageClient;

  CirculationPolicyRepository(
    CirculationRulesClient circulationRulesClient,
    CollectionResourceClient policyStorageClient) {
    this.circulationRulesClient = circulationRulesClient;
    this.policyStorageClient = policyStorageClient;
  }

  public CompletableFuture<Result<T>> lookupPolicy(Loan loan) {
    return lookupPolicy(loan.getItem(), loan.getUser());
  }

  public CompletableFuture<Result<T>> lookupPolicy(Request request) {
    return lookupPolicy(request.getItem(), request.getRequester());
  }

  public CompletableFuture<Result<T>> lookupPolicy(
    Item item,
    User user) {

    return lookupPolicyId(item, user)
      .thenApply(mapResult(pair -> pair.getKey()))
      .thenComposeAsync(r -> r.after(this::lookupPolicy));
  }

  private Result<T> mapToPolicy(JsonObject json) {
    if (log.isInfoEnabled()) {
      log.info("Mapping json to policy {}", json.encodePrettily());
    }

    return toPolicy(json);
  }

  public CompletableFuture<Result<T>> lookupPolicy(String policyId) {
    log.info("Looking up policy with id {}", policyId);

    return SingleRecordFetcher.json(policyStorageClient, "circulation policy",
      response -> failedDueToServerError(getPolicyNotFoundErrorMessage(policyId)))
      .fetch(policyId)
      .thenApply(result -> result.next(this::mapToPolicy));
  }

  public CompletableFuture<Result<Pair<String, List<String>>>> lookupPolicyId(Item item, User user) {
    CompletableFuture<Result<Pair<String, List<String>>>> findLoanPolicyCompleted = new CompletableFuture<>();

    if (item.isNotFound()) {
      return completedFuture(failedDueToServerError(
        "Unable to apply circulation rules for unknown item"));
    }

    if (item.doesNotHaveHolding()) {
      return completedFuture(failedDueToServerError(
        "Unable to apply circulation rules for unknown holding"));
    }

    String loanTypeId = item.determineLoanTypeForItem();
    String locationId = item.getLocationId();
    String materialTypeId = item.getMaterialTypeId();
    String patronGroupId = user.getPatronGroupId();

    CompletableFuture<Response> circulationRulesResponse = new CompletableFuture<>();

    log.info(
      "Applying circulation rules for material type: {}, patron group: {}, loan type: {}, location: {}",
      materialTypeId, patronGroupId, loanTypeId, locationId);

    circulationRulesClient.applyRules(loanTypeId, locationId, materialTypeId,
      patronGroupId, ResponseHandler.any(circulationRulesResponse));

    circulationRulesResponse.thenAcceptAsync(response -> {
      if (response.getStatusCode() == 404) {
        findLoanPolicyCompleted.complete(failedDueToServerError(
          "Unable to apply circulation rules"));
      } else if (response.getStatusCode() != 200) {
        findLoanPolicyCompleted.complete(failed(
          new ForwardOnFailure(response)));
      } else {
        log.info("Rules response {}", response.getBody());

        String policyId = fetchPolicyId(response.getJson());
        List<String> conditions = response.getJson().getJsonArray("conditions").getList();

        log.info("Policy to fetch based upon rules {}", policyId);

        findLoanPolicyCompleted.complete(succeeded(new ImmutablePair<>(policyId, conditions)));
      }
    });

    return findLoanPolicyCompleted;
  }

  public CompletableFuture<Result<List<String>>> lookupConditions(Item item, User user) {

    return lookupPolicyId(item, user).thenApply(mapResult(Pair::getValue));
  }

  protected abstract String getPolicyNotFoundErrorMessage(String policyId);

  protected abstract Result<T> toPolicy(JsonObject representation);

  protected abstract String fetchPolicyId(JsonObject jsonObject);
}
