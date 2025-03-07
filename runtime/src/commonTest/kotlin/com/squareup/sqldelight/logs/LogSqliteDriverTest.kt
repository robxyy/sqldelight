package com.squareup.sqldelight.logs

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter.Transaction
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.logs.LogSqliteDriver
import kotlin.js.JsName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LogSqliteDriverTest {

  private lateinit var driver: LogSqliteDriver
  private lateinit var transacter: TransacterImpl
  private val logs = LinkedList<String>()

  @BeforeTest fun setup() {
    driver = LogSqliteDriver(FakeSqlDriver()) { log ->
      logs.add(log)
    }
    transacter = object : TransacterImpl(driver) {}
  }

  @AfterTest fun tearDown() {
    driver.close()
    logs.clear()
  }

  @JsName("insertLogsCorrect")
  @Test
  fun `insert logs are correct`() {
    val insert = { binders: SqlPreparedStatement.() -> Unit ->
      driver.execute(2, "INSERT INTO test VALUES (?, ?);", 2, binders)
    }

    insert {
      bindLong(1, 1)
      bindString(2, "Alec")
    }

    insert {}

    assertEquals("EXECUTE\n INSERT INTO test VALUES (?, ?);", logs[0])
    assertEquals(" [1, Alec]", logs[1])
    assertEquals("EXECUTE\n INSERT INTO test VALUES (?, ?);", logs[2])
  }

  @JsName("queryLogsCorrect")
  @Test
  fun `query logs are correct`() {
    val query = {
      driver.executeQuery(3, "SELECT * FROM test", {}, 0)
    }

    query()

    assertEquals("QUERY\n SELECT * FROM test", logs[0])
  }

  @JsName("transactionLogsCorrect")
  @Test
  fun `transaction logs are correct`() {
    transacter.transaction {}
    transacter.transaction { rollback() }
    transacter.transaction {
      val insert = { binders: SqlPreparedStatement.() -> Unit ->
        driver.execute(2, "INSERT INTO test VALUES (?, ?);", 2, binders)
      }

      insert {
        bindLong(1, 1)
        bindString(2, "Alec")
      }
    }

    assertEquals("TRANSACTION BEGIN", logs[0])
    assertEquals("TRANSACTION COMMIT", logs[1])
    assertEquals("TRANSACTION BEGIN", logs[2])
    assertEquals("TRANSACTION ROLLBACK", logs[3])
    assertEquals("TRANSACTION BEGIN", logs[4])
    assertEquals("EXECUTE\n INSERT INTO test VALUES (?, ?);", logs[5])
    assertEquals(" [1, Alec]", logs[6])
    assertEquals("TRANSACTION COMMIT", logs[7])
  }
}

class FakeSqlDriver : SqlDriver {
  override fun <R> executeQuery(
    identifier: Int?,
    sql: String,
    mapper: (SqlCursor) -> R,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?,
  ): QueryResult<R> {
    return QueryResult.Value(mapper(FakeSqlCursor()))
  }

  override fun execute(
    identifier: Int?,
    sql: String,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?,
  ): QueryResult<Long> {
    return QueryResult.Value(0)
  }

  override fun newTransaction(): QueryResult<Transaction> {
    return QueryResult.Value(FakeTransaction())
  }

  override fun currentTransaction(): Transaction? {
    return null
  }

  override fun addListener(listener: Query.Listener, queryKeys: Array<String>) {
  }

  override fun removeListener(listener: Query.Listener, queryKeys: Array<String>) {
  }

  override fun notifyListeners(queryKeys: Array<String>) {
  }

  override fun close() {
  }
}

class FakeSqlCursor : SqlCursor {
  override fun next(): Boolean {
    return false
  }

  override fun getString(index: Int): String? {
    return null
  }

  override fun getLong(index: Int): Long? {
    return null
  }

  override fun getBytes(index: Int): ByteArray? {
    return null
  }

  override fun getDouble(index: Int): Double? {
    return null
  }

  override fun getBoolean(index: Int): Boolean? {
    return null
  }
}

class FakeTransaction : Transaction() {
  override val enclosingTransaction: Transaction? = null

  override fun endTransaction(successful: Boolean): QueryResult<Unit> = QueryResult.Unit
}
