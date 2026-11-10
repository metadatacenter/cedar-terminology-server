package org.metadatacenter.terms.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteOpenMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a reading connection fails to open, its place in both allowances is given back.
 *
 * Neither count is ever reset, so a connection counted and never opened stays counted for the life
 * of the process. When enough of them accumulate in one store, every thread shares its first
 * connection. When enough accumulate across all stores, none of them can open another.
 */
public class ReadConnectionsTest {

  @Test
  public void aConnectionThatFailsToOpenIsGivenBack(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("served.sqlite");
    createSnapshot(file);
    String path = readOnly(file);
    Connection first = DriverManager.getConnection("jdbc:sqlite:" + path);
    try (ReadConnections reads = ReadConnections.of(path, first, c -> { }, 2)) {
      int taken = reads.taken();
      int takenByAll = ReadConnections.takenByAll();
      Files.delete(file);

      assertThrows(SQLException.class, reads::get);
      assertEquals(taken, reads.taken(), "the store still counts the connection it failed to open");
      assertEquals(takenByAll, ReadConnections.takenByAll(), "all stores still count it");

      // With its place given back, the store has room for this thread. Once the file returns, the
      // thread gets a connection of its own rather than the first one.
      createSnapshot(file);
      assertNotSame(first, reads.get());
    }
  }

  @Test
  public void aConnectionThatCannotBePreparedIsClosedAndGivenBack(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("served.sqlite");
    createSnapshot(file);
    String path = readOnly(file);
    Connection[] prepared = new Connection[1];
    ReadConnections.Configurer refuse = fresh -> {
      prepared[0] = fresh;
      throw new SQLException("refused");
    };
    try (ReadConnections reads =
             ReadConnections.of(path, DriverManager.getConnection("jdbc:sqlite:" + path), refuse, 2)) {
      int taken = reads.taken();
      int takenByAll = ReadConnections.takenByAll();

      assertThrows(SQLException.class, reads::get);
      assertEquals(taken, reads.taken(), "the store still counts the connection it could not prepare");
      assertEquals(takenByAll, ReadConnections.takenByAll(), "all stores still count it");
      assertTrue(prepared[0].isClosed(), "the connection it could not prepare was left open");
    }
  }

  @Test
  public void aStoreTheProcessHoldsOneOfKeepsItsOwnAllowance(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("index.sqlite");
    createSnapshot(file);
    String path = readOnly(file);
    Connection first = DriverManager.getConnection("jdbc:sqlite:" + path);
    try (ReadConnections reads = ReadConnections.single(path, first, c -> { }, 3)) {
      int takenByAll = ReadConnections.takenByAll();

      // Each thread gets a connection of its own until the store's own limit, whatever the snapshots
      // have taken, and none of them is charged to the allowance the snapshots share. The threads
      // stay alive while the others ask, as a busy server's workers do.
      Connection[] got = new Connection[3];
      java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
      Thread[] workers = new Thread[3];
      for (int i = 0; i < got.length; i++) {
        int index = i;
        java.util.concurrent.CountDownLatch holding = new java.util.concurrent.CountDownLatch(1);
        workers[i] = new Thread(() -> {
          got[index] = connectionOf(reads);
          holding.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
        workers[i].start();
        holding.await();
      }
      release.countDown();
      for (Thread worker : workers) {
        worker.join();
      }
      assertNotSame(first, got[0]);
      assertNotSame(first, got[1]);
      assertNotSame(got[0], got[1]);
      assertEquals(first, got[2], "past its own limit a thread shares the first connection");
      assertEquals(3, reads.taken());
      assertEquals(takenByAll, ReadConnections.takenByAll(), "the shared allowance is untouched");
    }
  }

  @Test
  public void aFinishedThreadsConnectionIsTakenOverRatherThanLost(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("served.sqlite");
    createSnapshot(file);
    String path = readOnly(file);
    Connection first = DriverManager.getConnection("jdbc:sqlite:" + path);
    try (ReadConnections reads = ReadConnections.single(path, first, c -> { }, 2)) {
      Connection[] got = new Connection[2];
      // A worker thread takes the store's only spare connection and then finishes, as a server's
      // pool retires an idle thread.
      Thread retired = new Thread(() -> got[0] = connectionOf(reads));
      retired.start();
      retired.join();
      assertNotSame(first, got[0]);
      assertEquals(2, reads.taken());

      // Its replacement must not be left sharing the first connection for the life of the process.
      Thread replacement = new Thread(() -> got[1] = connectionOf(reads));
      replacement.start();
      replacement.join();
      assertEquals(got[0], got[1], "the new thread takes over the finished thread's connection");
      assertEquals(2, reads.taken(), "and no further connection is opened for it");

      // A thread that is still running keeps its connection, so this one shares the first.
      assertEquals(first, takenWhileOthersLive(reads, first));
    }
  }

  private static Connection connectionOf(ReadConnections reads) {
    try {
      return reads.get();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  /** What a third thread gets while the thread holding the spare connection is still alive. */
  private static Connection takenWhileOthersLive(ReadConnections reads, Connection first) throws Exception {
    Connection[] held = new Connection[2];
    java.util.concurrent.CountDownLatch holding = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    Thread holder = new Thread(() -> {
      held[0] = connectionOf(reads);
      holding.countDown();
      try {
        release.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    holder.start();
    holding.await();
    Thread other = new Thread(() -> held[1] = connectionOf(reads));
    other.start();
    other.join();
    release.countDown();
    holder.join();
    assertNotSame(first, held[0]);
    return held[1];
  }

  private static String readOnly(Path file) {
    return file + "?open_mode=" + SQLiteOpenMode.READONLY.flag;
  }

  private static void createSnapshot(Path file) throws SQLException {
    try (SnapshotStore writer = SnapshotStore.openFile(file.toString())) {
      writer.initSchema();
    }
  }
}
