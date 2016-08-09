package osgi6.geoserver.geotools

import java.util.logging.Logger

import org.geotools.coverage.processing.CoverageProcessor
import org.geotools.referencing.factory.DeferredAuthorityFactory

/**
  * Created by pappmar on 08/08/2016.
  */
object RunGeotoolsLeak {

  def main(args: Array[String]) {

    Logger.getLogger("boo").log(CoverageProcessor.OPERATION, "hehe")

    GeotoolsUtil.shutdown()

  }

}
