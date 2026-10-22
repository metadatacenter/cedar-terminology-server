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

  private static String readOnly(Path file) {
    return file + "?open_mode=" + SQLiteOpenMode.READONLY.flag;
  }

  private static void createSnapshot(Path file) throws SQLException {
    try (SnapshotStore writer = SnapshotStore.openFile(file.toString())) {
      writer.initSchema();
    }
  }
}
