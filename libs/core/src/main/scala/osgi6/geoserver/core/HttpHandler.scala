package osgi6.geoserver.core

import java.util
import javax.naming.InitialContext
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper, HttpServletResponse}
import javax.servlet.{ServletConfig, ServletContext}

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.geotools.data.DataStore
import org.osgi.framework.BundleContext
import org.springframework.web.servlet.DispatcherServlet
import osgi6.actor.ActorSystemActivator
import osgi6.multi.api.MultiApi
import sbt.io.IO


/**
  * Created by pappmar on 17/12/2015.
  */
object HttpHandler {

  def activate(
    bundleContext: BundleContext,
    classLoader: Option[ClassLoader] = None,
    config: Config = ConfigFactory.empty()
  ) = {

    ActorSystemActivator.activate(
      bundleContext,
      ???,
      classLoader = classLoader,
      config = config
    )

    val workDir = IO.createTemporaryDirectory

    val servlet = {
      val dataStoreMap : Map[String, DataStore] = Map(
      )

      val wac = GeoserverStarter.setupGeoserver(workDir,
        dataStoreMap
      )

      val config = new ServletConfig {
        import scala.collection.JavaConversions._
        override def getInitParameterNames: util.Enumeration[_] = scala.collection.Iterator()
        override def getServletName: String = "service"
        override def getInitParameter(name: String): String = null
        override def getServletContext: ServletContext = MultiApi.servlet.getServletContext
      }

      val s = new DispatcherServlet(wac)
      s.init(config)
      s
    }

    override def handle(req: HttpServletRequest, res: HttpServletResponse): Unit = {
      val pp = "/public"

      if (req.getPathInfo.startsWith(pp)) {
        val wrap = new HttpServletRequestWrapper(req) {
          override def getContextPath: String = super.getContextPath + "/service" + pp
          override def getRequestURI: String = super.getRequestURI
          override def getPathInfo: String = super.getPathInfo.substring(pp.length)
        }
        servlet.service(wrap, res)
      } else {
        res.setStatus(404)
      }
    }

    def stop() = {
      //    proc.stop()
      IO.delete(workDir)
    }

  }


}
