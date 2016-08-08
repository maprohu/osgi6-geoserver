package osgi6.geoserver.geotools

import org.geotools.referencing.factory.DeferredAuthorityFactory

/**
  * Created by pappmar on 08/08/2016.
  */
object RunGeotoolsLeak {

  def main(args: Array[String]) {

    DeferredAuthorityFactory.exit()




  }

}
