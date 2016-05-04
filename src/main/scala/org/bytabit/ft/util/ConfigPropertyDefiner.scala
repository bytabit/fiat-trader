package org.bytabit.ft.util

import ch.qos.logback.core.PropertyDefinerBase

class ConfigPropertyDefiner extends PropertyDefinerBase {

  override def getPropertyValue: String = Config.config
}
