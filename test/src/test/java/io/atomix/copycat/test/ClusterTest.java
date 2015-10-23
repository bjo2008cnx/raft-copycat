/*
 * Copyright 2015 the original author or authors.
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
 * limitations under the License
 */
package io.atomix.copycat.test;

import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.LocalServerRegistry;
import io.atomix.catalyst.transport.LocalTransport;
import io.atomix.copycat.client.Command;
import io.atomix.copycat.client.CopycatClient;
import io.atomix.copycat.client.Query;
import io.atomix.copycat.client.session.Session;
import io.atomix.copycat.server.*;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;
import net.jodah.concurrentunit.ConcurrentTestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Copycat cluster test.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
@Test
public class ClusterTest extends ConcurrentTestCase {
  private static final File directory = new File("target/test-logs");
  protected volatile LocalServerRegistry registry;
  protected volatile int port;
  protected volatile List<Address> members;
  protected volatile List<CopycatClient> clients = new ArrayList<>();
  protected volatile List<CopycatServer> servers = new ArrayList<>();

  /**
   * Tests joining a server to an existing cluster.
   */
  public void testServerJoin() throws Throwable {
    createServers(3);
    CopycatServer joiner = createServer(nextAddress());
    joiner.open().thenRun(this::resume);
    await(5000);
  }

  /**
   * Tests joining a server after many entries have been committed.
   */
  public void testServerJoinLate() throws Throwable {
    createServers(3);
    CopycatClient client = createClient();
    submit(client, 0, 10000);
    await(30000);
    CopycatServer joiner = createServer(nextAddress());
    joiner.open().thenRun(this::resume);
    await(30000);
    joiner.onStateChange(state -> {
      if (state == CopycatServer.State.FOLLOWER)
        resume();
    });
    await(30000);
  }

  /**
   * Submits a bunch of commands recursively.
   */
  private void submit(CopycatClient client, int count, int total) {
    if (count < total) {
      client.submit(new TestCommand("Hello world!", Command.ConsistencyLevel.LINEARIZABLE)).whenComplete((result, error) -> {
        threadAssertNull(error);
        submit(client, count + 1, total);
      });
    } else {
      resume();
    }
  }

  /**
   * Tests leaving a sever from a cluster.
   */
  public void testServerLeave() throws Throwable {
    List<CopycatServer> servers = createServers(3);
    CopycatServer server = servers.get(0);
    server.close().thenRun(this::resume);
    await(5000);
  }

  /**
   * Tests leaving the leader from a cluster.
   */
  public void testLeaderLeave() throws Throwable {
    List<CopycatServer> servers = createServers(3);
    CopycatServer server = servers.stream().filter(s -> s.state() == RaftServer.State.LEADER).findFirst().get();
    server.close().thenRun(this::resume);
    await(5000);
  }

  /**
   * Tests scaling the cluster from 1 node to 3 nodes and back.
   */
  public void testScale() throws Throwable {
    RaftServer s1 = createServer(nextAddress()).open().get();
    RaftServer s2 = createServer(nextAddress()).open().get();
    RaftServer s3 = createServer(nextAddress()).open().get();
    RaftServer s4 = createServer(nextAddress()).open().get();
    RaftServer s5 = createServer(nextAddress()).open().get();
    s1.close().join();
    s2.close().join();
    s3.close().join();
    s4.close().join();
    s5.close().join();
  }

  /**
   * Tests keeping a client session alive.
   */
  public void testClientKeepAlive() throws Throwable {
    createServers(3);
    CopycatClient client = createClient();
    Thread.sleep(Duration.ofSeconds(10).toMillis());
    threadAssertTrue(client.session().isOpen());
  }

  /**
   * Tests submitting a command.
   */
  public void testOneNodeSubmitCommandWithNoneConsistency() throws Throwable {
    testSubmitCommand(1, Command.ConsistencyLevel.NONE);
  }

  /**
   * Tests submitting a command.
   */
  public void testOneNodeSubmitCommandWithSequentialConsistency() throws Throwable {
    testSubmitCommand(1, Command.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a command.
   */
  public void testOneNodeSubmitCommandWithLinearizableConsistency() throws Throwable {
    testSubmitCommand(1, Command.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a command.
   */
  public void testTwoNodeSubmitCommandWithNoneConsistency() throws Throwable {
    testSubmitCommand(2, Command.ConsistencyLevel.NONE);
  }

  /**
   * Tests submitting a command.
   */
  public void testTwoNodeSubmitCommandWithSequentialConsistency() throws Throwable {
    testSubmitCommand(2, Command.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a command.
   */
  public void testTwoNodeSubmitCommandWithLinearizableConsistency() throws Throwable {
    testSubmitCommand(2, Command.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a command.
   */
  public void testThreeNodeSubmitCommandWithNoneConsistency() throws Throwable {
    testSubmitCommand(3, Command.ConsistencyLevel.NONE);
  }

  /**
   * Tests submitting a command.
   */
  public void testThreeNodeSubmitCommandWithSequentialConsistency() throws Throwable {
    testSubmitCommand(3, Command.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a command.
   */
  public void testThreeNodeSubmitCommandWithLinearizableConsistency() throws Throwable {
    testSubmitCommand(3, Command.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a command.
   */
  public void testFourNodeSubmitCommandWithNoneConsistency() throws Throwable {
    testSubmitCommand(4, Command.ConsistencyLevel.NONE);
  }

  /**
   * Tests submitting a command.
   */
  public void testFourNodeSubmitCommandWithSequentialConsistency() throws Throwable {
    testSubmitCommand(4, Command.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a command.
   */
  public void testFourNodeSubmitCommandWithLinearizableConsistency() throws Throwable {
    testSubmitCommand(4, Command.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a command.
   */
  public void testFiveNodeSubmitCommandWithNoneConsistency() throws Throwable {
    testSubmitCommand(5, Command.ConsistencyLevel.NONE);
  }

  /**
   * Tests submitting a command.
   */
  public void testFiveNodeSubmitCommandWithSequentialConsistency() throws Throwable {
    testSubmitCommand(5, Command.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a command.
   */
  public void testFiveNodeSubmitCommandWithLinearizableConsistency() throws Throwable {
    testSubmitCommand(5, Command.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a command with a configured consistency level.
   */
  private void testSubmitCommand(int nodes, Command.ConsistencyLevel consistency) throws Throwable {
    createServers(nodes);

    CopycatClient client = createClient();
    client.submit(new TestCommand("Hello world!", consistency)).thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });

    await(5000);
  }

  /**
   * Tests submitting a command.
   */
  public void testTwoOfThreeNodeSubmitCommandWithNoneConsistency() throws Throwable {
    testSubmitCommand(2, 3, Command.ConsistencyLevel.NONE);
  }

  /**
   * Tests submitting a command.
   */
  public void testTwoOfThreeNodeSubmitCommandWithSequentialConsistency() throws Throwable {
    testSubmitCommand(2, 3, Command.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a command.
   */
  public void testTwoOfThreeNodeSubmitCommandWithLinearizableConsistency() throws Throwable {
    testSubmitCommand(2, 3, Command.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a command.
   */
  public void testTwoOfFourNodeSubmitCommandWithNoneConsistency() throws Throwable {
    testSubmitCommand(3, 4, Command.ConsistencyLevel.NONE);
  }

  /**
   * Tests submitting a command.
   */
  public void testTwoOfFourNodeSubmitCommandWithSequentialConsistency() throws Throwable {
    testSubmitCommand(3, 4, Command.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a command.
   */
  public void testTwoOfFourNodeSubmitCommandWithLinearizableConsistency() throws Throwable {
    testSubmitCommand(3, 4, Command.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a command.
   */
  public void testThreeOfFiveNodeSubmitCommandWithNoneConsistency() throws Throwable {
    testSubmitCommand(3, 5, Command.ConsistencyLevel.NONE);
  }

  /**
   * Tests submitting a command.
   */
  public void testThreeOfFiveNodeSubmitCommandWithSequentialConsistency() throws Throwable {
    testSubmitCommand(3, 5, Command.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a command.
   */
  public void testThreeOfFiveNodeSubmitCommandWithLinearizableConsistency() throws Throwable {
    testSubmitCommand(3, 5, Command.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a command to a partial cluster.
   */
  private void testSubmitCommand(int live, int total, Command.ConsistencyLevel consistency) throws Throwable {
    createServers(live, total);

    CopycatClient client = createClient();
    client.submit(new TestCommand("Hello world!", consistency)).thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });

    await(5000);
  }

  /**
   * Tests submitting a query.
   */
  public void testOneNodeSubmitQueryWithCausalConsistency() throws Throwable {
    testSubmitQuery(1, Query.ConsistencyLevel.CAUSAL);
  }

  /**
   * Tests submitting a query.
   */
  public void testOneNodeSubmitQueryWithSequentialConsistency() throws Throwable {
    testSubmitQuery(1, Query.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a query.
   */
  public void testOneNodeSubmitQueryWithBoundedLinearizableConsistency() throws Throwable {
    testSubmitQuery(1, Query.ConsistencyLevel.BOUNDED_LINEARIZABLE);
  }

  /**
   * Tests submitting a query.
   */
  public void testOneNodeSubmitQueryWithLinearizableConsistency() throws Throwable {
    testSubmitQuery(1, Query.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a query.
   */
  public void testTwoNodeSubmitQueryWithCausalConsistency() throws Throwable {
    testSubmitQuery(2, Query.ConsistencyLevel.CAUSAL);
  }

  /**
   * Tests submitting a query.
   */
  public void testTwoNodeSubmitQueryWithSequentialConsistency() throws Throwable {
    testSubmitQuery(2, Query.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a query.
   */
  public void testTwoNodeSubmitQueryWithBoundedLinearizableConsistency() throws Throwable {
    testSubmitQuery(2, Query.ConsistencyLevel.BOUNDED_LINEARIZABLE);
  }

  /**
   * Tests submitting a query.
   */
  public void testTwoNodeSubmitQueryWithLinearizableConsistency() throws Throwable {
    testSubmitQuery(2, Query.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a query.
   */
  public void testThreeNodeSubmitQueryWithCausalConsistency() throws Throwable {
    testSubmitQuery(3, Query.ConsistencyLevel.CAUSAL);
  }

  /**
   * Tests submitting a query.
   */
  public void testThreeNodeSubmitQueryWithSequentialConsistency() throws Throwable {
    testSubmitQuery(3, Query.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a query.
   */
  public void testThreeNodeSubmitQueryWithBoundedLinearizableConsistency() throws Throwable {
    testSubmitQuery(3, Query.ConsistencyLevel.BOUNDED_LINEARIZABLE);
  }

  /**
   * Tests submitting a query.
   */
  public void testThreeNodeSubmitQueryWithLinearizableConsistency() throws Throwable {
    testSubmitQuery(3, Query.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a query.
   */
  public void testFourNodeSubmitQueryWithCausalConsistency() throws Throwable {
    testSubmitQuery(4, Query.ConsistencyLevel.CAUSAL);
  }

  /**
   * Tests submitting a query.
   */
  public void testFourNodeSubmitQueryWithSequentialConsistency() throws Throwable {
    testSubmitQuery(4, Query.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a query.
   */
  public void testFourNodeSubmitQueryWithBoundedLinearizableConsistency() throws Throwable {
    testSubmitQuery(4, Query.ConsistencyLevel.BOUNDED_LINEARIZABLE);
  }

  /**
   * Tests submitting a query.
   */
  public void testFourNodeSubmitQueryWithLinearizableConsistency() throws Throwable {
    testSubmitQuery(4, Query.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a query.
   */
  public void testFiveNodeSubmitQueryWithCausalConsistency() throws Throwable {
    testSubmitQuery(5, Query.ConsistencyLevel.CAUSAL);
  }

  /**
   * Tests submitting a query.
   */
  public void testFiveNodeSubmitQueryWithSequentialConsistency() throws Throwable {
    testSubmitQuery(5, Query.ConsistencyLevel.SEQUENTIAL);
  }

  /**
   * Tests submitting a query.
   */
  public void testFiveNodeSubmitQueryWithBoundedLinearizableConsistency() throws Throwable {
    testSubmitQuery(5, Query.ConsistencyLevel.BOUNDED_LINEARIZABLE);
  }

  /**
   * Tests submitting a query.
   */
  public void testFiveNodeSubmitQueryWithLinearizableConsistency() throws Throwable {
    testSubmitQuery(5, Query.ConsistencyLevel.LINEARIZABLE);
  }

  /**
   * Tests submitting a query with a configured consistency level.
   */
  private void testSubmitQuery(int nodes, Query.ConsistencyLevel consistency) throws Throwable {
    createServers(nodes);

    CopycatClient client = createClient();
    client.submit(new TestQuery("Hello world!", consistency)).thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });

    await(5000);
  }

  /**
   * Tests submitting a sequential event.
   */
  public void testOneNodeSequentialEvent() throws Throwable {
    testSequentialEvent(1);
  }

  /**
   * Tests submitting a sequential event.
   */
  public void testTwoNodeSequentialEvent() throws Throwable {
    testSequentialEvent(2);
  }

  /**
   * Tests submitting a sequential event.
   */
  public void testThreeNodeSequentialEvent() throws Throwable {
    testSequentialEvent(3);
  }

  /**
   * Tests submitting a sequential event.
   */
  public void testFourNodeSequentialEvent() throws Throwable {
    testSequentialEvent(4);
  }

  /**
   * Tests submitting a sequential event.
   */
  public void testFiveNodeSequentialEvent() throws Throwable {
    testSequentialEvent(5);
  }

  /**
   * Tests submitting a sequential event.
   */
  private void testSequentialEvent(int nodes) throws Throwable {
    createServers(nodes);

    CopycatClient client = createClient();
    client.session().onEvent("test", message -> {
      threadAssertEquals(message, "Hello world!");
      resume();
    });

    client.submit(new TestEvent("Hello world!", true, Command.ConsistencyLevel.SEQUENTIAL)).thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });

    await(5000, 2);
  }

  /**
   * Tests submitting sequential events.
   */
  public void testOneNodeSequentialEvents() throws Throwable {
    testSequentialEvents(1);
  }

  /**
   * Tests submitting sequential events.
   */
  public void testTwoNodeSequentialEvents() throws Throwable {
    testSequentialEvents(2);
  }

  /**
   * Tests submitting sequential events.
   */
  public void testThreeNodeSequentialEvents() throws Throwable {
    testSequentialEvents(3);
  }

  /**
   * Tests submitting sequential events.
   */
  public void testFourNodeSequentialEvents() throws Throwable {
    testSequentialEvents(4);
  }

  /**
   * Tests submitting sequential events.
   */
  public void testFiveNodeSequentialEvents() throws Throwable {
    testSequentialEvents(5);
  }

  /**
   * Tests submitting sequential events to all sessions.
   */
  private void testSequentialEvents(int nodes) throws Throwable {
    createServers(nodes);

    CopycatClient client = createClient();
    client.session().onEvent("test", message -> {
      threadAssertEquals(message, "Hello world!");
      resume();
    });
    createClient().session().onEvent("test", message -> {
      threadAssertEquals(message, "Hello world!");
      resume();
    });
    createClient().session().onEvent("test", message -> {
      threadAssertEquals(message, "Hello world!");
      resume();
    });

    client.submit(new TestEvent("Hello world!", false, Command.ConsistencyLevel.SEQUENTIAL)).thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });

    await(5000, 4);
  }

  /**
   * Tests submitting a linearizable event.
   */
  public void testOneNodeLinearizableEvent() throws Throwable {
    testLinearizableEvent(1);
  }

  /**
   * Tests submitting a linearizable event.
   */
  public void testTwoNodeLinearizableEvent() throws Throwable {
    testLinearizableEvent(2);
  }

  /**
   * Tests submitting a linearizable event.
   */
  public void testThreeNodeLinearizableEvent() throws Throwable {
    testLinearizableEvent(3);
  }

  /**
   * Tests submitting a linearizable event.
   */
  public void testFourNodeLinearizableEvent() throws Throwable {
    testLinearizableEvent(4);
  }

  /**
   * Tests submitting a linearizable event.
   */
  public void testFiveNodeLinearizableEvent() throws Throwable {
    testLinearizableEvent(5);
  }

  /**
   * Tests submitting an event command.
   */
  private void testLinearizableEvent(int nodes) throws Throwable {
    createServers(nodes);

    AtomicInteger counter = new AtomicInteger();

    CopycatClient client = createClient();
    client.session().onEvent("test", message -> {
      threadAssertEquals(message, "Hello world!");
      counter.incrementAndGet();
      resume();
    });

    client.submit(new TestEvent("Hello world!", true, Command.ConsistencyLevel.LINEARIZABLE)).thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      threadAssertEquals(counter.get(), 1);
      resume();
    });

    await(5000, 2);
  }

  /**
   * Tests submitting linearizable events.
   */
  public void testOneNodeLinearizableEvents() throws Throwable {
    testLinearizableEvents(1);
  }

  /**
   * Tests submitting linearizable events.
   */
  public void testTwoNodeLinearizableEvents() throws Throwable {
    testLinearizableEvents(2);
  }

  /**
   * Tests submitting linearizable events.
   */
  public void testThreeNodeLinearizableEvents() throws Throwable {
    testLinearizableEvents(3);
  }

  /**
   * Tests submitting linearizable events.
   */
  public void testFourNodeLinearizableEvents() throws Throwable {
    testLinearizableEvents(4);
  }

  /**
   * Tests submitting linearizable events.
   */
  public void testFiveNodeLinearizableEvents() throws Throwable {
    testLinearizableEvents(5);
  }

  /**
   * Tests submitting a linearizable event that publishes to all sessions.
   */
  private void testLinearizableEvents(int nodes) throws Throwable {
    createServers(nodes);

    AtomicInteger counter = new AtomicInteger();

    CopycatClient client = createClient();
    client.session().onEvent("test", message -> {
      threadAssertEquals(message, "Hello world!");
      counter.incrementAndGet();
      resume();
    });
    createClient().session().onEvent("test", message -> {
      threadAssertEquals(message, "Hello world!");
      counter.incrementAndGet();
      resume();
    });
    createClient().session().onEvent("test", message -> {
      threadAssertEquals(message, "Hello world!");
      counter.incrementAndGet();
      resume();
    });

    client.submit(new TestEvent("Hello world!", false, Command.ConsistencyLevel.LINEARIZABLE)).thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      threadAssertEquals(counter.get(), 3);
      resume();
    });

    await(5000, 4);
  }

  /**
   * Tests submitting linearizable events.
   */
  public void testFiveNodeCommandBeforeEvent() throws Throwable {
    testCommandBeforeEvent(5);
  }

  /**
   * Tests submitting a linearizable event that publishes to all sessions.
   */
  private void testCommandBeforeEvent(int nodes) throws Throwable {
    createServers(nodes);

    AtomicInteger counter = new AtomicInteger();

    CopycatClient client = createClient();
    client.session().onEvent("test", message -> {
      threadAssertEquals(message, "Hello world!");
      counter.incrementAndGet();
      resume();
    });

    client.submit(new TestCommand("Hello world!", Command.ConsistencyLevel.LINEARIZABLE)).thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      resume();
    });

    client.submit(new TestEvent("Hello world!", true, Command.ConsistencyLevel.LINEARIZABLE)).thenAccept(result -> {
      threadAssertEquals(result, "Hello world!");
      threadAssertEquals(counter.get(), 1);
      resume();
    });

    await(5000, 3);
  }

  /**
   * Tests submitting linearizable events.
   */
  public void testFiveNodeManyEvents() throws Throwable {
    testManyEvents(5);
  }

  /**
   * Tests submitting a linearizable event that publishes to all sessions.
   */
  private void testManyEvents(int nodes) throws Throwable {
    createServers(nodes);

    AtomicReference<String> value = new AtomicReference<>();

    CopycatClient client = createClient();
    client.session().onEvent("test", message -> {
      threadAssertEquals(message, value.get());
      resume();
    });

    for (int i = 0 ; i < 1000; i++) {
      String event = UUID.randomUUID().toString();
      value.set(event);
      client.submit(new TestEvent(event, true, Command.ConsistencyLevel.LINEARIZABLE)).thenAccept(result -> {
        threadAssertEquals(result, event);
        resume();
      });

      await(10000, 2);
    }
  }

  /**
   * Tests submitting linearizable events.
   */
  public void testThreeNodesManyEventsAfterLeaderShutdown() throws Throwable {
    testManyEventsAfterLeaderShutdown(3);
  }

  /**
   * Tests submitting linearizable events.
   */
  public void testFiveNodesManyEventsAfterLeaderShutdown() throws Throwable {
    testManyEventsAfterLeaderShutdown(5);
  }

  /**
   * Tests submitting a linearizable event that publishes to all sessions.
   */
  private void testManyEventsAfterLeaderShutdown(int nodes) throws Throwable {
    List<CopycatServer> servers = createServers(nodes);

    AtomicReference<String> value = new AtomicReference<>();

    CopycatClient client = createClient();
    client.session().onEvent("test", message -> {
      threadAssertEquals(message, value.get());
      resume();
    });

    for (int i = 0 ; i < 100; i++) {
      String event = UUID.randomUUID().toString();
      value.set(event);
      client.submit(new TestEvent(event, true, Command.ConsistencyLevel.LINEARIZABLE)).thenAccept(result -> {
        threadAssertEquals(result, event);
        resume();
      });

      await(5000, 2);
    }

    CopycatServer leader = servers.stream().filter(s -> s.state() == CopycatServer.State.LEADER).findFirst().get();
    leader.close().join();

    for (int i = 0 ; i < 100; i++) {
      String event = UUID.randomUUID().toString();
      value.set(event);
      client.submit(new TestEvent(event, true, Command.ConsistencyLevel.LINEARIZABLE)).thenAccept(result -> {
        threadAssertEquals(result, event);
        resume();
      });

      await(5000, 2);
    }
  }

  /**
   * Tests submitting linearizable events.
   */
  public void testFiveNodeManySessionsManyEvents() throws Throwable {
    testManySessionsManyEvents(5);
  }

  /**
   * Tests submitting a linearizable event that publishes to all sessions.
   */
  private void testManySessionsManyEvents(int nodes) throws Throwable {
    createServers(nodes);

    AtomicReference<String> value = new AtomicReference<>();

    CopycatClient client = createClient();
    client.session().onEvent("test", message -> {
      threadAssertEquals(message, value.get());
      resume();
    });

    createClient().session().onEvent("test", message -> {
      threadAssertEquals(message, value.get());
      resume();
    });

    createClient().session().onEvent("test", message -> {
      threadAssertEquals(message, value.get());
      resume();
    });

    for (int i = 0 ; i < 1000; i++) {
      String event = UUID.randomUUID().toString();
      value.set(event);
      client.submit(new TestEvent(event, false, Command.ConsistencyLevel.LINEARIZABLE)).thenAccept(result -> {
        threadAssertEquals(result, event);
        resume();
      });

      await(10000, 4);
    }
  }

  /**
   * Tests session expiring events.
   */
  public void testOneNodeExpireEvent() throws Throwable {
    testSessionExpire(1);
  }

  /**
   * Tests session expiring events.
   */
  public void testThreeNodeExpireEvent() throws Throwable {
    testSessionExpire(3);
  }

  /**
   * Tests session expiring events.
   */
  public void testFiveNodeExpireEvent() throws Throwable {
    testSessionExpire(5);
  }

  /**
   * Tests a session expiring.
   */
  private void testSessionExpire(int nodes) throws Throwable {
    createServers(nodes);

    CopycatClient client1 = createClient();
    CopycatClient client2 = createClient();
    client1.session().onEvent("expired", this::resume);
    client1.submit(new TestExpire()).thenRun(this::resume);
    client2.close().thenRun(this::resume);
    await(Duration.ofSeconds(10).toMillis(), 3);
  }

  /**
   * Returns the next server address.
   *
   * @return The next server address.
   */
  private Address nextAddress() {
    Address address = new Address("localhost", port++);
    members.add(address);
    return address;
  }

  /**
   * Creates a set of Copycat servers.
   */
  private List<CopycatServer> createServers(int nodes) throws Throwable {
    List<CopycatServer> servers = new ArrayList<>();

    List<Address> members = new ArrayList<>();
    for (int i = 0; i < nodes; i++) {
      members.add(nextAddress());
    }

    for (int i = 0; i < nodes; i++) {
      CopycatServer server = createServer(members.get(i));
      server.open().thenRun(this::resume);
      servers.add(server);
    }

    await(5000, nodes);

    return servers;
  }

  /**
   * Creates a set of Copycat servers.
   */
  private List<CopycatServer> createServers(int live, int total) throws Throwable {
    List<CopycatServer> servers = new ArrayList<>();

    List<Address> members = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      members.add(nextAddress());
    }

    for (int i = 0; i < live; i++) {
      CopycatServer server = createServer(members.get(i));
      server.open().thenRun(this::resume);
      servers.add(server);
    }

    await(5000, live);

    return servers;
  }

  /**
   * Creates a Copycat server.
   */
  private CopycatServer createServer(Address address) {
    CopycatServer server = CopycatServer.builder(address, members)
      .withTransport(new LocalTransport(registry))
      .withStorage(new Storage(StorageLevel.MEMORY))
      .withStateMachine(new TestStateMachine())
      .build();
    servers.add(server);
    return server;
  }

  /**
   * Creates a Copycat client.
   */
  private CopycatClient createClient() throws Throwable {
    CopycatClient client = CopycatClient.builder(members).withTransport(new LocalTransport(registry)).build();
    client.open().thenRun(this::resume);
    await(5000);
    clients.add(client);
    return client;
  }

  @BeforeMethod
  @AfterMethod
  public void clearTests() throws Exception {
    deleteDirectory(directory);
    registry = new LocalServerRegistry();
    members = new ArrayList<>();
    port = 5000;
    clients = new ArrayList<>();
    servers = new ArrayList<>();
  }

  /**
   * Deletes a directory recursively.
   */
  private void deleteDirectory(File directory) throws IOException {
    if (directory.exists()) {
      File[] files = directory.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            deleteDirectory(file);
          } else {
            Files.delete(file.toPath());
          }
        }
      }
      Files.delete(directory.toPath());
    }
  }

  /**
   * Test state machine.
   */
  public static class TestStateMachine extends StateMachine {
    private Commit<TestExpire> expire;

    @Override
    protected void configure(StateMachineExecutor executor) {
      executor.register(TestCommand.class, this::command);
      executor.register(TestQuery.class, this::query);
      executor.register(TestEvent.class, this::event);
      executor.<TestExpire>register(TestExpire.class, this::expire);
    }

    @Override
    public void expire(Session session) {
      if (expire != null)
        expire.session().publish("expired");
    }

    private String command(Commit<TestCommand> commit) {
      try {
        return commit.operation().value();
      } finally {
        commit.clean();
      }
    }

    private String query(Commit<TestQuery> commit) {
      try {
        return commit.operation().value();
      } finally {
        commit.close();
      }
    }

    private String event(Commit<TestEvent> commit) {
      try {
        if (commit.operation().own()) {
          commit.session().publish("test", commit.operation().value());
        } else {
          for (Session session : sessions()) {
            session.publish("test", commit.operation().value());
          }
        }
        return commit.operation().value();
      } finally {
        commit.clean();
      }
    }

    private void expire(Commit<TestExpire> commit) {
      this.expire = commit;
    }
  }

  /**
   * Test command.
   */
  public static class TestCommand implements Command<String> {
    private String value;
    private ConsistencyLevel consistency;

    public TestCommand(String value, ConsistencyLevel consistency) {
      this.value = value;
      this.consistency = consistency;
    }

    @Override
    public ConsistencyLevel consistency() {
      return consistency;
    }

    public String value() {
      return value;
    }
  }

  /**
   * Test query.
   */
  public static class TestQuery implements Query<String> {
    private String value;
    private ConsistencyLevel consistency;

    public TestQuery(String value, ConsistencyLevel consistency) {
      this.value = value;
      this.consistency = consistency;
    }

    @Override
    public ConsistencyLevel consistency() {
      return consistency;
    }

    public String value() {
      return value;
    }
  }

  /**
   * Test event.
   */
  public static class TestEvent implements Command<String> {
    private String value;
    private boolean own;
    private ConsistencyLevel consistency;

    public TestEvent(String value, boolean own, ConsistencyLevel consistency) {
      this.value = value;
      this.own = own;
      this.consistency = consistency;
    }

    @Override
    public Command.ConsistencyLevel consistency() {
      return consistency;
    }

    public String value() {
      return value;
    }

    public boolean own() {
      return own;
    }
  }

  /**
   * Test event.
   */
  public static class TestExpire implements Command<Void> {
  }

}
