package osgi6.geoserver.geotools

import java.util
import java.util.ResourceBundle
import java.util.logging.{Level, LogManager}

import org.geotools.coverage.processing.CoverageProcessor
import org.geotools.referencing.factory.DeferredAuthorityFactory
import org.geotools.renderer.i18n.Vocabulary
import org.geotools.util.WeakCollectionCleaner
import org.geotools.util.logging.Logging
import org.slf4j.LoggerFactory

/**
  * Created by pappmar on 08/08/2016.
  */
object GeotoolsUtil {

  val LOG = LoggerFactory.getLogger(GeotoolsUtil.getClass)

  def init() = {
    Logging.getLogger("org.geotools").setLevel(Level.OFF)
  }

  def shutdown() : Unit = {
    WeakCollectionCleaner.DEFAULT.exit()
    DeferredAuthorityFactory.exit()
    ResourceBundle.clearCache(classOf[Vocabulary].getClassLoader)

    try {
      val knownLevelClass =
        Option(
          classOf[Level]
            .getClassLoader
        ).orElse(
          Option(ClassLoader.getSystemClassLoader)
        ).map({ cl =>
          cl.loadClass(
            "java.util.logging.Level$KnownLevel"
          )
        }).getOrElse(
          Class.forName(
            "java.util.logging.Level$KnownLevel"
          )
        )

      import scala.collection.JavaConversions._
      knownLevelClass.synchronized {

        val levelObjectField = knownLevelClass.getDeclaredField("levelObject")
        levelObjectField.setAccessible(true)

        def processMap(map: java.util.Map[_, java.util.List[_]]) = {
          new util.ArrayList(map.values()).foreach({ list =>

            val it = list.iterator()

            while (it.hasNext) {
              val knownLevel = it.next()

              val level = levelObjectField.get(
                knownLevel
              ).asInstanceOf[Level]

              if (level eq CoverageProcessor.OPERATION) {
                it.remove()
              }
            }
          })

        }


        processMap(
          getStaticFieldValue[java.util.Map[_, java.util.List[_]]](
            knownLevelClass,
            "intToLevels"
          )
        )

        processMap(
          getStaticFieldValue[java.util.Map[_, java.util.List[_]]](
            knownLevelClass,
            "nameToLevels"
          )
        )
      }

    } catch {
      case ex : Throwable =>
        LOG.info("Could not unload logger level", ex)
    }

  }

  def getStaticFieldValue[T <: AnyRef](
    className : String,
    fieldName : String
  ) : T = {
    val clazz = Class.forName(className)
    getStaticFieldValue(clazz, fieldName)
  }

  def getStaticFieldValue[T <: AnyRef](
    clazz : Class[_],
    fieldName : String
  ) : T = {
    val field = clazz.getDeclaredField(fieldName)
    field.setAccessible(true)
    field.get(null).asInstanceOf[T]
  }

}
