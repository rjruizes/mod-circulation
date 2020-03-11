package org.folio.circulation.support.fetching;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.client.PageLimit.maximumLimit;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class FindMultipleRecordsByIdTests {
  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Captor
  private ArgumentCaptor<Result<CqlQuery>> generatedCqlQueries;

  @Mock
  private FindWithCqlQuery<JsonObject> queryFinder;

  @Test
  public void shouldUseSingleCqlQueryForFindingSmallNumberOfRecordsById() {
    final int MAX_VALUES_PER_CQL_SEARCH_QUERY = 50;

    when(queryFinder.findByQuery(any(), any())).thenReturn(
      CompletableFuture.completedFuture(Result.succeeded(MultipleRecords.empty())));

    final FindWithMultipleCqlIndexValues<JsonObject> fetcher
        = new CqlIndexValuesFinder<>(queryFinder, MAX_VALUES_PER_CQL_SEARCH_QUERY);

    final Collection<String> ids = generateIds(10);

    fetcher.findByIds(ids);

    verify(queryFinder).findByQuery(generatedCqlQueries.capture(), eq(maximumLimit()));

    final CqlQuery expectedQuery = exactMatchAny("id", ids).value();

    assertThat(generatedCqlQueries.getValue().value(), is(expectedQuery));
  }

  @Test
  @Parameters({ "50", "30" })
  public void shouldUseMultipleCqlQueriesForFindingSmallNumberOfRecordsById(
      int maximumValuesPerCqlQuery) {

    final FindWithCqlQuery<JsonObject> queryFinder = mock(FindWithCqlQuery.class);

    when(queryFinder.findByQuery(any(), any())).thenReturn(
        CompletableFuture.completedFuture(Result.succeeded(MultipleRecords.empty())));

    final FindWithMultipleCqlIndexValues<JsonObject> fetcher
        = new CqlIndexValuesFinder<>(queryFinder, maximumValuesPerCqlQuery);

    final Collection<String> firstSetOfIds = generateIds(maximumValuesPerCqlQuery);
    final Collection<String> secondSetOfIds = generateIds(maximumValuesPerCqlQuery);

    fetcher.findByIds(combineSetsOfIds(firstSetOfIds, secondSetOfIds));

    verify(queryFinder, times(2))
      .findByQuery(generatedCqlQueries.capture(), eq(maximumLimit()));

    final CqlQuery firstExpectedQuery = exactMatchAny("id", firstSetOfIds).value();
    final CqlQuery secondExpectedQuery = exactMatchAny("id", secondSetOfIds).value();

    assertThat(getCapturedQuery(generatedCqlQueries, 0), is(firstExpectedQuery));
    assertThat(getCapturedQuery(generatedCqlQueries, 1), is(secondExpectedQuery));
  }

  @Test
  public void shouldIncludeAdditionalQuery() {
    final FindWithCqlQuery<JsonObject> queryFinder = mock(FindWithCqlQuery.class);

    when(queryFinder.findByQuery(any(), any())).thenReturn(
      CompletableFuture.completedFuture(Result.succeeded(MultipleRecords.empty())));

    final FindWithMultipleCqlIndexValues<JsonObject> fetcher
      = new CqlIndexValuesFinder<>(queryFinder, 1);

    final Collection<String> ids = generateIds(1);
    final Result<CqlQuery> openStatusQuery = exactMatch("status", "Open");

    fetcher.findByIdIndexAndQuery(ids, "itemId", openStatusQuery);

    verify(queryFinder).findByQuery(generatedCqlQueries.capture(), eq(maximumLimit()));

    final CqlQuery expectedQuery = exactMatchAny("itemId", ids).value()
        .and(openStatusQuery.value());

    assertThat(generatedCqlQueries.getValue().value(), is(expectedQuery));
  }

  @Test
  public void shouldAssumeNoRecordsAreFoundWhenSearchingForNoIds()
      throws InterruptedException, ExecutionException, TimeoutException {

    final FindWithCqlQuery<JsonObject> queryFinder = mock(FindWithCqlQuery.class);

    when(queryFinder.findByQuery(any(), any())).thenReturn(
      CompletableFuture.completedFuture(Result.succeeded(MultipleRecords.empty())));

    final FindWithMultipleCqlIndexValues<JsonObject> fetcher
      = new CqlIndexValuesFinder<>(queryFinder);

    final CompletableFuture<Result<MultipleRecords<JsonObject>>> futureResult
      = fetcher.findByIds(new ArrayList<>());

    final MultipleRecords<JsonObject> result = getFutureResultValue(futureResult);

    assertThat("Should assume no records are found",
        result.isEmpty(), is(true));

    verify(queryFinder, times(0)).findByQuery(any(), any());
  }

  private Collection<String> generateIds(int size) {
    return Stream.generate(UUID::randomUUID)
      .map(UUID::toString)
      .limit(size)
      .collect(Collectors.toList());
  }

  private Collection<String> combineSetsOfIds(Collection<String> firstSetOfIds,
      Collection<String> secondSetOfIds) {

    return Stream.concat(firstSetOfIds.stream(), secondSetOfIds.stream())
      .collect(Collectors.toList());
  }

  private <T> T getFutureResultValue(CompletableFuture<Result<T>> futureResult)
      throws InterruptedException, ExecutionException, TimeoutException {

    return futureResult.get(1, SECONDS).value();
  }

  private CqlQuery getCapturedQuery(
      ArgumentCaptor<Result<CqlQuery>> capturedQueries, int index) {

    return capturedQueries.getAllValues().get(index).value();
  }
}