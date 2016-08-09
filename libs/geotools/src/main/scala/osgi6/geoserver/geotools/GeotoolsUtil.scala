package osgi6.geoserver.geotools

import java.util.ResourceBundle

import org.geotools.referencing.factory.DeferredAuthorityFactory
import org.geotools.renderer.i18n.Vocabulary
import org.geotools.util.WeakCollectionCleaner

/**
  * Created by pappmar on 08/08/2016.
  */
object GeotoolsUtil {

  def shutdown() = {
    WeakCollectionCleaner.DEFAULT.exit()
    DeferredAuthorityFactory.exit()
    ResourceBundle.clearCache(classOf[Vocabulary].getClassLoader)
  }

}
