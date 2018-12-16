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

  private val logger = Logger(this.getClass)
  val rootzip = new java.util.zip.ZipFile("/tmp/data/data1.zip")
  val now = Source.fromFile("/tmp/data/options.txt").getLines.toList.head.toLong * 1000
  println("data.zip timestamp " + new Date(now))
  import scala.collection.JavaConverters._

  //val dbUrl = "jdbc:sqlite:/tmp/data/travel.db"
  val dbUrl = "jdbc:sqlite::memory:"
  private implicit var conn = DriverManager.getConnection(dbUrl)
  private val start = System.currentTimeMillis()
  createDB()
  loadDb()
  createIndex()
  private val end = System.currentTimeMillis()
  println("All data loaded in " + (end - start) / 1000 + "s. Accounts: " + getCount("Accounts")
    + " Visits: " + getCount("visits") + " Locations: " + getCount("locations"))
  System.gc()

  import java.sql.SQLException

  @throws[ClassNotFoundException]
  @throws[SQLException]
  def createDB(): Unit = {
    val statmt = conn.createStatement()
    statmt.execute("CREATE TABLE if not exists Accounts (id INTEGER PRIMARY KEY, email text, first_name text, last_name text, gender text, birth_date integer);")
    statmt.execute("CREATE TABLE if not exists visits (id INTEGER PRIMARY KEY, location integer, Account integer, visited_at integer, mark integer);")
    statmt.execute("CREATE TABLE if not exists locations (id INTEGER PRIMARY KEY, place text, country text, city text, distance integer);")
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
          case _ if e.getName.contains("Accounts") =>
            val Accounts: Reads[Seq[Account]] = (JsPath \ "Accounts").read[Seq[Account]]
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
    val u2e_Idx = "CREATE UNIQUE INDEX idx_Account_email ON Accounts (email);"
    val v2u_Idx = "CREATE INDEX idx_visit_Account ON visits (Account);"
    val v2l_Idx = "CREATE INDEX idx_visit_location ON visits (location);"
    val statmt = conn.createStatement()
    statmt.execute(u2e_Idx)
    System.out.println("Индекс u2e_Idx создан.")
    statmt.execute(v2u_Idx)
    System.out.println("Индекс v2u_Idx создан.")
    statmt.execute(v2l_Idx)
    System.out.println("Индекс v2l_Idx создан.")
    statmt.close()
  }

  def writeAccounts(Accounts: Iterable[Account])(implicit conn: Connection): Unit = {
    val statmt = conn.createStatement()
    val sb = new StringBuffer("INSERT INTO Accounts (id, email, first_name, last_name, gender, birth_date) VALUES ")
      .append(
        Accounts.map(Account =>
          new StringBuffer("(").append(Account.id).append(",'")
            .append(Account.email).append("','")
            .append(Account.first_name).append("','")
            .append(Account.last_name).append("','")
            .append(Account.gender).append("',").append(Account.birth_date).append(")").toString).mkString(",")
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

  def writeVisits(visits: Iterable[Visit])(implicit conn: Connection): Unit = {
    val statmt = conn.createStatement()
    val sb = new StringBuffer("INSERT INTO visits (id, location, Account, visited_at, mark)  VALUES ")
      .append(
        visits.map(v => new StringBuffer("(").append(v.id).append(",")
          .append(v.location).append(",")
          .append(v.Account).append(",")
          .append(v.visited_at).append(",")
          .append(v.mark).append(")").toString).mkString(",")
      )
      .append(";")
    statmt.execute(sb.toString)
    statmt.close()
  }

  def writeLocations(locations: Iterable[Location])(implicit conn: Connection): Unit = {
    val statmt = conn.createStatement()
    val sb = new StringBuffer("INSERT INTO locations (id, place, country, city, distance) VALUES ")
      .append(
        locations.map(l => new StringBuffer().append("(").append(l.id).append(",'")
          .append(l.place).append("','")
          .append(l.country).append("','")
          .append(l.city).append("',")
          .append(l.distance).append(")").toString).mkString(",")
      )
      .append(";")
    statmt.execute(sb.toString)
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
          case Some(Account) => Results.BadRequest
        }
      }
    }
  }

  private def isEmailExists(email: String): Boolean = {
    getAccounts("SELECT id, email, first_name, last_name, gender, birth_date from Accounts WHERE email='" + email + "'") match {
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
            val Account = map.values.head
            if (data.email.isDefined && Account.email != data.email.get && isEmailExists(data.email.get)) {
              Results.BadRequest
            } else {
              val updatedAccount = Account(Account.id, data.email.getOrElse(Account.email),
                data.first_name.getOrElse(Account.first_name), data.last_name.getOrElse(Account.last_name),
                data.gender.getOrElse(Account.gender), data.birth_date.getOrElse(Account.birth_date))
              deleteObj(updatedAccount.id, "Accounts")
              writeAccounts(List(updatedAccount))
              Results.Ok(Json.obj())
            }
        }
      }
    }
  }

  override def createLocation(data: Location)(implicit mc: MarkerContext): Future[Result] = {
    Future {
      this.synchronized {
        getLocations(sqlLocation + data.id) match {
          case None =>
            writeLocations(List(data))
            Results.Ok(Json.obj())
          case Some(map) => Results.NotFound
        }
      }
    }
  }


  override def updateLocation(id: Int, data: LocationPost)(implicit mc: MarkerContext): Future[Result] = {
    Future {
      this.synchronized {
        getLocations(sqlLocation + id) match {
          case None => Results.NotFound
          case Some(map) =>
            val location = map.values.head
            val updatedLocation = Location(id, data.place.getOrElse(location.place),
              data.country.getOrElse(location.country), data.city.getOrElse(location.city),
              data.distance.getOrElse(location.distance))
            deleteObj(location.id, "locations")
            writeLocations(List(updatedLocation))
            Results.Ok(Json.obj())
        }
      }
    }
  }

  override def createVisit(data: Visit)(implicit mc: MarkerContext): Future[Result] = {
    Future {
      this.synchronized {
        getVisits(sqlVisit + data.id) match {
          case None =>
            writeVisits(List(data))
            Results.Ok(Json.obj())
          case Some(visit) => Results.NotFound
        }
      }
    }
  }


  override def updateVisit(id: Int, data: VisitPost)(implicit mc: MarkerContext): Future[Result] = {
    Future {
      this.synchronized {
        getVisits(sqlVisit + id) match {
          case None => Results.NotFound
          case Some(list) =>
            val visit = list.head
            val updatedVisit = Visit(id, data.location.getOrElse(visit.location),
              data.Account.getOrElse(visit.Account), data.visited_at.getOrElse(visit.visited_at), data.mark.getOrElse(visit.mark))
            deleteObj(id, "visits")
            writeVisits(List(updatedVisit))
            Results.Ok(Json.obj())
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
      val Account = if (rs.next()) {
        Some(Account(rs.getInt("id"),
          rs.getString("email"),
          rs.getString("first_name"),
          rs.getString("last_name"),
          rs.getString("gender"),
          rs.getInt("birth_date")))
      } else {
        None
      }
      statmt.close()
      Account
    }
  }

  val sqlVisit = "SELECT id, location, Account, visited_at, mark from visits WHERE id="

  override def getVisit(id: Int)(implicit mc: MarkerContext): Future[Option[Visit]] = {
    Future {
      val statmt = conn.createStatement()
      val rs = statmt.executeQuery(sqlVisit + id)
      val visit = if (rs.next()) {
        Some(Visit(rs.getInt("id"),
          rs.getInt("location"),
          rs.getInt("Account"),
          rs.getInt("visited_at"),
          rs.getInt("mark")))
      } else {
        None
      }
      statmt.close()
      visit
    }
  }

  private def getVisits(sql: String)(implicit mc: MarkerContext): Option[Iterable[Visit]] = {
    val statmt = conn.createStatement()
    val rs = statmt.executeQuery(sql)
    var list = ListBuffer[Visit]()

    while (rs.next()) {
      list += Visit(rs.getInt("id"),
        rs.getInt("location"),
        rs.getInt("Account"),
        rs.getInt("visited_at"),
        rs.getInt("mark"))
    }
    statmt.close()
    if (list.isEmpty) {
      None
    } else {
      Some(list.toList)
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
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("gender"),
        rs.getInt("birth_date"))
    }
    statmt.close()
    if (map.isEmpty) {
      None
    } else {
      Some(map)
    }
  }

  private def getLocations(sql: String)(implicit mc: MarkerContext): Option[Map[Int, Location]] = {
    val statmt = conn.createStatement()
    val rs = statmt.executeQuery(sql)
    var map = Map[Int, Location]()

    while (rs.next()) {
      val id = rs.getInt("id")
      map += id -> Location(id,
        rs.getString("place"),
        rs.getString("country"),
        rs.getString("city"),
        rs.getInt("distance"))
    }
    statmt.close()
    if (map.isEmpty) {
      None
    } else {
      Some(map)
    }
  }

  val sqlLocation = "SELECT id, place, country, city, distance from locations WHERE id="

  override def getLocation(id: Int)(implicit mc: MarkerContext): Future[Option[Location]] = {
    Future {
      val statmt = conn.createStatement()
      val rs = statmt.executeQuery(sqlLocation + id)
      val location = if (rs.next()) {
        Some(Location(rs.getInt("id"),
          rs.getString("place"),
          rs.getString("country"),
          rs.getString("city"),
          rs.getInt("distance")))
      } else {
        None
      }
      statmt.close()
      location
    }
  }

  val sqlVisitByLocation = "SELECT id, location, Account, visited_at, mark from visits WHERE location="

  override def getLocationAvg(id: Int)(implicit mc: MarkerContext): Future[Option[Iterable[Visit2Account]]] = {
    Future {
      if (!isExist("locations", id)) {
        None
      } else {
        val visits = getVisits(sqlVisitByLocation + id)
        visits.map(x =>
          x.map(v => v.Account).mkString(",")) match {
          case None => Some(List.empty)
          case Some(AccountIds) =>
            val Accounts = getAccounts("SELECT id, email, first_name, last_name, gender, birth_date from Accounts WHERE id IN (" + AccountIds + ")")
            visits.map(x =>
              x.map(v => Visit2Account(v, Accounts.getOrElse(Map.empty[Int, Account]).getOrElse(v.Account, null))).filter(_.Account != null)

            )
        }
      }
    }
  }

  val sqlAccount = "SELECT id, email, first_name, last_name, gender, birth_date from Accounts WHERE id="
  val sqlVisitsByAccount = "SELECT id, location, Account, visited_at, mark from visits WHERE Account="

  override def lookupVisitsByAccountId(id: Int)(implicit mc: MarkerContext): Future[Option[Iterable[VisitResp]]] = {
    Future {
      if (!isExist("Accounts", id)) {
        None
      } else {
        val visits = getVisits(sqlVisitsByAccount + id)
        visits.map(x =>
          x.map(v => v.location).mkString(",")) match {
          case None => Some(List.empty)
          case Some(locationIds) =>
            val locations = getLocations("SELECT id, place, country, city, distance from locations WHERE id IN (" + locationIds + ")")
            visits.map(x =>
              x.map(v => VisitResp(v, locations.getOrElse(Map.empty[Int, Location]).getOrElse(v.location, null))).filter(_.location != null)
            )
        }
      }
    }
  }
}
