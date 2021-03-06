package cz.filmtit.core.search.postgres

import cz.filmtit.core.Configuration
import org.postgresql.util.PSQLException
import java.net.ConnectException
import com.weiglewilczek.slf4s.Logger
import cz.filmtit.core.model.data.{TranslationPair, MediaSource}
import cz.filmtit.core.model.storage.{MediaStorage, TranslationPairStorage}
import cz.filmtit.core.model.{TranslationSource, Language}
import java.sql.{BatchUpdateException, SQLException, DriverManager}
import java.util.Iterator
import collection.mutable.HashSet
import com.google.common.hash.{Funnels, BloomFilter}


/**
 * Base class for all translation pair storages using Postgres.
 *
 * @author Joachim Daiber
 *
 */

abstract class BaseStorage(
  l1: Language,
  l2: Language,
  source: TranslationSource,
  readOnly: Boolean = true
  ) extends TranslationPairStorage(l1, l2)
with MediaStorage {

  val log = Logger(this.getClass.getSimpleName)

  //Load the driver:
  classOf[org.postgresql.Driver]

  //Initialize connection
  val connection = try {
    DriverManager.getConnection(Configuration.dbConnector,
      Configuration.dbUser,
      Configuration.dbPassword)
  } catch {
    case e: PSQLException => {
      System.err.println("Could not connect to database %s. Please check if the DBMS is running and database exists.".format(Configuration.dbConnector))
      System.exit(1)
      null
    }
  }

  //Assure the database is in read-only mode if required.
  if (readOnly == true)
    connection.setReadOnly(true)

  var pairTable = "chunks"
  var chunkSourceMappingTable = "chunk_mediasources"
  var mediasourceTable = "mediasources"

  var maxCandidates = 200

  def reset() {
    System.err.println("Resetting BaseStorage (chunks, mediasources).")

    connection.createStatement().execute(
      "DROP TABLE IF EXISTS %s CASCADE; DROP TABLE IF EXISTS %s CASCADE; DROP TABLE IF EXISTS %s CASCADE;"
        .format(pairTable, mediasourceTable, chunkSourceMappingTable))

    connection.createStatement().execute(
      "CREATE TABLE %s (source_id SERIAL PRIMARY KEY, title TEXT, year VARCHAR(4), genres TEXT);"
        .format(mediasourceTable))

    connection.createStatement().execute(
      ("CREATE TABLE %s (pair_id SERIAL PRIMARY KEY, chunk_l1 TEXT, chunk_l2 TEXT, count INTEGER);")
        .format(pairTable))

    connection.createStatement().execute(
      ("CREATE TABLE %s (" +
        "pair_id INTEGER references %s(pair_id), " +
        "source_id INTEGER references %s(source_id)," +
        "PRIMARY KEY(pair_id, source_id)" +
        ");")
        .format(chunkSourceMappingTable, pairTable, mediasourceTable))


  }

  private val msInsertStmt = connection.prepareStatement("INSERT INTO %s(pair_id, source_id) VALUES(?, ?);".format(chunkSourceMappingTable))

  /**
   * The following two data structures are only used for indexing, hence
   * they are lazy and are not initiliazed in read-only mode.
   */
  private lazy val bloomFilter: BloomFilter[java.lang.CharSequence] = BloomFilter.create(Funnels.stringFunnel(), Configuration.expectedNumberOfTranslationPairs)
  private lazy val pairMediaSourceMappings:HashSet[Pair[Long, Long]] = HashSet[Pair[Long, Long]]()
  /**
   * Add a media source <-> translation pair correspondence to
   * the database.
   *
   * @param pairID
   * @param mediaSourceID
   */
  private def addMediaSourceForChunk(pairID: Long, mediaSourceID: Long) {

    if ( !(pairMediaSourceMappings.contains(Pair(pairID, mediaSourceID))) ) {
      msInsertStmt.setLong(1, pairID)
      msInsertStmt.setLong(2, mediaSourceID)
      msInsertStmt.execute()
      pairMediaSourceMappings.add(Pair(pairID, mediaSourceID))
    }

  }


  private val pairIDStmt = connection.prepareStatement("SELECT * FROM %s WHERE chunk_l1 = ? AND chunk_l2 = ? LIMIT 1;".format(pairTable))

  /**
   * Search for the translation pair in the database and return its
   * ID if it is present. This method is slow, it should only
   * be used while indexing!
   *
   * @param translationPair
   * @return
   */
  private def pairIDInDatabase(translationPair: TranslationPair): Option[Long] = {

    if ( bloomFilter.mightContain( "%s-%s".format(translationPair.chunkL1, translationPair.chunkL2) ) ) {
      pairIDStmt.setString(1, translationPair.chunkL1.surfaceform)
      pairIDStmt.setString(2, translationPair.chunkL2.surfaceform)
      pairIDStmt.execute()

      if (pairIDStmt.getResultSet.next()) {
        Some(pairIDStmt.getResultSet.getLong("pair_id"))
      }else{
        None
      }
    } else {
      None
    }
  }


  /**
   * This is the only place where {TranslationPair}s are actually
   * added to the database. All subclasses of BaseStorage work with the
   * translation pairs that were added to the database by this method.
   *
   * @param translationPairs a Traversable of translation pairs
   */
  def add(translationPairs: TraversableOnce[TranslationPair]) {

    val inStmt = connection.prepareStatement(("INSERT INTO %s (chunk_l1, chunk_l2, count) VALUES (?, ?, 1) RETURNING pair_id;").format(pairTable))
    val upStmt = connection.prepareStatement(("UPDATE %s SET count = count + 1 WHERE pair_id = ?;").format(pairTable))

    //Important for performance: Only commit after all INSERT statements are
    //executed:
    connection.setAutoCommit(false)

    System.err.println("Writing translation pairs to database...")
    translationPairs foreach { translationPair => {
      try {

        val pairID = pairIDInDatabase(translationPair) match {
          case None => {
            //Normal case: there is no equivalent translation pair in the database

            inStmt.setString(1, translationPair.chunkL1)
            inStmt.setString(2, translationPair.chunkL2)
            inStmt.execute()

            //Remember that we already put it into the database
            bloomFilter.put( "%s-%s".format(translationPair.chunkL1, translationPair.chunkL2) )

            //Get the pair_id of the new translation pair
            inStmt.getResultSet.next()
            inStmt.getResultSet.getLong("pair_id")
          }
          case Some(existingPairID) => {
            //Special case: there is an equivalent translation pair in the database,
            upStmt.setLong(1, existingPairID)
            upStmt.execute()
            existingPairID
          }
        }

        //Add the MediaSource as the source to the TP
        addMediaSourceForChunk(pairID, translationPair.mediaSources(0).id)
      } catch {
        case e: SQLException => {
          e.printStackTrace()
          System.err.println("Skipping translation pair due to database error: " + translationPair)
        }
      }
    }
    }

    //Commit the changes to the database:
    connection.commit()
  }


  def getMediaSource(id: Int): MediaSource = {
    val stmt = connection.prepareStatement("SELECT * FROM %s where source_id = ? LIMIT 1;".format(mediasourceTable))
    stmt.setLong(1, id)
    stmt.execute()
    stmt.getResultSet.next()

    new MediaSource(
      stmt.getResultSet.getString("title"),
      stmt.getResultSet.getString("year"),
      stmt.getResultSet.getString("genres")
    )
  }


  def addMediaSource(mediaSource: MediaSource): Long = {
    val stmt = connection.prepareStatement("INSERT INTO %s(title, year, genres) VALUES(?, ?, ?) RETURNING source_id;".format(mediasourceTable))

    stmt.setString(1, mediaSource.title)
    stmt.setString(2, mediaSource.year)
    stmt.setString(3, mediaSource.genres mkString ",")
    stmt.execute()

    stmt.getResultSet.next()
    stmt.getResultSet.getLong("source_id")
  }

}

