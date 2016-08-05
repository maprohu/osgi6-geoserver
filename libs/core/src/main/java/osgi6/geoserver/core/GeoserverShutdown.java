package osgi6.geoserver.core;

import org.apache.commons.logging.LogFactory;
import org.apache.log4j.LogManager;
import org.geotools.data.DataAccessFinder;
import org.geotools.data.DataStoreFinder;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.geotools.referencing.factory.AbstractAuthorityFactory;
import org.geotools.referencing.factory.DeferredAuthorityFactory;
import org.geotools.util.WeakCollectionCleaner;
import org.geotools.util.logging.Logging;
import org.opengis.referencing.AuthorityFactory;
import org.opengis.referencing.FactoryException;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.IIOServiceProvider;
import javax.media.jai.JAI;
import javax.media.jai.OperationRegistry;
import javax.media.jai.RegistryElementDescriptor;
import javax.media.jai.RegistryMode;
import javax.servlet.ServletContextEvent;
import java.beans.Introspector;
import java.lang.reflect.Method;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by pappmar on 05/08/2016.
 */
public class GeoserverShutdown {
    static final Logger LOGGER = Logging
            .getLogger("org.geoserver.logging");

    /**
     * This method tries hard to stop all threads and remove all references to classes in GeoServer
     * so that we can avoid permgen leaks on application undeploy.
     * What happes is that, if any JDK class references to one of the classes loaded by the
     * webapp classloader, then the classloader cannot be collected and neither can all the
     * classes loaded by it (since each class keeps a back reference to the classloader that
     * loaded it). The same happens for any residual thread launched by the web app.
     */
    public static void contextDestroyed(ClassLoader webappClassLoader) {

        try {
            LOGGER.info("Beginning GeoServer cleanup sequence");


            // the dreaded classloader
//            ClassLoader webappClassLoader = getClass().getClassLoader();

//            // unload all of the jdbc drivers we have loaded. We need to store them and unregister
//            // later to avoid concurrent modification exceptions
//            Enumeration<Driver> drivers = DriverManager.getDrivers();
//            Set<Driver> driversToUnload = new HashSet<Driver>();
//            while (drivers.hasMoreElements()) {
//                Driver driver = drivers.nextElement();
//                try {
//                    // the driver class loader can be null if the driver comes from the JDK, such as the
//                    // sun.jdbc.odbc.JdbcOdbcDriver
//                    ClassLoader driverClassLoader = driver.getClass().getClassLoader();
//                    if (driverClassLoader != null && webappClassLoader.equals(driverClassLoader)) {
//                        driversToUnload.add(driver);
//                    }
//                } catch(Throwable t) {
//                    t.printStackTrace();
//                }
//            }
//            for (Driver driver : driversToUnload) {
//                try {
//                    DriverManager.deregisterDriver(driver);
//                    LOGGER.info("Unregistered JDBC driver " + driver);
//                } catch(Exception e) {
//                    LOGGER.log(Level.SEVERE, "Could now unload driver " + driver.getClass(), e);
//                }
//            }
//            drivers = DriverManager.getDrivers();
//            while (drivers.hasMoreElements()) {
//                Driver driver = drivers.nextElement();
//            }
//            try {
//                Class h2Driver = Class.forName("org.h2.Driver");
//                Method m = h2Driver.getMethod("unload");
//                m.invoke(null);
//            } catch(Exception e) {
//                LOGGER.log(Level.WARNING, "Failed to unload the H2 driver", e);
//            }

            // unload all deferred authority factories so that we get rid of the timer tasks in them
            try {
                disposeAuthorityFactories(ReferencingFactoryFinder.getCoordinateOperationAuthorityFactories(null));
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "Error occurred trying to dispose authority factories", e);
            }
            try {
                disposeAuthorityFactories(ReferencingFactoryFinder.getCRSAuthorityFactories(null));
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "Error occurred trying to dispose authority factories", e);
            }
            try {
                disposeAuthorityFactories(ReferencingFactoryFinder.getCSAuthorityFactories(null));
            } catch (Throwable e) {
                LOGGER.log(Level.WARNING, "Error occurred trying to dispose authority factories", e);
            }

            // kill the threads created by referencing
            WeakCollectionCleaner.DEFAULT.exit();
            DeferredAuthorityFactory.exit();
            CRS.reset("all");
            LOGGER.info("Shut down GT referencing threads ");
            // reset
            ReferencingFactoryFinder.reset();
            CommonFactoryFinder.reset();
            DataStoreFinder.reset();
            DataAccessFinder.reset();
            LOGGER.info("Shut down GT  SPI ");


            LOGGER.info("Shut down coverage thread pool ");
            Object o = Hints.getSystemDefault(Hints.EXECUTOR_SERVICE);
            if(o !=null && o instanceof ExecutorService){
                final ThreadPoolExecutor executor = (ThreadPoolExecutor) o;
                try{
                    executor.shutdown();
                } finally {
                    try {
                        executor.shutdownNow();
                    } finally {

                    }
                }
            }

            // unload everything that JAI ImageIO can still refer to
            // We need to store them and unregister later to avoid concurrent modification exceptions
            final IIORegistry ioRegistry = IIORegistry.getDefaultInstance();
            Set<IIOServiceProvider> providersToUnload = new HashSet();
            for(Iterator<Class<?>> cats = ioRegistry.getCategories(); cats.hasNext(); ) {
                Class<?> category = cats.next();
                for (Iterator it = ioRegistry.getServiceProviders(category, false); it.hasNext();) {
                    final IIOServiceProvider provider = (IIOServiceProvider) it.next();
//                    if(webappClassLoader.equals(provider.getClass().getClassLoader())) {
                        providersToUnload.add(provider);
//                    }
                }
            }
            for (IIOServiceProvider provider : providersToUnload) {
                ioRegistry.deregisterServiceProvider(provider);
                LOGGER.info("Unregistering Image I/O provider " + provider);
            }

            // unload everything that JAI can still refer to
            final OperationRegistry opRegistry = JAI.getDefaultInstance().getOperationRegistry();
            for(String mode : RegistryMode.getModeNames()) {
                for (Iterator descriptors = opRegistry.getDescriptors(mode).iterator(); descriptors != null && descriptors.hasNext();) {
                    RegistryElementDescriptor red = (RegistryElementDescriptor) descriptors.next();
                    int factoryCount = 0;
                    int unregisteredCount = 0;
                    // look for all the factories for that operation
                    for (Iterator factories = opRegistry.getFactoryIterator(mode, red.getName()); factories != null && factories.hasNext();) {
                        Object factory = factories.next();
                        if(factory == null) {
                            continue;
                        }
                        factoryCount++;
//                        if(webappClassLoader.equals(factory.getClass().getClassLoader())) {
                            boolean unregistered = false;
                            // we need to scan against all "products" to unregister the factory
                            Vector orderedProductList = opRegistry.getOrderedProductList(mode, red.getName());
                            if(orderedProductList != null) {
                                for(Iterator products = orderedProductList.iterator(); products != null && products.hasNext();) {
                                    String product = (String) products.next();
                                    try {
                                        opRegistry.unregisterFactory(mode, red.getName(), product, factory);
                                        LOGGER.info("Unregistering JAI factory " + factory.getClass());
                                    } catch(Throwable t) {
                                        // may fail due to the factory not being registered against that product
                                    }
                                }
                            }
                            if(unregistered) {
                                unregisteredCount++;
                            }

//                        }
                    }

                    // if all the factories were unregistered, get rid of the descriptor as well
                    if(factoryCount > 0 && unregisteredCount == factoryCount) {
                        opRegistry.unregisterDescriptor(red);
                    }
                }
            }

            // flush all javabean introspection caches as this too can keep a webapp classloader from being unloaded
            Introspector.flushCaches();
            LOGGER.info("Cleaned up javabean caches");

            // GeoTools/GeoServer have a lot of finalizers and until they are run the JVM
            // itself wil keepup the class loader...
            try {
                System.gc();
                System.runFinalization();
                System.gc();
                System.runFinalization();
                System.gc();
                System.runFinalization();
            } catch(Throwable t) {
                System.out.println("Failed to perform closing up finalization");
                t.printStackTrace();
            }
        } catch(Throwable t) {
            // if anything goes south during the cleanup procedures I want to know what it is
            t.printStackTrace();
        }
    }

    private static void disposeAuthorityFactories(Set<? extends AuthorityFactory> factories)
            throws FactoryException {
        for (AuthorityFactory af : factories) {
            if(af instanceof AbstractAuthorityFactory) {
                LOGGER.info("Disposing referencing factory " + af);
                ((AbstractAuthorityFactory) af).dispose();
            }
        }
    }

}
