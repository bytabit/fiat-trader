package org.bytabit.ft.fxui.util

import java.io.{File, IOException}

import com.gluonhq.charm.down.common.PlatformFactory
import org.bytabit.ft.util.Config

class GluonConfig extends Config {

  def filesDir: Option[File] = {
    try {
      new Some[File](PlatformFactory.getPlatform.getPrivateStorage)
    }
    catch {
      case ioe: IOException => Option.apply(null)
    }
  }
}