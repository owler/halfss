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
      case Eq(name) if name == "status" => (name, name + "=" + cc.postRepository.getStatuses().indexOf(l._2.head) + "")
      case Eq(name) => (name, name + "='" + l._2.head + "'")
      case Contains(name) if name == "likes" => ("likee", "likee in (" + l._2.head.split(",").toList.sorted.mkString(",") + ")")
      case Contains(name) if name == "interests" => (name, name + " in (" + cc.postRepository.wrapInterests(l._2.head.split(",").toList).sorted.mkString(",") + ")")
      case Any(name) if name == "interests" => (name, name + " in  (" + cc.postRepository.wrapInterests(l._2.head.split(",").toList).sorted.mkString(",") + ")")
      case Neq(name) if name == "status" => (name, name + "!=" + cc.postRepository.getStatuses().indexOf(l._2.head) + "")
      case Neq(name) => (name, name + "!='" + l._2.head + "'")
      case Starts(name) => (name, name + " like '" + l._2.head + "%'")
      case Code(name) => (name, name + " like '%(" + l._2.head + ")%'")
      case Year(name) => val y = y_from_to(l._2.head.toInt); (name, name + " >= " + y._1 + " AND " + name + " < " + y._2)
      case Null(name) if name=="premium" => ("start, finish", "start" + (l._2.head match {
        case "0" => " is not null"
        case "1" => " is null"
      }))
      case Null(name) => (name, name + (l._2.head match {
        case "0" => " is not null"
        case "1" => " is null"
      }))
      case Now(name) => l._2.head match {
        case "1" => ("start, finish", "start <= " + cc.postRepository.getNow + " AND finish >= " + cc.postRepository.getNow)
        case _ => null
      }
      case Lt(name) if name == "birth" => (name, name + "<" + l._2.head)
      case Lt(name) => (name, name + "<'" + l._2.head + "'")
      case Gt(name) if name == "birth" => (name, name + ">" + l._2.head)
      case Gt(name) => (name, name + ">'" + l._2.head + "'")
      case Domain(name) => (name, name + " like '%" + l._2.head + "'")
      case Any(name) => (name, name + " in (" + l._2.head.split(",").map(s => "'" + s + "'").mkString(",") + ")")
      case _ => ("", null)
    })
    val params = list.values
    if (params.exists(_ == null)) {
      Future {
        BadRequest
      }
    } else {
      postResourceHandler.filter(list.keys, params, limit).map(
        l => Ok(Json.obj("accounts" -> l))
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

  def likes: Action[AnyContent] = PostAction.async { implicit request =>
    request.body.asJson match {
      case None => Future {
        BadRequest
      }
      case Some(json) => postResourceHandler.updateLikes(json)
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
    val limit = request.getQueryString("limit").map(_.toInt)
    val params = request.queryString.filterNot(x => x._1 == "query_id" || x._1 == "limit").map(l => l._1 match {
      case name if (name == "country" || name == "city") && l._2.head.length>0 => name + "='" + l._2.head + "'"
      case _ => null
    })

    if (params.exists(_ == null)) {
      Future {
        BadRequest
      }
    } else {
      postResourceHandler.suggest(id, params.toList, limit).map {
        case None => NotFound
        case Some(v) => Ok(Json.obj("accounts" -> v))
      }
    }
  }

  def recommend(id: Int): Action[AnyContent] = PostAction.async { implicit request =>
    val limit = request.getQueryString("limit").map(_.toInt)
    val params = request.queryString.filterNot(x => x._1 == "query_id" || x._1 == "limit").map(l => l._1 match {
      case name if (name == "country" || name == "city") && l._2.head.length>0 => name + "='" + l._2.head + "'"
      case _ => null
    })

    if (params.exists(_ == null)) {
      Future {
        BadRequest
      }
    } else {
      postResourceHandler.recommend(id, params.toList, limit).map {
        case None => NotFound
        case Some(v) => Ok(Json.obj("accounts" -> v))
      }
    }

  }

  import scala.util.{Try, Success, Failure}
  val validKeys= List("sex", "status", "interests", "country", "city")
  def group: Action[AnyContent] = PostAction.async { implicit request =>
    val limit = Try(request.getQueryString("limit").map(_.toInt))
    val orderS = Try(request.getQueryString("order").map(_.toInt))
    if(orderS.isFailure || limit.isFailure || (orderS.get.get != -1 && orderS.get.get != 1)) {
      Future {
        BadRequest
      }
    } else {

    val order = orderS.get.map(_ == -1)
    request.getQueryString("keys").map(_.split(",")) match {
      case Some(keys) => {
        if(keys.forall(s => validKeys.contains(s))) {
        val list = request.queryString.filterNot(x => x._1 == "query_id" || x._1 == "limit" ||
          x._1 == "keys" || x._1 == "order").map(l => l._1 match {
          case "likes" => "likee = " + l._2.head.toInt
          case "interests" => "interests = " + cc.postRepository.wrapInterests(List(l._2.head)).head
          case "birth" => val y = y_from_to(l._2.head.toInt); "birth >= " + y._1 + " AND birth < " + y._2
          case "joined" => val y = y_from_to(l._2.head.toInt); "joined >= " + y._1 + " AND joined < " + y._2
          case name => name + "='" + l._2.head + "'"
        })
        postResourceHandler.group(keys, list, limit.get, order.getOrElse(true)).map(
          l => Ok(Json.obj("groups" -> l))
        )} else {
          Future {
            BadRequest
          }
        }
      }
      case None => Future {
        BadRequest
      }
    }}
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
