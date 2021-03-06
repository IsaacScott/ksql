/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.confluent.ksql.schema.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.google.common.collect.ImmutableMap;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.ksql.util.KsqlConfig;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.network.Mode;
import org.apache.kafka.common.security.ssl.SslFactory;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author andy
 * created 3/26/18
 */
@RunWith(EasyMockRunner.class)
public class KsqlSchemaRegistryClientFactoryTest {

  private static final SSLContext SSL_CONTEXT = getTestSslContext();

  @Mock
  private Supplier<RestService> restServiceSupplier;

  @Mock
  private RestService restService;

  @Mock
  private SslFactory sslFactory;

  @Mock
  private KsqlSchemaRegistryClientFactory.SchemaRegistryClientFactory schemaRegistryClientFactory;

  @Before
  public void setUp() {
    EasyMock.expect(schemaRegistryClientFactory.create(EasyMock.anyObject(),
                                                       EasyMock.anyInt(),
                                                       EasyMock.anyObject()))
        .andReturn(EasyMock.niceMock(CachedSchemaRegistryClient.class));
    EasyMock.expect(restServiceSupplier.get()).andReturn(restService);
    EasyMock.replay(restServiceSupplier, schemaRegistryClientFactory);

    EasyMock.expect(sslFactory.sslContext()).andReturn(SSL_CONTEXT);
  }

  @Test
  public void shouldSetSocketFactoryWhenNoSpecificSslConfig() {
    // Given:
    final KsqlConfig config = config();

    final Map<String, Object> expectedConfigs = defaultConfigs();
    setUpMocksWithExpectedConfig(expectedConfigs);

    // When:
    final SchemaRegistryClient client =
        new KsqlSchemaRegistryClientFactory(config, restServiceSupplier, sslFactory,
                                            schemaRegistryClientFactory).get();

    // Then:
    assertThat(client, is(notNullValue()));
    EasyMock.verify(restService);
  }

  @Test
  public void shouldPickUpNonPrefixedSslConfig() {
    // Given:
    final KsqlConfig config = config(
        SslConfigs.SSL_PROTOCOL_CONFIG, "SSLv3"
    );

    final Map<String, Object> expectedConfigs = defaultConfigs();
    expectedConfigs.put(SslConfigs.SSL_PROTOCOL_CONFIG, "SSLv3");
    setUpMocksWithExpectedConfig(expectedConfigs);

    // When:
    final SchemaRegistryClient client =
        new KsqlSchemaRegistryClientFactory(config, restServiceSupplier, sslFactory,
                                            schemaRegistryClientFactory).get();

    // Then:
    assertThat(client, is(notNullValue()));
    EasyMock.verify(restService);
  }

  @Test
  public void shouldPickUpPrefixedSslConfig() {
    // Given:
    final KsqlConfig config = config(
        "ksql.schema.registry." + SslConfigs.SSL_PROTOCOL_CONFIG, "SSLv3"
    );

    final Map<String, Object> expectedConfigs = defaultConfigs();
    expectedConfigs.put(SslConfigs.SSL_PROTOCOL_CONFIG, "SSLv3");
    setUpMocksWithExpectedConfig(expectedConfigs);

    // When:
    final SchemaRegistryClient client =
        new KsqlSchemaRegistryClientFactory(config, restServiceSupplier, sslFactory,
                                            schemaRegistryClientFactory).get();


    // Then:
    assertThat(client, is(notNullValue()));
    EasyMock.verify(restService);
  }


  @Test
  public void shouldPassBasicAuthCredentialsToSchemaRegistryClient() {
    // Given
    final Map<String, Object> schemaRegistryClientConfigs = ImmutableMap.of(
        "ksql.schema.registry.basic.auth.credentials.source", "USER_INFO",
        "ksql.schema.registry.basic.auth.user.info", "username:password"
    );

    final KsqlConfig config = new KsqlConfig(schemaRegistryClientConfigs);

    final Map<String, Object> expectedConfigs = schemaRegistryClientConfigs.entrySet()
        .stream()
        .collect(Collectors.toMap(
            e -> e.getKey().replaceFirst(KsqlConfig.KSQL_SCHEMA_REGISTRY_PREFIX, ""),
            Map.Entry::getValue
        ));

    final KsqlSchemaRegistryClientFactory.SchemaRegistryClientFactory clientFactory =
        EasyMock.niceMock(KsqlSchemaRegistryClientFactory.SchemaRegistryClientFactory.class);

    EasyMock.expect(clientFactory.create(EasyMock.same(restService),
                                         EasyMock.anyInt(),
                                         EasyMock.eq(expectedConfigs)))
        .andReturn(EasyMock.niceMock(CachedSchemaRegistryClient.class));

    EasyMock.replay(clientFactory);

    // When:
    final SchemaRegistryClient schemaRegistryClient =
        new KsqlSchemaRegistryClientFactory(config,
                                            restServiceSupplier,
                                            sslFactory,
                                            clientFactory).get();

    // Then:
    EasyMock.verify(clientFactory);
  }


  private void setUpMocksWithExpectedConfig(final Map<String, Object> expectedConfigs) {
    sslFactory.configure(expectedConfigs);
    EasyMock.expectLastCall();

    restService.setSslSocketFactory(EasyMock.anyObject(SSL_CONTEXT.getSocketFactory().getClass()));
    EasyMock.expectLastCall();

    EasyMock.replay(restService, sslFactory);
  }

  private static Map<String, Object> defaultConfigs() {
    return config().valuesWithPrefixOverride(KsqlConfig.KSQL_SCHEMA_REGISTRY_PREFIX);
  }

  private static KsqlConfig config() {
    return new KsqlConfig(ImmutableMap.of());
  }

  private static KsqlConfig config(final String k1, final Object v1) {
    return new KsqlConfig(ImmutableMap.of(k1, v1));
  }

  // Can't mock SSLContext.
  private static SSLContext getTestSslContext() {
    final SslFactory sslFactory = new SslFactory(Mode.CLIENT);

    final Map<String, Object> configs = new KsqlConfig(Collections.emptyMap())
        .valuesWithPrefixOverride(KsqlConfig.KSQL_SCHEMA_REGISTRY_PREFIX);

    sslFactory.configure(configs);
    return sslFactory.sslContext();
  }
}