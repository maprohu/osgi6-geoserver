package osgi6.geoserver.core

import java.io.File
import java.util
import javax.naming.InitialContext
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper, HttpServletResponse}
import javax.servlet.{ServletConfig, ServletContext, ServletContextEvent}

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.geoserver.GeoserverInitStartupListener
import org.geotools.data.DataStore
import org.geotools.util.WeakCollectionCleaner
import org.osgi.framework.BundleContext
import org.springframework.web.servlet.DispatcherServlet
import osgi6.actor.ActorSystemActivator
import osgi6.common.AsyncActivator
import osgi6.geoserver.core.GeoserverBuilder.Input
import osgi6.lib.multi.{ContextApiActivator, MultiApiActivator}
import osgi6.multi.api.MultiApi.{Callback, Handler}
import osgi6.multi.api.{Context, MultiApi}
import sbt.io.IO

import scala.concurrent.Future


/**
  * Created by pappmar on 17/12/2015.
  */
object GeoserverActivator {

//  def activate(
//    rootPath : String,
//    bundleContext: BundleContext,
//    classLoader: Option[ClassLoader] = None,
//    config: Config = ConfigFactory.empty()
//  ) = {
//
//    ActorSystemActivator.activate(
//      bundleContext,
//      { as => import as._
//        import actorSystem.dispatcher
//
//        ContextApiActivator.activateNonNull(
//          { apiCtx =>
//            createHandler(rootPath, apiCtx, classLoader)._3
//          }
//        )
//      },
//      classLoader = classLoader,
//      config = config
//    )
//  }

  def createWac(
    workDir: File,
    classLoader: ClassLoader
  ) = {
    GeoserverBuilder.createInstance(Input(workDir, classLoader))
  }

  def createHandler(
    rootPath : String,
    apiCtx : Context,
    classLoader : Option[ClassLoader]
  ) : (GS, WS, AsyncActivator.Stop) = {
    import sbt.io.Path._

    //    val workDir = IO.createTemporaryDirectory
    val workDir = apiCtx.data / "../storage" / apiCtx.name / "geoserver_data"
    IO.delete(workDir)
    workDir.mkdirs()


    val (gs, ws, servlet, stop) = {

      val sc = new DelegatedServletContext(apiCtx.servletConfig.getServletContext)
      def doCreateWac() = createWac(workDir, classLoader.getOrElse(GeoserverActivator.getClass.getClassLoader))

      val (gs, ws, stop) =
//        classLoader.map({ cl =>
//          val clx = Thread.currentThread().getContextClassLoader
//          Thread.currentThread().setContextClassLoader(cl)
//          try {
//            doCreateWac()
//          } finally {
//            Thread.currentThread().setContextClassLoader(clx)
//          }
//        }).getOrElse(
          doCreateWac()
//        )


      val config = new ServletConfig {
        import scala.collection.JavaConversions._
        override def getInitParameterNames: util.Enumeration[_] = scala.collection.Iterator()
        override def getServletName: String = "service"
        override def getInitParameter(name: String): String = null
        override def getServletContext: ServletContext = sc
      }




      val s = new DispatcherServlet(gs.webApplicationContext)
      s.setPublishContext(false)
      s.init(config)
      (gs, ws, s, stop)
    }

//    val RootPath = "/public/api/ogc"
    val appendToContextPath = apiCtx.rootPath + rootPath

    val handler = new Handler {
      override def dispatch(req: HttpServletRequest, res: HttpServletResponse, callback: Callback): Unit = {

        MultiApiActivator.extractPath(apiCtx, req) match {
          case Some(p) if p startsWith(rootPath) =>
            val wrap = new HttpServletRequestWrapper(req) {
              override def getContextPath: String = super.getContextPath + appendToContextPath
              override def getRequestURI: String = super.getRequestURI
              override def getPathInfo: String = Option(super.getPathInfo).map(_.substring(appendToContextPath.length)).orNull
            }
            servlet.service(wrap, res)
            callback.handled(true)
          case _ =>
            callback.handled(false)
        }
      }
    }

    val stopper : AsyncActivator.Stop = { () =>
      IO.delete(workDir)

      servlet.destroy()
      stop()

      Future.successful()
    }

    val astop = MultiApiActivator.activate(
      (handler, stopper)
    )

    (gs, ws, astop)

  }


}
