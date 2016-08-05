package osgi6.geoserver.core

import java.io.InputStream
import java.net.URL
import java.util
import javax.servlet.{RequestDispatcher, Servlet, ServletContext}
import scala.collection.JavaConversions._

/**
  * Created by pappmar on 05/08/2016.
  */
class DelegatedServletContext(delegate : ServletContext) extends ServletContext {

  val attributes : collection.mutable.Map[String, AnyRef] = collection.mutable.Map()

  override def log(message: String, throwable: Throwable): Unit = delegate.log(message, throwable)

  override def getMajorVersion: Int = delegate.getMajorVersion

  override def getResourcePaths(path: String): util.Set[_] = delegate.getResourcePaths(path)

  override def getAttributeNames: util.Enumeration[_] = {
    (attributes.keys ++ delegate.getAttributeNames).toIterator
  }

  override def removeAttribute(name: String): Unit = {
    attributes -= name
  }

  override def getNamedDispatcher(name: String): RequestDispatcher = delegate.getNamedDispatcher(name)

  override def getAttribute(name: String): AnyRef = {
    attributes
      .get(name)
      .getOrElse(
        delegate.getAttribute(name)
      )
  }

  override def getRequestDispatcher(path: String): RequestDispatcher = delegate.getRequestDispatcher(path)

  override def getContextPath: String = delegate.getContextPath

  override def getResource(path: String): URL = delegate.getResource(path)

  override def getServlet(name: String): Servlet = delegate.getServlet(name)

  override def getInitParameterNames: util.Enumeration[_] = delegate.getInitParameterNames

  override def getServletNames: util.Enumeration[_] = delegate.getServletNames

  override def setAttribute(name: String, `object`: scala.Any): Unit = {
    attributes.put(name, `object`.asInstanceOf[AnyRef])
  }

  override def getResourceAsStream(path: String): InputStream = delegate.getResourceAsStream(path)

  override def getInitParameter(name: String): String = delegate.getInitParameter(name)

  override def log(msg: String): Unit = delegate.log(msg)

  override def getMinorVersion: Int = delegate.getMinorVersion

  override def getContext(uripath: String): ServletContext = delegate.getContext(uripath)

  override def getServerInfo: String = delegate.getServerInfo

  override def log(exception: Exception, msg: String): Unit = delegate.log(exception, msg)

  override def getMimeType(file: String): String = delegate.getMimeType(file)

  override def getServlets: util.Enumeration[_] = delegate.getServlets

  override def getServletContextName: String = delegate.getServletContextName

  override def getRealPath(path: String): String = delegate.getRealPath(path)
}
