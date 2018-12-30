package v1.post

import java.sql.{Connection, DriverManager, ResultSet}
import java.util.Date

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.libs.concurrent.CustomExecutionContext
import play.api.libs.json._
import play.api.mvc.{Result, Results}
import play.api.{Logger, MarkerContext}
import v1.post.data._

import scala.concurrent.Future
import scala.io.Source


class PostExecutionContext @Inject()(actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "repository.dispatcher")

/**
  * A pure non-blocking interface for the PostRepository.
  */
trait PostRepository {
  def getNow: Long

  def getStatuses(): Array[String]

  def wrapInterests(str: List[String]): List[Int]

  def createAccount(data: Account)(implicit mc: MarkerContext): Future[Result]

  def updateAccount(id: Int, data: AccountPost)(implicit mc: MarkerContext): Future[Result]

  def getAccount(id: Int)(implicit mc: MarkerContext): Future[Option[Account]]

  def recommend(id: Int, list: List[String], limit: Option[Int])(implicit mc: MarkerContext): Future[List[Account]]
  def suggest(id: Int, list: List[String], limit: Option[Int])(implicit mc: MarkerContext): Future[List[Account]]

  def filter(keys: Iterable[String], list: Iterable[String], limit: Option[Int])(implicit mc: MarkerContext): Future[List[Account]]

  def group(keys: Iterable[String], list: Iterable[String], limit: Option[Int], order: Boolean)(implicit mc: MarkerContext): Future[List[Group]]
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
  var statuses = Array("свободны", "всё сложно", "заняты")
  //val rootzip = new java.util.zip.ZipFile("/tmp/data/data.zip")
  val rootzip = new java.util.zip.ZipFile("./data.zip")
  //var now = Source.fromFile("/tmp/data/options.txt").getLines.toList.head.toLong
  var now = Source.fromFile("./options.txt").getLines.toList.head.toLong
  /*  rootzip.entries.asScala.filter(_.getName.contains("options")).foreach(e =>
      now = Source.fromInputStream(rootzip.getInputStream(e)).getLines.toList.head.toLong
    )*/
  println("data.zip timestamp " + new Date(now * 1000))


  //val dbUrl = "jdbc:sqlite:./travel.db"
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
    statmt.execute("CREATE TABLE if not exists Accounts (id INTEGER PRIMARY KEY, joined INTEGER, status INTEGER,email text, fname text, sname text, phone text, sex text, birth integer, country text, city text, start INTEGER, finish INTEGER);")
    statmt.execute("CREATE TABLE if not exists Likes (liker INTEGER, likee INTEGER, ts integer);")
    statmt.execute("CREATE TABLE if not exists Interests (acc INTEGER, interests INTEGER);")
    statmt.close()
    System.out.println("Таблица создана или уже существует.")
  }

  override def wrapInterests(str: List[String]): List[Int] = {
    str.map(s => interests.indexOf(s))
  }


  override def getStatuses(): Array[String] = statuses

  def loadDb() = {
    rootzip.entries.asScala.
      filter(_.getName.endsWith(".json")).
      foreach { e =>
        println("loading " + e.getName)
        val json: JsValue = Json.parse(rootzip.getInputStream(e))
        e match {
          case _ if e.getName.contains("accounts") =>
            val Accounts: Reads[Seq[Account]] = (JsPath \ "accounts").read[Seq[Account]]
            json.validate[Seq[Account]](Accounts) match {
              case s: JsSuccess[Seq[Account]] => writeAccounts(s.get)
              case e: JsError =>
                println("Error loading DB " + e)
            }
        }
      }

    rootzip.close()
  }

  def createIndex(): Unit = {
    val u2e_Idx_email = "CREATE UNIQUE INDEX idx_account_email ON Accounts (email);"
    val u2e_Idx_status = "CREATE INDEX idx_account_status ON Accounts (status);"
    val u2e_Idx_interests = "CREATE INDEX idx_interests ON Interests (acc, interests);"
    val u2e_Idx_likes = "CREATE INDEX idx_likes ON Likes (liker, likee);"
    val statmt = conn.createStatement()
    statmt.execute(u2e_Idx_email)
    System.out.println("Индекс u2e_Idx_email создан.")
    statmt.execute(u2e_Idx_status)
    System.out.println("Индекс u2e_Idx_status создан.")
    statmt.execute(u2e_Idx_interests)
    System.out.println("Индекс u2e_Idx_interests создан.")
    statmt.execute(u2e_Idx_likes)
    System.out.println("Индекс u2e_Idx_likes создан.")
    statmt.close()
  }

  def addInterests(values: List[String]): String = {
    values.map(v => addInterest(v)).sorted.mkString(",")
  }

  def addInterest(value: String): Int = {
    interests.indexOf(value) match {
      case -1 => interests = interests :+ value; interests.length
      case i: Int => i + 1
    }
  }

  def writeAccounts(accounts: Iterable[Account])(implicit conn: Connection): Unit = {

    val statmt = conn.createStatement()
    try {
      val interes = accounts.map(a => a.id -> a.interests.map(v => addInterests(v))).toMap
      val sb = new StringBuffer("INSERT INTO Accounts (id, joined, status, email, fname, sname, phone, sex, birth, country, city, start, finish) VALUES ")
        .append(
          accounts.map(account =>
            new StringBuffer("(").append(account.id).append(",")
              .append(unwrapInt(account.joined)).append(",")
              .append(unwrapInt(account.status.map(statuses.indexOf(_)))).append(",'")
              .append(account.email).append("',")
              .append(unwrap(account.fname)).append(",")
              .append(unwrap(account.sname)).append(",")
              .append(unwrap(account.phone)).append(",")
              .append(unwrap(account.sex)).append(",")
              .append(unwrapInt(account.birth)).append(",")
              .append(unwrap(account.country)).append(",")
              .append(unwrap(account.city)).append(",")
              .append(account.premium.map(_.start).getOrElse(null)).append(",")
              .append(account.premium.map(_.finish).getOrElse(null))
              .append(")").toString).mkString(",")
        )
        .append(";")
      statmt.execute(sb.toString)

      val sb2 = new StringBuffer("INSERT INTO Likes (liker, likee, ts) VALUES ")
        .append(
          accounts.flatMap(account => account.likes.map(listLikes => listLikes.map(l =>
            new StringBuffer("(").append(account.id).append(",")
              .append(l.id).append(",")
              .append(l.ts).append(")").toString))
          ).flatten.mkString(",")).append(";")
      if (sb2.toString.contains("VALUES (")) {
        statmt.execute(sb2.toString)
      }

      val sb3 = new StringBuffer("INSERT INTO Interests (acc, interests) VALUES ")
        .append(
          accounts.flatMap(account => account.interests.map(listInterests => listInterests.map(l =>
            new StringBuffer("(").append(account.id).append(",")
              .append(interests.indexOf(l)).append(")").toString))
          ).flatten.mkString(",")).append(";")
      if (sb3.toString.contains("VALUES (")) {
        statmt.execute(sb3.toString)
      }
    } catch {
      case e: Throwable => println("Error " + e)
    }
    statmt.close()
  }

  def unwrap(str: Option[String]): String = {
    str match {
      case Some(v) => "'" + v + "'"
      case None => null
    }
  }

  def unwrapInt(str: Option[Int]): Int = {
    str match {
      case Some(v) => v
      case None => 0
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
        getAccounts(Set(), sqlAccount + data.id) match {
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
    getAccounts(Set(), "SELECT id, joined, email, fname, sname, phone, sex, birth, country, city, start, finish from Accounts WHERE email='" + email + "'") match {
      case None => false
      case Some(map) => true
    }

  }

  override def updateAccount(id: Int, data: AccountPost)(implicit mc: MarkerContext): Future[Result] = {
    Future {
      this.synchronized {
        //@todo add full list if column
        getAccounts(Set(), sqlAccount + id) match {
          case None => logger.error("Wrong request,Account  id not found?" + id); Results.NotFound
          case Some(map) =>
            val account = map.head
            if (data.email.isDefined && account.email != data.email.get && isEmailExists(data.email.get)) {
              Results.BadRequest
            } else {
              val updatedAccount = Account(account.id,
                data.joined match {
                  case Some(v) => data.joined
                  case None => account.joined
                },
                data.status match {
                  case Some(v) => data.status
                  case None => account.status
                },
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
                data.sex match {
                  case Some(v) => data.sex
                  case None => account.sex
                },
                data.birth match {
                  case Some(v) => data.birth
                  case None => account.birth
                },
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
                },
                data.premium match {
                  case Some(value) => data.premium
                  case None => account.premium
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

  def getInterests(id: Int): List[Int] = {
    var list = List[Int]()
    val sql = "SELECT * FROM Interests where acc = "
    val statmt = conn.createStatement()
    val rs = statmt.executeQuery(sql + id)
    while (rs.next()) {
      list = list :+ rs.getInt("interests")
    }
    statmt.close()
    list
  }

 def getLikees(id: Int): List[Int] = {
    var list = List[Int]()
    val sql = "SELECT * FROM Likes where liker = "
    val statmt = conn.createStatement()
    val rs = statmt.executeQuery(sql + id)
    while (rs.next()) {
      list = list :+ rs.getInt("likee")
    }
    statmt.close()
    list
  }

  override def getAccount(id: Int)(implicit mc: MarkerContext): Future[Option[Account]] = {
    Future {
      val statmt = conn.createStatement()
      val rs = statmt.executeQuery(sqlAccount + id)
      val account = if (rs.next()) {
        Some(Account(rs.getInt("id"),
          Option(rs.getInt("joined")),
          Option(rs.getInt("status")).map(statuses(_)),
          rs.getString("email"),
          Option(rs.getString("fname")),
          Option(rs.getString("sname")),
          Option(rs.getString("phone")),
          Option(rs.getString("sex")),
          Option(rs.getInt("birth")),
          Option(rs.getString("country")),
          Option(rs.getString("city")),
          Option(null),
          Option(null),
          getPremium(rs)
        ))
      } else {
        None
      }
      statmt.close()
      account
    }
  }


  private def getPremium(rs: ResultSet): Option[Premium] = {
    val start = rs.getInt("start")
    val finish = rs.getInt("finish")
    if (start > 0 || finish > 0) Option(Premium(start, finish)) else None
  }

  private def getAccounts(columns: Set[String], sql: String)(implicit mc: MarkerContext): Option[List[Account]] = {
    println("SQL: " + sql)
    val statmt = conn.createStatement()
    val rs = statmt.executeQuery(sql)
    var list = List[Account]()

    while (rs.next()) {
      val id = rs.getInt("id")
      list = list :+ Account(id,
        if (columns.contains("joined")) Option(rs.getInt("joined")) else None,
        if (columns.contains("status")) Option(rs.getInt("status")).map(statuses(_)) else None,
        rs.getString("email"),
        if (columns.contains("fname")) Option(rs.getString("fname")) else None,
        if (columns.contains("sname")) Option(rs.getString("sname")) else None,
        if (columns.contains("phone")) Option(rs.getString("phone")) else None,
        if (columns.contains("sex")) Option(rs.getString("sex")) else None,
        if (columns.contains("birth")) Option(rs.getInt("birth")) else None,
        if (columns.contains("country")) Option(rs.getString("country")) else None,
        if (columns.contains("city")) Option(rs.getString("city")) else None,
        Option(null),
        Option(null),
        if (columns.exists(_ contains "start")) getPremium(rs) else None
      )
    }
    statmt.close()
    if (list.isEmpty) {
      None
    } else {
      Some(list)
    }
  }

  override def recommend(id: Int, list: List[String], limit: Option[Int])(implicit mc: MarkerContext): Future[List[Account]] = {
    getAccount(id).map {
      case None => List()
      case Some(a) =>
        val interests = getInterests(a.id)
        if (interests.isEmpty) {
          List()
        } else {
          val sex = a.sex.get match {
            case "f" => "m"
            case "m" => "f"
          }
          val sql = "select id, status, email, fname, sname, birth, start, finish  from Accounts a inner join Interests i on a.id = i.acc where sex = '" + sex + "' " + (if (list.nonEmpty) " AND " + list.mkString(" AND ") else "") + " and interests in (" + interests.mkString(", ") + ") group by id, status, email, fname, sname, birth, start, finish having count(1) > 0 order by status, count(1) desc, ABS(birth - " + a.birth.getOrElse(0) + ")  "
          println("SQL: " + sql)
          getAccounts(Set("id", "email", "status", "fname", "sname", "birth", "start", "finish"), sql) match {
            case None => List()
            case Some(l) =>
              (l.filter(activePremium) ::: l.filter(!activePremium(_))).take(limit.getOrElse(20))
          }
        }
    }
  }

 override def suggest(id: Int, list: List[String], limit: Option[Int])(implicit mc: MarkerContext): Future[List[Account]] = {
    getAccount(id).map {
      case None => List()
      case Some(a) =>
        val likees = getLikees(a.id)
        if (likees.isEmpty) {
          List()
        } else {
          val sex = a.sex.get
          //val sql = "select id, status, email, fname, sname, birth, start, finish from Accounts a inner join (\nselect id, SUM(1/ABS(AVG(l.ts) - my.ts_avg)) as ts_diff  from Accounts a inner join Likes l on a.id = l.liker \ninner join (select liker, likee, AVG(ts) as ts_avg from Likes where liker="+id+" group by liker, likee) my on l.likee = my.likee \nwhere sex = '"+sex+"' GROUP BY id ) ts_select on a.id = ts_select.id ORDER BY ts_diff desc"
          //val sql = "select a2.id, status, email, fname, sname, birth, start, finish from Accounts a2 inner join (\nselect id from Accounts a inner join Likes l on a.id = l.liker \ninner join (select liker, likee, AVG(ts) as ts_avg from Likes where liker="+id+" group by liker, likee) my on l.likee = my.likee \nwhere sex = '"+sex+"' GROUP BY id ) ts_select on a2.id = ts_select.id "
          val sql = """select a3.id, status, email, fname, sname, birth, start, finish from Accounts a3 inner join
             (select st1.id, st2.likee, ord, max(st2.ts) as max_ts from (select a2.id, SUM(1/(case ts_diff when 0 then 1 else ts_diff end)) as ord from Accounts a2 inner join (
select id, l.likee, ABS(AVG(l.ts) - my.ts_avg) as ts_diff from Accounts a inner join Likes l on a.id = l.liker
inner join (select liker, likee, AVG(ts) as ts_avg from Likes where liker="""+id+""" group by liker, likee) my on l.likee = my.likee
where id!="""+id+""" AND sex = '"""+sex+"""' """ + (if (list.nonEmpty) " AND " + list.mkString(" AND ") else "") + """ GROUP BY id, l.likee ) ts_select on a2.id = ts_select.id
inner join Likes l2 on a2.id = l2.liker
GROUP BY a2.id) st1 inner join Likes st2 on st1.id=st2.liker group by st1.id, st2.likee, ord) t_similars on a3.id = t_similars.likee where t_similars.likee not in("""+likees.mkString(",")+""") ORDER BY t_similars.ord desc, a3.id desc"""
          println("SQL: " + sql)
          getAccounts(Set("id", "email", "status", "fname", "sname", "birth", "start", "finish"), sql) match {
            case None => List()
            case Some(l) =>l.take(limit.getOrElse(20))
          }
        }
    }
  }

  private def activePremium(a: Account): Boolean = a.premium.isDefined && a.premium.get.start < getNow && a.premium.get.finish >= getNow

  val full_columns = "id, joined, status, email, fname, sname, phone, sex, birth, country, city, start, finish"

  def sqlAccountWhere(columns: String) = "SELECT " + columns + " from Accounts "

  def sqlLikesWhere(columns: String) = "SELECT " + columns + " from Accounts a inner join Likes l on a.id = l.liker "

  def sqlInterestsWhere(columns: String) = "SELECT " + columns + " from Accounts a inner join Interests i on a.id = i.acc "

  def sqlInterestsLikesWhere(columns: String) = "SELECT " + columns + " from Accounts a inner join Interests i on a.id = i.acc inner join Likes l on a.id = l.liker "

  val sqlAccount = sqlAccountWhere(full_columns) + " WHERE id="
  val sqlAccounts = sqlAccountWhere(full_columns) + " WHERE id in "


  override def filter(keys: Iterable[String], list: Iterable[String], limit: Option[Int])(implicit mc: MarkerContext): Future[List[Account]] = {
    Future {
      val columns = Set("id", "email") ++ keys.toSet
      val fields = columns.mkString(", ")
      val table = if (list.exists(s => s.contains("likee in (")) && list.exists(s => s.contains("interests in "))) sqlInterestsLikesWhere(fields)
      else if (list.exists(s => s.contains("likee in ("))) sqlLikesWhere(fields)
      else if (list.exists(s => s.contains("interests in "))) sqlInterestsWhere(fields)
      else sqlAccountWhere(fields)
      //val columns = "id, email, fname, sname, phone, sex, birth, country, city, start, finish"

      getAccounts(columns, table + (if (list.nonEmpty) " WHERE " + list.mkString(" AND ") else "") +
        (if (table == sqlLikesWhere(fields)) " GROUP BY " + fields + " HAVING count(1) = " + list.filter(s => s.contains("likee in (")).head.split(",").length else "") +
        /* all */
        (if (table == sqlInterestsWhere(fields) && list.exists(s => s.contains("interests in ("))) " GROUP BY " + fields + " HAVING count(1) = " + list.filter(s => s.contains("interests in (")).head.split(",").length else "") +
        (if (table == sqlInterestsLikesWhere(fields) && list.exists(s => s.contains("interests in ("))) " GROUP BY " + fields + " HAVING count(1) = " + (list.filter(s => s.contains("interests in (")).head.split(",").length * list.filter(s => s.contains("likee in (")).head.split(",").length) else "") +
        /* any */
        (if ((table == sqlInterestsWhere(fields)) && list.exists(s => s.contains("interests in  ("))) " GROUP BY " + fields + " HAVING count(1) > 0 " else "") +
        (if ((table == sqlInterestsLikesWhere(fields)) && list.exists(s => s.contains("interests in  ("))) " GROUP BY " + fields + " HAVING count(1) >= " + list.filter(s => s.contains("likee in (")).head.split(",").length else "") +
        " ORDER BY id desc " +
        (limit match {
          case Some(i) => " LIMIT " + i
          case None => ""
        })
      ) match {
        case Some(list) => list
        case None => List()
      }
    }
  }

  override def group(keys: Iterable[String], list: Iterable[String], limit: Option[Int], order: Boolean)(implicit mc: MarkerContext): Future[List[Group]] = {
    Future {
      val sql = "SELECT " + keys.mkString(",") + ", count(1) as c FROM Accounts a " +
        (if (list.exists(_ contains "interests =") || keys.exists(_ == "interests")) " INNER JOIN Interests i on a.id = i.acc " else "") +
        (if (list.exists(_ contains "likee =")) " INNER JOIN Likes l on a.id = l.liker " else "") +
        (if (list.nonEmpty) " WHERE " + list.mkString(" AND ") else "") +
        " GROUP BY " + keys.mkString(",") +
        (if (order) " ORDER BY c desc," + keys.mkString(" desc, ") + " desc " else " ORDER BY c," + keys.mkString(",")) +
      (limit match {
        case Some(i) => " LIMIT " + i
        case None => ""
      })
      getGroups(sql, keys) match {
        case Some(list) => list
        case None => List()
      }
    }
  }

  private def get(rs: ResultSet, name: String, key: Iterable[String]): Option[String] = {
    Option(if (key.exists(_ == name)) rs.getString(name) else null)
  }

  private def getGroups(sql: String, key: Iterable[String])(implicit mc: MarkerContext): Option[List[Group]] = {
    println("SQL: " + sql)
    val statmt = conn.createStatement()
    val rs = statmt.executeQuery(sql)
    var list = List[Group]()

    while (rs.next()) {
      list = list :+ Group(
        get(rs, "sex", key),
        get(rs, "status", key).map(x => statuses(x.toInt)),
        get(rs, "interests", key).map(x => interests(x.toInt)),
        get(rs, "country", key),
        get(rs, "city", key),
        rs.getInt("c")
      )
    }
    statmt.close()
    if (list.isEmpty) {
      None
    } else {
      Some(list)
    }
  }

}
