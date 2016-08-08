package osgi6.geoserver.geotools

import org.geotools.referencing.factory.DeferredAuthorityFactory
import org.geotools.util.WeakCollectionCleaner

/**
  * Created by pappmar on 08/08/2016.
  */
object GeotoolsUtil {

  def shutdown() = {
    WeakCollectionCleaner.DEFAULT.exit()
    DeferredAuthorityFactory.exit()
  }

}
