package v1.post

import java.util.{Calendar, GregorianCalendar, TimeZone}

import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

case class TravelFormInput(body: String)

trait Mstr {
  def prefix: String
  def allowed: List[String]

  def validate(in: String): Option[String] = {
    if (allowed contains in) Some(in)
    else None
  }

  def unapply(str: String): Option[String] = {
    str match {
      case s if s.endsWith(prefix) => validate(s.stripSuffix(prefix))
      case _ => None
    }
  }
}

object Eq extends Mstr {
  val allowed = List("sex", "status", "fname", "sname", "country", "city")
  override def prefix: String = "_eq"
}

object Gt extends Mstr {
  override def prefix: String = "_gt"
  override def allowed = List("email", "birth")
}

object Lt extends Mstr {
  override def prefix: String = "_lt"
  override def allowed = List("email", "birth")
}

object Domain extends Mstr {
  override def prefix: String = "_domain"
  override def allowed: List[String] = List("email")
}

object Any extends Mstr {
  override def prefix: String = "_any"
  override def allowed: List[String] = List("fname", "city", "interests")
}

object Neq extends Mstr {
  override def prefix: String = "_neq"
  override def allowed: List[String] = List("status")
}

object Null extends Mstr {
  override def prefix: String = "_null"
  override def allowed: List[String] = List("fname", "sname", "phone", "country", "city", "premium")
}

object Starts extends Mstr {
  override def prefix: String = "_starts"
  override def allowed: List[String] = List("sname")
}

object Code extends Mstr {
  override def prefix: String = "_code"
  override def allowed: List[String] = List("phone")
}

object Year extends Mstr {
  override def prefix: String = "_year"
  override def allowed: List[String] = List("birth")
}

object Contains extends Mstr {
  override def prefix: String = "_contains"
  override def allowed: List[String] = List("interests", "likes")
}

object Now extends Mstr {
  override def prefix: String = "_now"
  override def allowed: List[String] = List("premium")
}
/**
  * Takes HTTP requests and produces JSON.
  */
class AccountController @Inject()(cc: PostControllerComponents)(implicit ec: ExecutionContext)
  extends PostBaseController(cc) {


  private val logger = Logger(getClass)

  def filter: Action[AnyContent] = PostAction.async { implicit request =>
    val limit = request.getQueryString("limit").map(_.toInt)
    val list = request.queryString.filterNot(x => x._1 == "query_id" || x._1 == "limit").map(l => l._1 match {
        case Eq(name) => name + "='" + l._2.head + "'"
        case Contains(name) if name=="likes" => "likee in (" + l._2.head.split(",").toList.sorted.mkString(",") + ")"
        case Contains(name) => name + "='" + cc.postRepository.wrapInterests(l._2.head.split(",").toList).sorted.mkString(",") + "'"
        case Any(name) if name == "interests" => cc.postRepository.wrapInterests(l._2.head.split(",").toList).sorted.map(e => name + " like '%," + e + ",%' OR " + name + " like '" + e + ",%' OR " + name + " like '%," + e + "'").mkString(" OR ")
        case Neq(name) => name + "!='" + l._2.head + "'"
        case Starts(name) => name + " like '" + l._2.head + "%'"
        case Code(name) => name + " like '%(" + l._2.head + ")%'"
        case Year(name) => val y = y_from_to(l._2.head.toInt); name + " >= " + y._1 + " AND " + name + " < " + y._2
        case Null(name) => name + (l._2.head match {
          case "0" => " is not null"
          case "1" => " is null"
        })
        case Lt(name) if name == "birth" => name + "<" + l._2.head
        case Lt(name) => name + "<'" + l._2.head + "'"
        case Gt(name) if name == "birth" => name + ">" + l._2.head
        case Gt(name) => name + ">'" + l._2.head + "'"
        case Domain(name) => name + " like '%" + l._2.head + "'"
        case Any(name) => name + " in (" + l._2.head.split(",").map(s => "'" + s + "'").mkString(",") + ")"
        case _ => null
    })
    if (list.exists(_ == null)) {
      Future{
        BadRequest
      }
    } else {

      postResourceHandler.filter(list, limit).map(
        l => Ok(Json.toJson(l))
      )
    }
  }

  def create: Action[AnyContent] = PostAction.async { implicit request =>
    request.body.asJson match {
      case None => Future {
        BadRequest
      }
      case Some(json) => postResourceHandler.createAccount(json)
    }
  }

  def update(id: Int): Action[AnyContent] = PostAction.async { implicit request =>
    request.body.asJson match {
      case None => Future {
        BadRequest
      }
      case Some(json) => postResourceHandler.updateAccount(id, json)
    }
  }


  def suggest(id: Int): Action[AnyContent] = PostAction.async { implicit request =>
    val start = System.currentTimeMillis()
    try {
      postResourceHandler.lookupAccount(id).map {
        case None => NotFound
        case Some(user) => Ok(Json.toJson(user))
      }
    } finally {
      val delay = System.currentTimeMillis() - start
      if (delay > 100) println("user " + id + " takes ms: " + delay)
    }
  }


  def recommend(id: Int): Action[AnyContent] = PostAction.async { implicit request =>
    postResourceHandler.lookupAccount(id).map {
      case None => NotFound
      case Some(account) => Ok(Json.toJson(account))
    }
  }


  def group: Action[AnyContent] = PostAction.async { implicit request =>
    val limit = request.getQueryString("limit").map(_.toInt)
    val order = request.getQueryString("order").map(_.toInt).map(_ == -1)
    val keys = request.getQueryString("keys").map(_.split(",")).get
    val list = request.queryString.filterNot(x => x._1 == "query_id" || x._1 == "limit" ||
      x._1 == "keys" || x._1 == "order").map(l => l._1 match {
      case "likes" => "likee = " + l._2.head.toInt
      case "interests" => "interests = " + cc.postRepository.wrapInterests(List(l._2.head)).head
      case "birth" => val y = y_from_to(l._2.head.toInt); "birth >= " + y._1 + " AND birth < " + y._2
      case name => name + "='" + l._2.head + "'"
    })
    postResourceHandler.group(keys, list, limit, order.getOrElse(true)).map(
      l => Ok(Json.obj("groups" -> l))
    )
  }

  def y_from_to(year: Int) = {
    val c = new GregorianCalendar(TimeZone.getTimeZone("UTC"))
    c.clear()
    c.set(Calendar.YEAR, year)
    val yFrom = c.getTimeInMillis / 1000
    c.add(Calendar.YEAR, 1)
    val yTo = c.getTimeInMillis / 1000
    (yFrom, yTo)
  }
}
