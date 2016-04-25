package org.bytabit.ft.trade.model

sealed trait Role {
  val identifier:String
}

case object ARBITRATOR extends Role {
  override val identifier: String = "ARBITRATOR"
}

case object SELLER extends Role {
  override val identifier: String = "SELLER"
}

case object BUYER extends Role {
  override val identifier: String = "BUYER"
}
