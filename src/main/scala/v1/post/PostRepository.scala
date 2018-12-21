package v1.post

import java.sql.{Connection, DriverManager}
import java.util.Date

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.libs.concurrent.CustomExecutionContext
import play.api.libs.json._
import play.api.mvc.{Result, Results}
import play.api.{Logger, MarkerContext}
import v1.post.data._

import scala.collection.{Map, mutable}
import scala.concurrent.Future
import scala.io.Source


class PostExecutionContext @Inject()(actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "repository.dispatcher")

/**
  * A pure non-blocking interface for the PostRepository.
  */
trait PostRepository {
  def getNow: Long
  def wrapInterests(str: List[String]): List[Int]
  def createAccount(data: Account)(implicit mc: MarkerContext): Future[Result]

  def updateAccount(id: Int, data: AccountPost)(implicit mc: MarkerContext): Future[Result]

  def getAccount(id: Int)(implicit mc: MarkerContext): Future[Option[Account]]

  def filter(list: Iterable[String], limit: Option[Int])(implicit mc: MarkerContext): Future[List[Account]]
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
  var interests = Array[String]()
  //val rootzip = new java.util.zip.ZipFile("/tmp/data/data.zip")
  val rootzip = new java.util.zip.ZipFile("data.zip")
  var now = 0L //Source.fromFile("/tmp/data/options.txt").getLines.toList.head.toLong * 1000
  rootzip.entries.asScala.filter(_.getName.contains("options")).foreach(e =>
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
  println("Total interests: " + interests.size)
  System.gc()

  import java.sql.SQLException

  @throws[ClassNotFoundException]
  @throws[SQLException]
  def createDB(): Unit = {
    val statmt = conn.createStatement()
    statmt.execute("CREATE TABLE if not exists Accounts (id INTEGER PRIMARY KEY, email text, fname text, sname text, phone text, sex text, birth integer, country text, city text, interests text);")
    statmt.execute("CREATE TABLE if not exists Likes (liker INTEGER, likee INTEGER, ts integer);")
    statmt.close()
    System.out.println("Таблица создана или уже существует.")
  }

  override def wrapInterests(str: List[String]): List[Int] = {
    str.map(s => interests.indexOf(s))
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

  def addInterests(values: List[String]): String ={
    values.map(v => addInterest(v)).sorted.mkString(",")
  }

  def addInterest(value: String): Int ={
    interests.indexOf(value) match {
      case -1 => interests = interests :+ value; interests.length
      case i: Int => i + 1
    }
  }

  def writeAccounts(accounts: Iterable[Account])(implicit conn: Connection): Unit = {
    val statmt = conn.createStatement()
    val interes = accounts.map(a => a.id -> a.interests.map(v => addInterests(v))).toMap
    println(interes(1))
    val sb = new StringBuffer("INSERT INTO Accounts (id, email, fname, sname, phone, sex, birth, country, city, interests) VALUES ")
      .append(
        accounts.map(account =>
          new StringBuffer("(").append(account.id).append(",'")
            .append(account.email).append("',")
            .append(unwrap(account.fname)).append(",")
            .append(unwrap(account.sname)).append(",")
            .append(unwrap(account.phone)).append(",'")
            .append(account.sex).append("',")
            .append(account.birth).append(",")
            .append(unwrap(account.country)).append(",")
            .append(unwrap(account.city)).append(",'")
            .append(interes(account.id).getOrElse(0))
            .append("')").toString).mkString(",")
      )
      .append(";")
    statmt.execute(sb.toString)
    val sb2= new StringBuffer("INSERT INTO Likes (liker, likee, ts) VALUES ")
      .append(
        accounts.map(account => account.likes.map(like => like.map(l =>
          new StringBuffer("(").append(account.id).append(",")
            .append(l.id).append(",")
            .append(l.ts).append(")").toString).mkString(","))
      ).filter(_.nonEmpty).map(_.getOrElse("")).mkString(",")).append(";")
    try {
      println(sb2.toString.take(1600))
      statmt.execute(sb2.toString)
    } catch {
      case e => println(e)
    }
    statmt.close()
  }

  def unwrap(str: Option[String]): String = {
    str match {
      case Some(v) => "'" + v + "'"
      case None => null
    }
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
                data.fname match {
                  case Some(v) => data.fname
                  case None => account.fname
                },
                data.sname match {
                  case Some(v) => data.sname
                  case None => account.sname
                },
                data.phone match {
                  case Some(v) => data.phone
                  case None => account.phone
                },
                data.sex.getOrElse(account.sex),
                data.birth.getOrElse(account.birth),
                data.country match {
                  case Some(value) => data.country
                  case None => account.country
                },
                data.city match {
                  case Some(value) => data.city
                  case None => account.city
                },
                data.interests match {
                  case Some(value) => data.interests
                  case None => account.interests
                },
                data.likes match {
                  case Some(value) => data.likes
                  case None => account.likes
                }

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

  def unwrapInterests(inter: String): Option[List[String]] = {
    Option(inter.split(",").toList)
  }

  override def getAccount(id: Int)(implicit mc: MarkerContext): Future[Option[Account]] = {
    Future {
      val statmt = conn.createStatement()
      val rs = statmt.executeQuery(sqlAccount + id)
      val account = if (rs.next()) {
        Some(Account(rs.getInt("id"),
          rs.getString("email"),
          Option(rs.getString("fname")),
          Option(rs.getString("sname")),
          Option(rs.getString("phone")),
          rs.getString("sex"),
          rs.getInt("birth"),
          Option(rs.getString("country")),
          Option(rs.getString("city")),
          unwrapInterests(rs.getString("interests")),
          Option(null)
        ))
      } else {
        None
      }
      statmt.close()
      account
    }
  }

  private def getAccounts(sql: String)(implicit mc: MarkerContext): Option[Map[Int, Account]] = {
    println(sql)
    val statmt = conn.createStatement()
    val rs = statmt.executeQuery(sql)
    var map = Map[Int, Account]()

    while (rs.next()) {
      val id = rs.getInt("id")
      map += id -> Account(id,
        rs.getString("email"),
        Option(rs.getString("fname")),
        Option(rs.getString("sname")),
        Option(rs.getString("phone")),
        rs.getString("sex"),
        rs.getInt("birth"),
        Option(rs.getString("country")),
        Option(rs.getString("city")),
        unwrapInterests(rs.getString("interests")),
        Option(null)
      )
    }
    statmt.close()
    if (map.isEmpty) {
      None
    } else {
      Some(map)
    }
  }

  val sqlAccountWhere = "SELECT id, email, fname, sname, phone, sex, birth, country, city, interests from Accounts "
  val sqlAccount = sqlAccountWhere + " WHERE id="


  override def filter(list: Iterable[String], limit: Option[Int])(implicit mc: MarkerContext): Future[List[Account]] = {
    Future {
      getAccounts(sqlAccountWhere + (if (list.nonEmpty) " WHERE " + list.mkString(" AND ") else "") +
        (limit match {
          case Some(i) => " LIMIT " + i
          case None => ""
        })
      ) match {
        case Some(map) => map.values.toList
        case None => List()
      }
    }
  }
}
