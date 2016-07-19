package osgi6.h2gis.impl

import osgi6.actor.ActorSystemActivator
import osgi6.akka.slf4j.AkkaSlf4j
import osgi6.common.MultiActivator
import osgi6.lib.multi.ContextApiActivator
import osgi6.multi.api.ContextApi

/**
  * Created by pappmar on 19/07/2016.
  */
class H2GisActivator extends ActorSystemActivator(
  { ctx =>
    import ctx.actorSystem.dispatcher
    ContextApiActivator.activateNonNull({ apiCtx =>
    })
  },
  Some(classOf[H2GisActivator].getClassLoader),
  config = AkkaSlf4j.config
)
