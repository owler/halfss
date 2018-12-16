package v1.post

import java.sql.{Connection, DriverManager}
import java.util.Date
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import play.api.libs.concurrent.CustomExecutionContext
import play.api.libs.json._
import play.api.mvc.{Result, Results}
import play.api.{Logger, MarkerContext}
import v1.post.data._

import scala.collection.Map
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.io.Source


class PostExecutionContext @Inject()(actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "repository.dispatcher")

/**
  * A pure non-blocking interface for the PostRepository.
  */
trait PostRepository {
  def getNow: Long

  def createAccount(data: Account)(implicit mc: MarkerContext): Future[Result]

  def updateAccount(id: Int, data: AccountPost)(implicit mc: MarkerContext): Future[Result]

  def getAccount(id: Int)(implicit mc: MarkerContext): Future[Option[Account]]
}

/**
  * A trivial implementation for the Post Repository.
  *
  * A custom execution context is used here to establish that blocking operations should be
  * executed in a different thread than Play's ExecutionContext, which is used for CPU bound tasks
  * such as rendering.
  */
@Singleton
class PostRepositoryImpl @Inject()()(implicit ec: PostExecutionContext) extends PostRepository {
  import scala.collection.JavaConverters._
  private val logger = Logger(this.getClass)
  //val rootzip = new java.util.zip.ZipFile("/tmp/data/data.zip")
  val rootzip = new java.util.zip.ZipFile("data.zip")
  var now = 0L//Source.fromFile("/tmp/data/options.txt").getLines.toList.head.toLong * 1000
  rootzip.entries.asScala.filter(_.getName.contains("options")).foreach(e=>
    now = Source.fromInputStream(rootzip.getInputStream(e)).getLines.toList.head.toLong * 1000
  )
  println("data.zip timestamp " + new Date(now))


  //val dbUrl = "jdbc:sqlite:/tmp/data/travel.db"
  val dbUrl = "jdbc:sqlite::memory:"
  private implicit var conn = DriverManager.getConnection(dbUrl)
  private val start = System.currentTimeMillis()
  createDB()
  loadDb()
  createIndex()
  private val end = System.currentTimeMillis()
  println("All data loaded in " + (end - start) / 1000 + "s. Accounts: " + getCount("Accounts"))
  System.gc()

  import java.sql.SQLException

  @throws[ClassNotFoundException]
  @throws[SQLException]
  def createDB(): Unit = {
    val statmt = conn.createStatement()
    statmt.execute("CREATE TABLE if not exists Accounts (id INTEGER PRIMARY KEY, email text, fname text, sname text, phone text, sex text, birth integer, country text, city text);")
    statmt.close()
    System.out.println("Таблица создана или уже существует.")
  }

  def loadDb() = {

    rootzip.entries.asScala.
      filter(_.getName.endsWith(".json")).
      foreach { e =>
        println(e.getName)
        val json: JsValue = Json.parse(rootzip.getInputStream(e))
        e match {
          case _ if e.getName.contains("accounts") =>
            val Accounts: Reads[Seq[Account]] = (JsPath \ "accounts").read[Seq[Account]]
            json.validate[Seq[Account]](Accounts) match {
              case s: JsSuccess[Seq[Account]] => writeAccounts(s.get)
              case e: JsError =>
                println(e)
            }
        }
      }

    rootzip.close()
  }

  def createIndex(): Unit = {
    val u2e_Idx = "CREATE UNIQUE INDEX idx_account_email ON Accounts (email);"
    val statmt = conn.createStatement()
    statmt.execute(u2e_Idx)
    System.out.println("Индекс u2e_Idx создан.")
    statmt.close()
  }

  def writeAccounts(Accounts: Iterable[Account])(implicit conn: Connection): Unit = {
    val statmt = conn.createStatement()
    val sb = new StringBuffer("INSERT INTO Accounts (id, email, fname, sname, phone, sex, birth, country, city) VALUES ")
      .append(
        Accounts.map(Account =>
          new StringBuffer("(").append(Account.id).append(",'")
            .append(Account.email).append("','")
            .append(Account.fname).append("','")
            .append(Account.sname).append("','")
            .append(Account.phone).append("','")
            .append(Account.sex).append("',")
            .append(Account.birth).append(",'")
            .append(Account.country).append("','")
            .append(Account.city).append("'")
            .append(")").toString).mkString(",")
      )
      .append(";")
    statmt.execute(sb.toString)
    statmt.close()
  }

  def deleteObj(id: Int, table: String): Unit = {
    val statmt = conn.createStatement()
    statmt.execute("DELETE from " + table + " WHERE id=" + id)
    statmt.close()
  }

  override def getNow: Long = now

  override def createAccount(data: Account)(implicit mc: MarkerContext): Future[Result] = {
    Future {
      this.synchronized {
        getAccounts(sqlAccount + data.id) match {
          case None =>
            if (!isEmailExists(data.email)) {
              writeAccounts(List(data))
              Results.Ok(Json.obj())
            } else {
              Results.BadRequest
            }
          case Some(map) => Results.BadRequest
        }
      }
    }
  }

  private def isEmailExists(email: String): Boolean = {
    getAccounts("SELECT id, email, fname, sname, phone, sex, birth, country, city from Accounts WHERE email='" + email + "'") match {
      case None => false
      case Some(map) => true
    }

  }

  override def updateAccount(id: Int, data: AccountPost)(implicit mc: MarkerContext): Future[Result] = {
    Future {
      this.synchronized {
        getAccounts(sqlAccount + id) match {
          case None => logger.error("Wrong request,Account  id not found?" + id); Results.NotFound
          case Some(map) =>
            val account = map.values.head
            if (data.email.isDefined && account.email != data.email.get && isEmailExists(data.email.get)) {
              Results.BadRequest
            } else {
              val updatedAccount = Account(account.id,
                data.email.getOrElse(account.email),
                data.fname.getOrElse(account.fname),
                data.sname.getOrElse(account.sname),
                data.phone.getOrElse(account.phone),
                data.sex.getOrElse(account.sex),
                data.birth.getOrElse(account.birth),
                data.country.getOrElse(account.country),
                data.city.getOrElse(account.city)
              )
              deleteObj(updatedAccount.id, "Accounts")
              writeAccounts(List(updatedAccount))
              Results.Ok(Json.obj())
            }
        }
      }
    }
  }



  private def getCount(table: String)(implicit mc: MarkerContext): Int = {
    val statmt = conn.createStatement()
    val rs = statmt.executeQuery("SELECT count(*) from " + table)
    val count = if (rs.next()) {
      rs.getInt(1)
    } else {
      -1
    }
    statmt.close()
    count
  }

  private def isExist(table: String, id: Int)(implicit mc: MarkerContext): Boolean = {
    val statmt = conn.createStatement()
    val rs = statmt.executeQuery("SELECT count(*) from " + table + " WHERE id=" + id)
    val count = if (rs.next()) {
      rs.getInt(1) > 0
    } else {
      false
    }
    statmt.close()
    count
  }

  override def getAccount(id: Int)(implicit mc: MarkerContext): Future[Option[Account]] = {
    Future {
      val statmt = conn.createStatement()
      val rs = statmt.executeQuery(sqlAccount + id)
      val account = if (rs.next()) {
        Some(Account(rs.getInt("id"),
          rs.getString("email"),
          rs.getString("fname"),
          rs.getString("sname"),
          rs.getString("phone"),
          rs.getString("sex"),
          rs.getInt("birth"),
          rs.getString("country"),
          rs.getString("city")
        ))
      } else {
        None
      }
      statmt.close()
      account
    }
  }

  private def getAccounts(sql: String)(implicit mc: MarkerContext): Option[Map[Int, Account]] = {
    val statmt = conn.createStatement()
    val rs = statmt.executeQuery(sql)
    var map = Map[Int, Account]()

    while (rs.next()) {
      val id = rs.getInt("id")
      map += id -> Account(id,
        rs.getString("email"),
        rs.getString("fname"),
        rs.getString("sname"),
        rs.getString("phone"),
        rs.getString("sex"),
        rs.getInt("birth"),
        rs.getString("country"),
        rs.getString("city")
      )
    }
    statmt.close()
    if (map.isEmpty) {
      None
    } else {
      Some(map)
    }
  }


  val sqlAccount = "SELECT id, email, fname, sname, phone, sex, birth, country, city from Accounts WHERE id="
 }
