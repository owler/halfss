package v1.post

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

/**
  * Takes HTTP requests and produces JSON.
  */
class AccountController @Inject()(cc: PostControllerComponents)(implicit ec: ExecutionContext)
  extends PostBaseController(cc) {


  private val logger = Logger(getClass)

  def filter: Action[AnyContent] = PostAction.async { implicit request =>
    val start = System.currentTimeMillis()
    if (!verifyIntParameter("birth", request) ||
      !verifyGenderParameter("sex_eq", request)) {
      Future {
        BadRequest
      }
    } else {

      postResourceHandler.lookupLocationAvg(id).map {
        case None => NotFound
        case Some(visits1) => {
          var visits = request.getQueryString("fromDate") match {
            case None => visits1
            case Some(str) => visits1.filter(_.visit.visited_at > str.toInt)
          }
          visits = request.getQueryString("toDate") match {
            case None => visits
            case Some(str) => visits.filter(_.visit.visited_at < str.toInt)
          }
          visits = request.getQueryString("gender") match {
            case None => visits
            case Some(str) => visits.filter(_.user.gender == str)
          }
          visits = request.getQueryString("fromAge") match {
            case None => visits
            case Some(str) => visits.filter(_.user.birth_date < postResourceHandler.relativeTimeStamp(str.toInt))
          }
          visits = request.getQueryString("toAge") match {
            case None => visits
            case Some(str) => visits.filter(_.user.birth_date > postResourceHandler.relativeTimeStamp(str.toInt))
          }
          Ok(Json.obj("avg" -> Math.round(100000 * (visits.map(v => v.visit.mark).sum.toDouble / visits.size)).toDouble / 100000))
        }
      }
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
      postResourceHandler.lookupUser(id).map {
        case None => NotFound
        case Some(user) => Ok(Json.toJson(user))
      }
    } finally {
      val delay = System.currentTimeMillis() - start
      if (delay > 100) println("user " + id + " takes ms: " + delay)
    }
  }


  def recommend(id: Int): Action[AnyContent] = PostAction.async { implicit request =>
    val start = System.currentTimeMillis()
    try {
      if (!verifyParameter("fromDate", request) ||
        !verifyParameter("toDate", request) ||
        !verifyParameter("toDistance", request) ||
        !verifyStringParameter("country", request)) {
        Future {
          BadRequest
        }
      } else {
        postResourceHandler.lookupVisitsByUserId(id).map {
          case None => NotFound
          case Some(visits1) => {
            var visits = request.getQueryString("fromDate") match {
              case None => visits1
              case Some(str) => visits1.filter(_.visit.visited_at > str.toInt)
            }
            visits = request.getQueryString("toDate") match {
              case None => visits
              case Some(str) => visits.filter(_.visit.visited_at < str.toInt)
            }
            visits = request.getQueryString("toDistance") match {
              case None => visits
              case Some(str) => visits.filter(_.location.distance < str.toInt)
            }
            visits = request.getQueryString("country") match {
              case None => visits
              case Some(str) => visits.filter(_.location.country == str)
            }
            Ok(Json.obj("visits" -> visits.toList.sortBy(_.visit.visited_at)))
          }
        }
      }
    } finally {
      val delay = System.currentTimeMillis() - start
      if (delay > 100) println("visits for user " + id + " takes ms: " + delay)
    }
  }


  def group: Handler = ???

}
