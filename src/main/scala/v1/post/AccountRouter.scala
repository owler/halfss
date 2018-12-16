package v1.post

import javax.inject.Inject
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

/**
  * Routes and URLs to the UsersResource controller.
  */
class AccountRouter @Inject()(controller: AccountController) extends SimpleRouter {
  val prefix = "/accounts"

  def link(id: String): String = {
    import com.netaporter.uri.dsl._
    val url = prefix / id
    url.toString()
  }


  override def routes: Routes = {
    case GET(p"/filter") =>
      controller.filter

    case GET(p"/group") =>
      controller.group

    case GET(p"/${int(id)}/recommend") =>
      controller.recommend(id)

    case GET(p"/${int(id)}/suggest") =>
      controller.suggest(id)

    case POST(p"/${int(id)}") =>
      controller.update(id)

    case POST(p"/new") =>
      controller.create

  }

}
